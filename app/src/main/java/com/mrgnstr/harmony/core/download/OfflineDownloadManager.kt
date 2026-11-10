package com.mrgnstr.harmony.core.download

import android.content.Context
import com.mrgnstr.harmony.core.common.ApplicationScope
import com.mrgnstr.harmony.core.database.NavidromeDao
import com.mrgnstr.harmony.core.database.SongDao
import com.mrgnstr.harmony.core.data.TranscodePolicy
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.mediastore.AudioFormatProbe
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.model.AudioFormat
import com.mrgnstr.harmony.core.model.DownloadQuality
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.network.NavidromeHttpClient
import com.mrgnstr.harmony.core.network.SubsonicApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages pinned offline audio downloads for Navidrome tracks.
 * Downloaded files are stored in `filesDir/offline_audio` and stamped onto `songs.downloadedPath` in Room,
 * so `Song.uriPath` automatically resolves the local file first when playing offline.
 */
@Singleton
class OfflineDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val navidromeDao: NavidromeDao,
    private val subsonic: SubsonicApiClient,
    private val http: NavidromeHttpClient,
    private val settings: SettingsStore,
    private val probe: AudioFormatProbe,
    private val policy: TranscodePolicy,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val offlineDir: File
        get() = File(context.filesDir, "offline_audio").apply { mkdirs() }

    private val _downloadingIds = MutableStateFlow<Set<Long>>(emptySet())
    val downloadingIds: StateFlow<Set<Long>> = _downloadingIds.asStateFlow()

    /** Per song being downloaded: the fraction done, or [UNKNOWN] while the size isn't known (a converted file has none). */
    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()

    /** Songs whose last download failed; cleared when they are tried again or succeed. */
    private val _failed = MutableStateFlow<Set<Long>>(emptySet())
    val failed: StateFlow<Set<Long>> = _failed.asStateFlow()

    /** Songs finished and requested since the queue was last empty; [total] is 0 when nothing is queued. */
    data class QueueStatus(val done: Int = 0, val total: Int = 0)
    private val _queue = MutableStateFlow(QueueStatus())
    val queue: StateFlow<QueueStatus> = _queue.asStateFlow()

    fun downloadSong(song: Song) = downloadSongs(listOf(song))

    fun downloadSongs(songs: List<Song>) {
        val remoteSongs = songs.filter { it.isRemote && !it.isDownloaded && !it.remoteId.isNullOrBlank() && it.id !in _downloadingIds.value }
        if (remoteSongs.isEmpty()) return
        _queue.update { it.copy(total = it.total + remoteSongs.size) }
        scope.launch {
            for (song in remoteSongs) {
                try { downloadSongInternal(song) } finally {
                    _queue.update { q -> if (q.done + 1 >= q.total) QueueStatus() else q.copy(done = q.done + 1) }
                }
            }
        }
    }

    /** What the server really has, by format: its own tags, with the AAC / ALAC / untagged ones looked into (they can hide Dolby Atmos). */
    private val _formats = MutableStateFlow<List<Pair<String, Int>>?>(null)
    val formats: StateFlow<List<Pair<String, Int>>?> = _formats.asStateFlow()

    suspend fun scanFormats() = withContext(Dispatchers.IO) {
        val counts = HashMap<String, Int>()
        val untrusted = songDao.remoteAmbiguousSongs()
        songDao.remoteCodecCounts().forEach { c ->
            val name = c.codec?.takeIf { it.isNotBlank() } ?: return@forEach
            if (!isAmbiguous(name)) counts.merge(formatName(name), c.n, Int::plus)
        }
        val gate = Semaphore(8)
        val real = coroutineScope {
            untrusted.map { ref ->
                async {
                    gate.withPermit { ref.streamUri?.let { probe.probeRemote(ref.id, it) }?.codec }
                }
            }.awaitAll()
        }
        // A file that can't be read keeps its tag (AAC), which is the safe reading: it is only converted when the user ticks AAC.
        real.forEach { counts.merge(formatName(it ?: "AAC"), 1, Int::plus) }
        _formats.value = counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    private fun isAmbiguous(codec: String) = TranscodePolicy.isAmbiguous(codec)

    private fun formatName(codec: String) = if (codec.equals(AudioFormat.DOLBY_ATMOS, true)) AudioFormat.DOLBY_ATMOS else codec.uppercase()

    suspend fun downloadSongInternal(song: Song): Boolean = withContext(Dispatchers.IO) {
        val remoteId = song.remoteId ?: return@withContext false
        if (song.id in _downloadingIds.value) return@withContext false
        val account = (if (song.serverId > 0L) navidromeDao.accountById(song.serverId) else navidromeDao.activeAccount())
            ?.toModel() ?: return@withContext false

        _downloadingIds.update { it + song.id }
        _failed.update { it - song.id }
        var ok = false
        try {
            // Converted only when the user asked for it and the policy allows this song's real format.
            val quality = settings.downloadQuality.first()
            val convert = account.downloadRole && quality != DownloadQuality.Original && policy.allows(song)
            val ext = if (convert) "opus" else (song.codec ?: "audio").lowercase().let {
                when (it) {
                    "flac" -> "flac"
                    "mp3" -> "mp3"
                    "opus" -> "opus"
                    "vorbis" -> "ogg"
                    "aac", "alac" -> "m4a"
                    "wav", "pcm" -> "wav"
                    else -> "audio"
                }
            }
            val target = File(offlineDir, "song_${song.serverId}_${ -song.id }.$ext")
            val tmp = File(offlineDir, "${target.name}.part")
            val url = if (account.downloadRole) {
                if (convert) subsonic.buildDownloadUrl(account, remoteId, format = "opus", bitrateKbps = quality.bitrateKbps)
                else subsonic.buildDownloadUrl(account, remoteId, format = "raw")
            } else {
                subsonic.buildStreamUrl(account, remoteId)
            }
            _progress.update { it + (song.id to UNKNOWN) }
            http.streamTo(account, url) { input, length, _ ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var shown = 0
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        // Whole percents only: a flow update per buffer would recompose the page for nothing.
                        val percent = if (length > 0) (read * 100 / length).toInt() else 0
                        if (percent != shown) {
                            shown = percent
                            _progress.update { it + (song.id to percent / 100f) }
                        }
                    }
                }
            }
            if (tmp.exists() && tmp.length() > 0L) {
                tmp.renameTo(target)
                val uri = "file://${target.absolutePath}"
                songDao.updateDownloadedPath(song.id, uri)
                ok = true
                true
            } else {
                tmp.delete()
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            if (!ok) _failed.update { it + song.id }
            _downloadingIds.update { it - song.id }
            _progress.update { it - song.id }
        }
    }

    companion object {
        const val UNKNOWN = -1f
    }

    suspend fun removeDownload(song: Song) = withContext(Dispatchers.IO) {
        song.downloadedPath?.removePrefix("file://")?.let { File(it).delete() }
        songDao.updateDownloadedPath(song.id, null)
    }

    suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        offlineDir.listFiles()?.forEach { f ->
            f.delete()
        }
        val all = songDao.observeAll()
        // Clear downloadedPath in DB for any downloaded songs
        offlineDir.mkdirs()
    }

    suspend fun totalDownloadedBytes(): Long = withContext(Dispatchers.IO) {
        offlineDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}

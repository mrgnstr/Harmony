package com.mrgnstr.harmony.core.download

import android.content.Context
import com.mrgnstr.harmony.core.common.ApplicationScope
import com.mrgnstr.harmony.core.database.NavidromeDao
import com.mrgnstr.harmony.core.database.SongDao
import com.mrgnstr.harmony.core.database.toModel
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
import kotlinx.coroutines.flow.update
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
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val offlineDir: File
        get() = File(context.filesDir, "offline_audio").apply { mkdirs() }

    private val _downloadingIds = MutableStateFlow<Set<Long>>(emptySet())
    val downloadingIds: StateFlow<Set<Long>> = _downloadingIds.asStateFlow()

    fun downloadSong(song: Song) {
        if (!song.isRemote || song.remoteId.isNullOrBlank()) return
        scope.launch { downloadSongInternal(song) }
    }

    fun downloadSongs(songs: List<Song>) {
        val remoteSongs = songs.filter { it.isRemote && !it.remoteId.isNullOrBlank() }
        if (remoteSongs.isEmpty()) return
        scope.launch {
            for (song in remoteSongs) {
                downloadSongInternal(song)
            }
        }
    }

    suspend fun downloadSongInternal(song: Song): Boolean = withContext(Dispatchers.IO) {
        val remoteId = song.remoteId ?: return@withContext false
        if (song.id in _downloadingIds.value) return@withContext false
        val account = (if (song.serverId > 0L) navidromeDao.accountById(song.serverId) else navidromeDao.activeAccount())
            ?.toModel() ?: return@withContext false

        _downloadingIds.update { it + song.id }
        try {
            val ext = (song.codec ?: "audio").lowercase().let {
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
                subsonic.buildDownloadUrl(account, remoteId, format = "raw")
            } else {
                subsonic.buildStreamUrl(account, remoteId)
            }
            http.streamTo(account, url) { input, _, _ ->
                tmp.outputStream().use { out -> input.copyTo(out, bufferSize = 64 * 1024) }
            }
            if (tmp.exists() && tmp.length() > 0L) {
                tmp.renameTo(target)
                val uri = "file://${target.absolutePath}"
                songDao.updateDownloadedPath(song.id, uri)
                true
            } else {
                tmp.delete()
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            _downloadingIds.update { it - song.id }
        }
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

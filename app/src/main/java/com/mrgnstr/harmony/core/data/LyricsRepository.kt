package com.mrgnstr.harmony.core.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.mrgnstr.harmony.core.model.ByteSource
import com.mrgnstr.harmony.core.model.EmbeddedLyrics
import com.mrgnstr.harmony.core.model.LrcParser
import com.mrgnstr.harmony.core.model.Lyrics
import com.mrgnstr.harmony.core.model.LyricsPriority
import com.mrgnstr.harmony.core.model.TtmlParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lyrics from the `.ttml`, else `.lrc` / `.elrc`, file next to the song (`Song.flac` → `Song.ttml`), and from the song's own tags
 * ([EmbeddedLyrics]); a [LyricsPriority] says which source is asked first, and the first that has lyrics wins. From Android 11 on, the
 * media permission does not cover the files (not media files): they need all-files access ([hasFileAccess]); the tags are read through
 * the song's own MediaStore URI and need nothing extra.
 */
@Singleton
class LyricsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    // Found and not found alike, so a song without lyrics is not looked up again on every change. A miss without file access
    // is looked up again once access is granted.
    private val cache = LruCache<Long, Result>(64)

    /** Optional hook wired by [NavidromeRepository] to fetch OpenSubsonic `songLyrics` v2 (`getLyricsBySongId?enhanced=true`). */
    @Volatile var remoteLyricsFetcher: (suspend (songId: Long) -> Lyrics?)? = null

    private class Result(val lyrics: Lyrics?, val withAccess: Boolean, val priority: LyricsPriority)

    /** Whether files beside the songs can be read: always before Android 11 (the storage permission covers them). */
    fun hasFileAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    suspend fun lyricsFor(songId: Long, priority: LyricsPriority = LyricsPriority.FilesFirst): Lyrics? {
        if (songId < 0L) {
            cache.get(songId)?.lyrics?.let { return it }
            val remote = remoteLyricsFetcher?.invoke(songId)
            if (remote != null) cache.put(songId, Result(remote, true, priority))
            return remote
        }
        val access = hasFileAccess()
        // A result found without file access is looked up again once access is granted (a file may win over the tags then).
        cache.get(songId)?.takeIf { it.priority == priority && (it.withAccess || !access) }?.let { return it.lyrics }
        val lyrics = withContext(Dispatchers.IO) { load(songId, priority) }
        cache.put(songId, Result(lyrics, access, priority))
        return lyrics
    }

    private fun load(songId: Long, priority: LyricsPriority): Lyrics? = when (priority) {
        LyricsPriority.FilesFirst -> fromFiles(songId) ?: fromTags(songId)
        LyricsPriority.EmbeddedFirst -> fromTags(songId) ?: fromFiles(songId)
    }

    /** The lyrics in the song's tags (null when it has none, or for a format that is not read). */
    private fun fromTags(songId: Long): Lyrics? = try {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).use { EmbeddedLyrics.read(ChannelSource(it.channel)) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Can't read the tags of song $songId", e)
        null
    }

    private class ChannelSource(private val channel: FileChannel) : ByteSource {
        override val length: Long = channel.size()
        override fun read(position: Long, size: Int): ByteArray {
            if (position >= length || size <= 0) return ByteArray(0)
            val buffer = ByteBuffer.allocate(minOf(size.toLong(), length - position).toInt())
            var at = position
            while (buffer.hasRemaining()) {
                val n = channel.read(buffer, at)
                if (n <= 0) break
                at += n
            }
            return buffer.array().copyOf(buffer.position())
        }
    }

    private fun fromFiles(songId: Long): Lyrics? {
        val path = pathOf(songId) ?: return null
        val song = File(path)
        val base = song.nameWithoutExtension
        // TTML first (word timing, voices); one that does not parse falls through to the LRC.
        for (ext in listOf("ttml", "TTML")) read(File(song.parentFile, "$base.$ext"))?.let { TtmlParser.parse(it) }?.let { return it }
        for (ext in listOf("lrc", "LRC", "elrc", "ELRC")) read(File(song.parentFile, "$base.$ext"))?.let { LrcParser.parse(it) }?.let { return it }
        return null
    }

    private fun read(file: File): String? = try { if (file.canRead()) file.readText() else null } catch (e: Exception) {
        Log.w(TAG, "Can't read $file", e)
        null
    }

    @Suppress("DEPRECATION") // DATA is still filled in and readable; it is the only way to the folder the song lies in.
    private fun pathOf(songId: Long): String? = try {
        context.contentResolver.query(
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId),
            arrayOf(MediaStore.Audio.Media.DATA), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        Log.w(TAG, "No path for song $songId", e)
        null
    }

    private companion object { const val TAG = "Lyrics" }
}

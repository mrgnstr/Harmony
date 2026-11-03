package com.mrgnstr.harmony.core.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.mrgnstr.harmony.core.model.LrcParser
import com.mrgnstr.harmony.core.model.Lyrics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lyrics from the `.lrc` file next to the song (`Song.flac` → `Song.lrc`). From Android 11 on, the media permission does not
 * cover it (not a media file): it needs all-files access ([hasFileAccess]). Embedded lyrics (USLT/SYLT) are not read yet.
 */
@Singleton
class LyricsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    // Found and not found alike, so a song without lyrics is not looked up again on every change. A miss without file access
    // is looked up again once access is granted.
    private val cache = LruCache<Long, Result>(64)

    private class Result(val lyrics: Lyrics?, val withAccess: Boolean)

    /** Whether files beside the songs can be read: always before Android 11 (the storage permission covers them). */
    fun hasFileAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    suspend fun lyricsFor(songId: Long): Lyrics? {
        val access = hasFileAccess()
        cache.get(songId)?.takeIf { it.lyrics != null || it.withAccess || !access }?.let { return it.lyrics }
        val lyrics = withContext(Dispatchers.IO) { load(songId) }
        cache.put(songId, Result(lyrics, access))
        return lyrics
    }

    private fun load(songId: Long): Lyrics? {
        val path = pathOf(songId) ?: return null
        val song = File(path)
        val base = song.nameWithoutExtension
        val candidates = listOf(File(song.parentFile, "$base.lrc"), File(song.parentFile, "$base.LRC"))
        for (file in candidates) {
            val text = try { if (file.canRead()) file.readText() else null } catch (e: Exception) {
                Log.w(TAG, "Can't read $file", e)
                null
            }
            text?.let { LrcParser.parse(it) }?.let { return it }
        }
        return null
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

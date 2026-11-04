package com.mrgnstr.harmony.core.artwork

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.RequiresApi

/**
 * One place that knows every way to get album art off a device:
 *  - the song file's embedded picture, at full quality;
 *  - else API 29+: ContentResolver.loadThumbnail on the album URI (scaled by the system);
 *  - else API < 29: the legacy content://media/external/audio/albumart/<id> URI.
 * Returns a software bitmap whose longest side is about [targetPx], or null if there is no art.
 */
object ArtworkDecoder {
    fun decode(context: Context, songId: Long, albumId: Long, targetPx: Int): Bitmap? {
        val px = targetPx.coerceIn(16, 2048)
        // The embedded picture first: the system's album thumbnails can be small or built from a low-resolution copy.
        embedded(context, songId, px)?.let { return it }
        if (albumId <= 0) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) thumbnail(context, albumId, px) else legacyAlbumArt(context, albumId, px)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun thumbnail(context: Context, albumId: Long, px: Int): Bitmap? = try {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        context.contentResolver.loadThumbnail(uri, Size(px, px), null).asSoftware()
    } catch (_: Exception) {
        null
    }

    private fun legacyAlbumArt(context: Context, albumId: Long, px: Int): Bitmap? = try {
        val uri = ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), albumId)
        context.contentResolver.openInputStream(uri)?.use { decodeSampled(it.readBytes(), px) }
    } catch (_: Exception) {
        null
    }

    private fun embedded(context: Context, songId: Long, px: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId))
            retriever.embeddedPicture?.let { decodeSampled(it, px) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Power-of-two downsampling straight from the bytes, so a 3000 px cover never lands in memory at full size. */
    internal fun decodeSampled(bytes: ByteArray, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun Bitmap.asSoftware(): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
}

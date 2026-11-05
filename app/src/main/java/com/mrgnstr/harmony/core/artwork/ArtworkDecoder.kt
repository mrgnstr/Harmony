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
import java.io.File

/**
 * One place that knows every way to get album art off a device:
 *  - the song file's embedded picture, at full quality (cached on disk after the first read);
 *  - else API 29+: ContentResolver.loadThumbnail on the album URI (scaled by the system);
 *  - else API < 29: the legacy content://media/external/audio/albumart/<id> URI.
 * Returns a software bitmap whose longest side is about [targetPx], or null if there is no art.
 */
object ArtworkDecoder {
    private const val CACHE_DIR = "artwork"
    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024
    private const val TRIM_EVERY = 50
    /** Cached sizes: a request is served from (and stored at) the next size up, so a few files cover every tile size. */
    private val BUCKETS = intArrayOf(96, 128, 192, 256, 384, 512, 768, 1024, 1536, 2048)

    fun decode(context: Context, songId: Long, albumId: Long, targetPx: Int): Bitmap? {
        val px = targetPx.coerceIn(16, 2048)
        val id = if (albumId > 0) "a$albumId" else "s$songId"
        // The embedded picture first: the system's album thumbnails can be small or built from a low-resolution copy. Reading it
        // means opening the song file (100+ ms on a fast phone), so the scaled result is kept on disk, and so is "this one has none".
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val none = File(dir, "$id.none")
        if (!none.exists()) {
            val bucket = BUCKETS.firstOrNull { it >= px } ?: BUCKETS.last()
            val cached = File(dir, "$id-$bucket")
            readCached(cached)?.let { return it }
            val fresh = embedded(context, songId, bucket)
            if (fresh != null) {
                writeCached(dir, cached, fresh)
                return fresh
            }
            runCatching { none.createNewFile() }
        }
        if (albumId <= 0) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) thumbnail(context, albumId, px) else legacyAlbumArt(context, albumId, px)
    }

    private fun readCached(file: File): Bitmap? =
        if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null

    private var writes = 0

    private fun writeCached(dir: File, file: File, bitmap: Bitmap) {
        runCatching {
            // Written aside and renamed, so a reader never sees half a file.
            val tmp = File(dir, "${file.name}.tmp${Thread.currentThread().id}")
            tmp.outputStream().use { out ->
                if (bitmap.hasAlpha()) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) else bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (!tmp.renameTo(file)) tmp.delete()
        }
        if (++writes % TRIM_EVERY == 0) trim(dir)
    }

    /** Keeps the cache under [MAX_CACHE_BYTES], oldest files first. */
    private fun trim(dir: File) {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES * 3 / 4) break
            total -= f.length()
            f.delete()
        }
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

package com.mrgnstr.harmony.core.data

import android.content.Context
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** A custom artist photo: its file and its shape (height / width). */
class ArtistImage(val path: String, val ratio: Float)

/** The custom images by artist; looked up by name, ignoring case. */
class ArtistImages internal constructor(private val byKey: Map<String, ArtistImage>) {
    operator fun get(name: String): ArtistImage? = byKey[ArtistImageStore.keyOf(name)]
}

/**
 * Custom artist photos. Local files carry none, so an artist is pictured with an album cover unless the user picks an image
 * (kept in its own shape, scaled down, in app storage). A file is named after the artist and a timestamp, so replacing an image
 * gives it a new path and the image cache never shows the old one.
 */
@Singleton
class ArtistImageStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "artist_images").apply { mkdirs() }
    private val logoDir get() = File(context.filesDir, "artist_logos").apply { mkdirs() }
    private val _images = MutableStateFlow(scan())
    val images: StateFlow<ArtistImages> get() = _images
    private val _logos = MutableStateFlow(scanLogos())
    val logos: StateFlow<ArtistImages> get() = _logos

    private fun scan(): ArtistImages = scanDir(dir)
    private fun scanLogos(): ArtistImages = scanDir(logoDir)

    private fun scanDir(folder: File): ArtistImages = ArtistImages(
        folder.listFiles().orEmpty().filter { it.extension == "jpg" || it.extension == "png" }
            .associate { it.name.substringBefore('-') to ArtistImage(it.absolutePath, ratioOf(it)) },
    )

    private fun ratioOf(file: File): Float {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return if (bounds.outWidth > 0) bounds.outHeight / bounds.outWidth.toFloat() else 1f
    }

    /** @return false if the image could not be read. */
    suspend fun set(name: String, uri: String): Boolean = withContext(Dispatchers.IO) {
        val key = keyOf(name)
        val file = File(dir, "$key-${System.nanoTime()}.png")
        if (!saveSquareJpeg(context, uri, file, square = false)) return@withContext false
        dir.listFiles().orEmpty().filter { it != file && it.name.startsWith("$key-") }.forEach { it.delete() }
        _images.value = scan()
        true
    }

    suspend fun clear(name: String) = withContext(Dispatchers.IO) {
        val key = keyOf(name)
        dir.listFiles().orEmpty().filter { it.name.startsWith("$key-") }.forEach { it.delete() }
        _images.value = scan()
    }

    /** @return false if the logo could not be read. */
    suspend fun setLogo(name: String, uri: String): Boolean = withContext(Dispatchers.IO) {
        val key = keyOf(name)
        val file = File(logoDir, "$key-${System.nanoTime()}.png")
        if (!saveSquareJpeg(context, uri, file, square = false, trimAlpha = true)) return@withContext false
        logoDir.listFiles().orEmpty().filter { it != file && it.name.startsWith("$key-") }.forEach { it.delete() }
        _logos.value = scanLogos()
        true
    }

    suspend fun clearLogo(name: String) = withContext(Dispatchers.IO) {
        val key = keyOf(name)
        logoDir.listFiles().orEmpty().filter { it.name.startsWith("$key-") }.forEach { it.delete() }
        _logos.value = scanLogos()
    }

    internal companion object {
        fun keyOf(name: String): String =
            MessageDigest.getInstance("SHA-1").digest(name.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }
}

package com.mrgnstr.harmony.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

private const val SQUARE_MAX_PX = 1024
private const val FULL_MAX_PX = 1600

/**
 * Reads the image at [uri], scales it down and writes a JPEG to [file]. [square] crops it to a centred square first (playlist
 * covers, at most 1024px); otherwise it keeps its own shape (artist photos, at most 1600px on the long side).
 */
internal fun saveSquareJpeg(context: Context, uri: String, file: File, square: Boolean = true, trimAlpha: Boolean = false): Boolean = runCatching {
    val maxPx = if (square) SQUARE_MAX_PX else FULL_MAX_PX
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    val raw = resolver.openInputStream(Uri.parse(uri))?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching false
    val decoded = if (trimAlpha && raw.hasAlpha()) trimTransparent(raw) else raw
    val out = if (square) {
        val side = minOf(decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        if (side > maxPx) Bitmap.createScaledBitmap(cropped, maxPx, maxPx, true) else cropped
    } else {
        val longest = maxOf(decoded.width, decoded.height)
        if (longest > maxPx) {
            val scale = maxPx / longest.toFloat()
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else decoded
    }
    val format = if (out.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    file.outputStream().use { out.compress(format, 90, it) }
    true
}.getOrDefault(false)

/** Crops transparent margins around a logo bitmap so it aligns tightly to its bounding box. */
private fun trimTransparent(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val px = IntArray(w * h)
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            if ((px[row + x] ushr 24) > 8) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < minX || maxY < minY) return bitmap
    if (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1) return bitmap
    return Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1)
}

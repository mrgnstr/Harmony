package com.mrgnstr.harmony.core.artwork

import android.graphics.Bitmap
import java.util.Base64
import kotlin.math. PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes Navidrome's ThumbHash (https://github.com/evanw/thumbhash) base64 strings into RGBA pixels,
 * Android software [Bitmap]s, average ARGB colours, and aspect ratios.
 */
object ThumbHash {
    data class DecodedImage(
        val width: Int,
        val height: Int,
        val rgba: ByteArray,
        val argbPixels: IntArray,
    )

    fun decodeBase64(base64: String): ByteArray? = runCatching {
        Base64.getDecoder().decode(base64.trim())
    }.getOrNull()?.takeIf { it.size >= 5 }

    /** Extracts the approximate aspect ratio (width / height) encoded in the 5-byte ThumbHash header. */
    fun approximateAspectRatio(hash: ByteArray): Float {
        if (hash.size < 5) return 1f
        val header = hash[3].toInt() and 0xFF
        val hasAlpha = (hash[2].toInt() and 0x80) != 0
        val isLandscape = (hash[4].toInt() and 0x80) != 0
        val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else (header and 7)
        val ly = if (isLandscape) (header and 7) else (if (hasAlpha) 5 else 7)
        if (lx <= 0 || ly <= 0) return 1f
        return lx.toFloat() / ly.toFloat()
    }

    /** Extracts the average ARGB colour directly from the 3-byte DC header with zero DCT work. */
    fun averageArgb(hash: ByteArray): Int {
        if (hash.size < 5) return 0xFF3C3C42.toInt()
        val header = (hash[0].toInt() and 0xFF) or
            ((hash[1].toInt() and 0xFF) shl 8) or
            ((hash[2].toInt() and 0xFF) shl 16)
        val l = (header and 63) / 63f
        val p = ((header ushr 6) and 63) / 31.5f - 1f
        val q = ((header ushr 12) and 63) / 31.5f - 1f
        val hasAlpha = (header ushr 23) != 0
        val a = if (hasAlpha && hash.size > 5) (hash[5].toInt() and 15) / 15f else 1f
        val b = l - 2f / 3f * p
        val r = (3f * l - b + q) / 2f
        val g = r - q
        val ai = (a.coerceIn(0f, 1f) * 255f).roundToInt()
        val ri = (r.coerceIn(0f, 1f) * 255f).roundToInt()
        val gi = (g.coerceIn(0f, 1f) * 255f).roundToInt()
        val bi = (b.coerceIn(0f, 1f) * 255f).roundToInt()
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /** Decodes a ThumbHash byte array into an RGBA + ARGB image (up to 32 px on its longest side). */
    fun decodeToRgba(hash: ByteArray): DecodedImage? {
        if (hash.size < 5) return null
        fun u8(i: Int): Int = if (i in hash.indices) hash[i].toInt() and 0xFF else 0

        val header24 = u8(0) or (u8(1) shl 8) or (u8(2) shl 16)
        val header16 = u8(3) or (u8(4) shl 8)
        val lDc = (header24 and 63) / 63f
        val pDc = ((header24 ushr 6) and 63) / 31.5f - 1f
        val qDc = ((header24 ushr 12) and 63) / 31.5f - 1f
        val lScale = ((header24 ushr 18) and 31) / 31f
        val hasAlpha = (header24 ushr 23) != 0
        val pScale = ((header16 ushr 3) and 63) / 63f
        val qScale = ((header16 ushr 9) and 63) / 63f
        val isLandscape = (header16 ushr 15) != 0
        val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else (header16 and 7))
        val ly = max(3, if (isLandscape) (header16 and 7) else (if (hasAlpha) 5 else 7))
        val aDc = if (hasAlpha) (u8(5) and 15) / 15f else 1f
        val aScale = (u8(5) ushr 4) / 15f

        val acStart = if (hasAlpha) 6 else 5
        var acIndex = 0
        fun decodeChannel(nx: Int, ny: Int, scale: Float): FloatArray {
            val out = FloatArray(nx * ny)
            var count = 0
            for (cy in 0 until ny) {
                var cx = if (cy > 0) 0 else 1
                while (cx * ny < nx * (ny - cy)) {
                    val byteIdx = acStart + (acIndex ushr 1)
                    val nibble = (u8(byteIdx) ushr ((acIndex and 1) shl 2)) and 15
                    acIndex++
                    out[count++] = (nibble / 7.5f - 1f) * scale
                    cx++
                }
            }
            return out.copyOf(count)
        }

        val lAc = decodeChannel(lx, ly, lScale)
        val pAc = decodeChannel(3, 3, pScale * 1.25f)
        val qAc = decodeChannel(3, 3, qScale * 1.25f)
        val aAc = if (hasAlpha) decodeChannel(5, 5, aScale) else FloatArray(0)

        val ratio = approximateAspectRatio(hash)
        val w = (if (ratio > 1f) 32f else 32f * ratio).roundToInt().coerceAtLeast(1)
        val h = (if (ratio > 1f) 32f / ratio else 32f).roundToInt().coerceAtLeast(1)
        val rgba = ByteArray(w * h * 4)
        val argb = IntArray(w * h)
        val cxMax = max(lx, if (hasAlpha) 5 else 3)
        val cyMax = max(ly, if (hasAlpha) 5 else 3)
        val fx = FloatArray(cxMax)
        val fy = FloatArray(cyMax)

        var pixelIdx = 0
        var byteOffset = 0
        for (y in 0 until h) {
            for (cy in 0 until cyMax) {
                fy[cy] = cos(PI / h * (y + 0.5) * cy).toFloat()
            }
            for (x in 0 until w) {
                for (cx in 0 until cxMax) {
                    fx[cx] = cos(PI / w * (x + 0.5) * cx).toFloat()
                }
                var l = lDc
                var p = pDc
                var q = qDc
                var a = aDc

                var j = 0
                for (cy in 0 until ly) {
                    val fy2 = fy[cy] * 2f
                    var cx = if (cy > 0) 0 else 1
                    while (cx * ly < lx * (ly - cy)) {
                        l += lAc[j++] * fx[cx] * fy2
                        cx++
                    }
                }

                j = 0
                for (cy in 0 until 3) {
                    val fy2 = fy[cy] * 2f
                    var cx = if (cy > 0) 0 else 1
                    while (cx < 3 - cy) {
                        val f = fx[cx] * fy2
                        p += pAc[j] * f
                        q += qAc[j] * f
                        j++
                        cx++
                    }
                }

                if (hasAlpha) {
                    j = 0
                    for (cy in 0 until 5) {
                        val fy2 = fy[cy] * 2f
                        var cx = if (cy > 0) 0 else 1
                        while (cx < 5 - cy) {
                            a += aAc[j++] * fx[cx] * fy2
                            cx++
                        }
                    }
                }

                val bVal = l - 2f / 3f * p
                val rVal = (3f * l - bVal + q) / 2f
                val gVal = rVal - q

                val ri = (max(0f, min(1f, rVal)) * 255f).toInt()
                val gi = (max(0f, min(1f, gVal)) * 255f).toInt()
                val bi = (max(0f, min(1f, bVal)) * 255f).toInt()
                val ai = (max(0f, min(1f, a)) * 255f).toInt()

                rgba[byteOffset++] = ri.toByte()
                rgba[byteOffset++] = gi.toByte()
                rgba[byteOffset++] = bi.toByte()
                rgba[byteOffset++] = ai.toByte()
                argb[pixelIdx++] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        return DecodedImage(w, h, rgba, argb)
    }

    fun decodeToBitmap(base64: String): Bitmap? {
        val bytes = decodeBase64(base64) ?: return null
        val decoded = decodeToRgba(bytes) ?: return null
        return Bitmap.createBitmap(decoded.argbPixels, decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
    }

    /** Parses a Navidrome `#rrggbb` dominantColor string into an opaque ARGB Int. */
    fun parseHexColor(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6) return null
        val rgb = clean.toIntOrNull(16) ?: return null
        return rgb or (0xFF shl 24)
    }
}

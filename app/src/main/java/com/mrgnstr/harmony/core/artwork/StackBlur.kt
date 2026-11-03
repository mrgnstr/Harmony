package com.mrgnstr.harmony.core.artwork

/**
 * Three box-blur passes (about a gaussian) over ARGB pixels. Used on tiny (~48 px) bitmaps only, then
 * the result is drawn upscaled, which gives a huge-radius blur for the cost of a thumbnail.
 */
internal object StackBlur {
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        if (radius < 1) return
        val tmp = IntArray(pixels.size)
        repeat(3) {
            pass(pixels, tmp, width, height, radius, horizontal = true)
            pass(tmp, pixels, width, height, radius, horizontal = false)
        }
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = src[at(i)]
                a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until len) {
                dst[at(i)] = ((a / window) shl 24) or ((rr / window) shl 16) or ((g / window) shl 8) or (b / window)
                val add = src[at(i + r + 1)]
                val sub = src[at(i - r)]
                a += (add ushr 24) - (sub ushr 24)
                rr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}

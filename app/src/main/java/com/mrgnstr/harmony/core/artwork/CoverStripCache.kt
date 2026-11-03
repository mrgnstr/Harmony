package com.mrgnstr.harmony.core.artwork

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What to build for the full-screen player's cover surroundings, all in screen pixels.
 * [aboveCoverPx] / [belowCoverPx] are the free space above and below the square cover.
 * [fadePx] is how far into the cover the blurred strip reaches (its alpha fades in over that distance).
 * [slope] is the blur growth: a disc radius of slope * distance, i.e. a gaussian sigma of half that.
 */
data class CoverStripSpec(
    val songId: Long,
    val albumId: Long,
    val widthPx: Int,
    val aboveCoverPx: Int,
    val belowCoverPx: Int,
    val fadePx: Int,
    val topSigmaPx: Float,
    val bottomSigmaPx: Float,
    val slope: Float,
)

/**
 * Two bitmaps drawn at full width: [top] spans screen y [0, coverTop + fade] and [bottom] spans
 * [coverBottom - fade, screen height]. Either is null when there is no room on that side.
 */
class CoverStrips(val top: Bitmap?, val bottom: Bitmap?)

/**
 * The blur around the full-screen cover, computed once per cover instead of every frame.
 *
 * Each strip is the cover's outermost pixel row stretched away from the cover and blurred with a radius that grows
 * linearly with distance. Every row of the stretched part is identical, so the blur there is purely horizontal and
 * each output row is just the edge row blurred by its own sigma. The few rows inside the cover (the fade zone, plus
 * padding so the blur kernel sees real picture) are blurred normally. Built at low resolution on the CPU, so it works
 * on every API level and costs nothing per frame afterwards.
 */
@Singleton
class CoverStripCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = LruCache<String, CoverStrips>(4)

    suspend fun get(spec: CoverStripSpec): CoverStrips? {
        val key = with(spec) {
            "${ArtworkKey(songId, albumId).cacheKey}|$widthPx|$aboveCoverPx|$belowCoverPx|$fadePx|$topSigmaPx|$bottomSigmaPx|$slope"
        }
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val decoded = ArtworkDecoder.decode(context, spec.songId, spec.albumId, min(spec.widthPx, MAX_WIDTH))
                ?: return@withContext null
            val strips = build(decoded, spec)
            cache.put(key, strips)
            strips
        }
    }

    companion object {
        /** The same fallback for custom images and covers, with the caller owning the decoded bitmap. */
        suspend fun fromBitmap(bitmap: Bitmap, spec: CoverStripSpec): CoverStrips = withContext(Dispatchers.Default) {
            build(bitmap, spec)
        }

        private fun build(bitmap: Bitmap, spec: CoverStripSpec): CoverStrips {
            val w = min(spec.widthPx, MAX_WIDTH).coerceAtLeast(8)
            val scale = w.toFloat() / spec.widthPx
            // The cover is as wide as the strips; normally square, but a wider-than-tall picture keeps its shape.
            val h = (w * bitmap.height / bitmap.width.toFloat()).roundToInt().coerceAtLeast(1)
            val square = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val cover = IntArray(w * h).also { square.getPixels(it, 0, w, 0, 0, w, h) }
            return CoverStrips(
                top = buildStrip(cover, w, h, true, spec.aboveCoverPx, spec.fadePx, spec.topSigmaPx, spec.slope, scale),
                bottom = buildStrip(cover, w, h, false, spec.belowCoverPx, spec.fadePx, spec.bottomSigmaPx, spec.slope, scale),
            )
        }

        /**
         * [flip] = false: the strip hangs below the cover's bottom edge. true: above its top edge (built mirrored, then flipped back).
         * The returned bitmap's rows run from the screen's far edge towards the cover, or the reverse, as drawn.
         */
        private fun buildStrip(
            cover: IntArray,
            w: Int,
            h: Int,
            flip: Boolean,
            spacePx: Int,
            fadePx: Int,
            sigma0Px: Float,
            slope: Float,
            scale: Float,
        ): Bitmap? {
            if (spacePx < 1) return null
            val fadeRows = ceil(fadePx * scale).toInt().coerceIn(1, h / 3)
            val padRows = ceil(3f * sigma0Px * scale).toInt().coerceIn(0, h - fadeRows)
            val belowRows = ceil(spacePx * scale).toInt().coerceAtLeast(1)
            val coverRows = fadeRows + padRows
            val rows = coverRows + belowRows

            // Scene, oriented as if the strip hangs below the cover: the cover's edge rows, then its last row repeated.
            val scene = IntArray(w * rows)
            for (r in 0 until coverRows) {
                val srcRow = if (flip) coverRows - 1 - r else h - coverRows + r
                System.arraycopy(cover, srcRow * w, scene, r * w, w)
            }
            val edgeRow = (coverRows - 1) * w
            for (r in coverRows until rows) System.arraycopy(scene, edgeRow, scene, r * w, w)

            val tmp = IntArray(maxOf(w, rows))
            // The base blur, same everywhere.
            val s0 = sigma0Px * scale
            if (s0 > 0.3f) {
                for (r in 0 until rows) Gaussian.line(scene, r * w, 1, w, s0, tmp)
                for (c in 0 until w) Gaussian.line(scene, c, w, rows, s0, tmp)
            }
            // Growing blur below the edge: row i is at distance (i + 0.5) / scale px, disc radius slope * that, sigma half
            // of it; in low-resolution pixels the scale cancels.
            for (i in 0 until belowRows) {
                Gaussian.line(scene, (coverRows + i) * w, 1, w, slope * (i + 0.5f) / 2f, tmp)
            }

            // Output: drop the padding rows; fade the cover rows in.
            val outRows = fadeRows + belowRows
            val out = IntArray(w * outRows)
            for (j in 0 until outRows) {
                val alpha = if (j < fadeRows) (smoothstep((j + 0.5f) / fadeRows) * 255f).roundToInt() else 255
                val dstRow = if (flip) outRows - 1 - j else j
                val srcBase = (padRows + j) * w
                val dstBase = dstRow * w
                for (x in 0 until w) out[dstBase + x] = (alpha shl 24) or (scene[srcBase + x] and 0xFFFFFF)
            }
            return Bitmap.createBitmap(out, w, outRows, Bitmap.Config.ARGB_8888)
        }

        private fun smoothstep(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

        /** Working width; the result is drawn upscaled, which is fine for something this blurred. */
        private const val MAX_WIDTH = 360
    }
}

/** Gaussian blur as three box passes with replicated edges, along one line of an ARGB array (alpha untouched, treated opaque). */
internal object Gaussian {
    fun line(px: IntArray, start: Int, stride: Int, len: Int, sigma: Float, tmp: IntArray) {
        if (sigma < 0.3f || len < 2) return
        for (size in boxSizes(sigma)) box(px, start, stride, len, ((size - 1) / 2).coerceAtMost(3 * len), tmp)
    }

    private fun boxSizes(sigma: Float): IntArray {
        val n = 3
        val ideal = sqrt(12f * sigma * sigma / n + 1f)
        var wl = floor(ideal).toInt()
        if (wl % 2 == 0) wl--
        wl = wl.coerceAtLeast(1)
        val wu = wl + 2
        val m = ((12f * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4f * wl - 4f)).roundToInt().coerceIn(0, n)
        return IntArray(n) { if (it < m) wl else wu }
    }

    private fun box(px: IntArray, start: Int, stride: Int, len: Int, r: Int, tmp: IntArray) {
        val window = 2 * r + 1
        val half = window / 2
        var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val p = px[start + i.coerceIn(0, len - 1) * stride]
            sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
        }
        for (i in 0 until len) {
            tmp[i] = (0xFF shl 24) or (((sr + half) / window) shl 16) or (((sg + half) / window) shl 8) or ((sb + half) / window)
            val add = px[start + (i + r + 1).coerceIn(0, len - 1) * stride]
            val sub = px[start + (i - r).coerceIn(0, len - 1) * stride]
            sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            sb += (add and 0xFF) - (sub and 0xFF)
        }
        for (i in 0 until len) px[start + i * stride] = tmp[i]
    }
}

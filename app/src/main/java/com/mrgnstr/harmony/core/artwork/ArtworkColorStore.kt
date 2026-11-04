package com.mrgnstr.harmony.core.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Color
import android.os.Process
import androidx.palette.graphics.Palette
import com.mrgnstr.harmony.core.database.AlbumColorDao
import com.mrgnstr.harmony.core.database.toEntity
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.model.AlbumPalette
import com.mrgnstr.harmony.core.model.ArtRef
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** [dominant] is the most common colour (ARGB), [bottomLuminance] the mean brightness (0..1) of the picture's bottom edge. */
class CoverSample(val dominant: Int?, val bottomLuminance: Float)

/** Palette extraction happens here, once per album, never while scrolling. Results live in Room. */
@Singleton
class ArtworkColorStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AlbumColorDao,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** Stored colours from an older extraction rule are dropped once, so every album is recomputed with the current rule. */
    private suspend fun invalidateStale() {
        val prefs = context.getSharedPreferences("artwork_colors", Context.MODE_PRIVATE)
        if (prefs.getInt("rule", 0) == RULE_VERSION) return
        dao.clear()
        prefs.edit().putInt("rule", RULE_VERSION).apply()
    }

    suspend fun paletteFor(songId: Long, albumId: Long): AlbumPalette? {
        if (albumId <= 0) return null
        invalidateStale()
        dao.get(albumId)?.let { return it.toModel() }
        return compute(songId, albumId).also { dao.upsert(it.toEntity()) }
    }

    /** Fills in every album that has no stored colours yet. Low priority, one album at a time. */
    suspend fun precomputeMissing() {
        invalidateStale()
        for (album in dao.missing()) {
            dao.upsert(compute(album.songId, album.albumId).toEntity())
            yield()
        }
    }

    private suspend fun compute(songId: Long, albumId: Long): AlbumPalette = withContext(worker) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val bitmap = ArtworkDecoder.decode(context, songId, albumId, SAMPLE_PX)
            ?: return@withContext AlbumPalette(albumId, FALLBACK, FALLBACK, FALLBACK, hasArt = false)
        extract(albumId, bitmap)
    }

    /**
     * The page colour for a playlist, taken from its cover as shown: the custom image, the 2x2 collage of its four album covers,
     * or its single cover. Null when it has no art. Cheap (a few 64px decodes), so it is not stored.
     */
    suspend fun accentForPlaylist(coverPath: String?, art: List<ArtRef>): Int? = withContext(worker) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val bitmap: Bitmap = when {
            coverPath != null -> decodeFile(coverPath)
            art.size >= 4 -> {
                val collage = Bitmap.createBitmap(SAMPLE_PX * 2, SAMPLE_PX * 2, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(collage)
                art.take(4).forEachIndexed { i, ref ->
                    ArtworkDecoder.decode(context, ref.songId, ref.albumId, SAMPLE_PX)?.let {
                        val dst = Rect((i % 2) * SAMPLE_PX, (i / 2) * SAMPLE_PX, (i % 2 + 1) * SAMPLE_PX, (i / 2 + 1) * SAMPLE_PX)
                        canvas.drawBitmap(it, null, dst, null)
                    }
                }
                collage
            }
            art.isNotEmpty() -> ArtworkDecoder.decode(context, art.first().songId, art.first().albumId, SAMPLE_PX)
            else -> null
        } ?: return@withContext null
        extract(0, bitmap).primary
    }

    /**
     * What Now Playing's controls need to know about a cover, from a few 64px pixels (cheap, so not stored): the colour that
     * covers the most of it (not the most vivid one) and how bright its bottom edge is, which is what the blurred area behind
     * the controls is made of. Null without art.
     */
    suspend fun coverSample(songId: Long, albumId: Long): CoverSample? = withContext(Dispatchers.Default) {
        val bitmap = ArtworkDecoder.decode(context, songId, albumId, SAMPLE_PX) ?: return@withContext null
        val source = if (bitmap.hasAlpha()) opaquePixelsOf(bitmap) ?: bitmap else bitmap
        val dominant = Palette.from(source).maximumColorCount(16).generate().dominantSwatch?.rgb?.or(OPAQUE)
        // Mean brightness (0..1, sRGB weights) of the bottom twelfth of the picture.
        val rows = (bitmap.height / 12).coerceAtLeast(1)
        val px = IntArray(bitmap.width * rows)
        bitmap.getPixels(px, 0, bitmap.width, 0, bitmap.height - rows, bitmap.width, rows)
        var sum = 0f
        for (c in px) sum += (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
        CoverSample(dominant, sum / px.size)
    }

    private fun decodeFile(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = (bounds.outWidth / (SAMPLE_PX * 2)).coerceAtLeast(1)
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun extract(albumId: Long, bitmap: Bitmap): AlbumPalette {
        val source = if (bitmap.hasAlpha()) opaquePixelsOf(bitmap) ?: bitmap else bitmap
        val p = Palette.from(source).maximumColorCount(16).generate()
        val primary = p.mutedSwatch?.rgb ?: p.vibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: FALLBACK
        val secondary = p.lightVibrantSwatch?.rgb ?: p.vibrantSwatch?.rgb ?: p.mutedSwatch?.rgb ?: primary
        val tertiary = p.darkVibrantSwatch?.rgb ?: p.vibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: primary
        return AlbumPalette(albumId, primary or OPAQUE, secondary or OPAQUE, tertiary or OPAQUE, hasArt = true)
    }

    /** Packs only non-transparent pixels so [Palette] does not treat transparent areas (0x00000000) as black. */
    private fun opaquePixelsOf(bitmap: Bitmap): Bitmap? {
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var count = 0
        for (c in px) {
            if ((c ushr 24) >= 128) px[count++] = c or OPAQUE
        }
        if (count == 0) return null
        return Bitmap.createBitmap(px, 0, count, count, 1, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val SAMPLE_PX = 64
        const val RULE_VERSION = 4 // 2: vibrant over dominant; 3: secondary = light vibrant, tertiary = dark vibrant; 4: primary = muted
        val OPAQUE = 0xFF000000.toInt()
        val FALLBACK = Color.rgb(60, 60, 66)
    }
}

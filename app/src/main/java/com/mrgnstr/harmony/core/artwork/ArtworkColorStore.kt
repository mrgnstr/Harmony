package com.mrgnstr.harmony.core.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Color
import android.os.Process
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

/** [dominant] is the most common colour (ARGB), [bottomLuminance] the mean brightness (0..1) of the picture's bottom and top edge. */
class CoverSample(val dominant: Int?, val bottomLuminance: Float, val topLuminance: Float)

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

    /** Palettes already read or computed, so an album page can theme itself in its very first frame. */
    private val memory = java.util.concurrent.ConcurrentHashMap<Long, AlbumPalette>()

    /** The palette if it is in memory (no waiting), else Navidrome's precomputed dominantColor/thumbHash, else null. */
    fun peek(albumId: Long): AlbumPalette? {
        memory[albumId]?.let { return it }
        if (albumId < 0L) {
            RemoteArtworkBridge.dominantOrThumbColor(0L, albumId)?.let { avg ->
                val c = CoverColour.fromAverage(avg)
                return AlbumPalette(albumId, c, c, c, hasArt = true).also { memory[albumId] = it }
            }
        }
        return null
    }

    /** Seeds the in-memory and Room colour cache from Navidrome's server-computed `dominantColor` / `thumbHash`. */
    suspend fun seedRemotePalette(albumId: Long, dominantColorHex: String?, thumbHash: String?) {
        if (albumId == 0L) return
        val color = ThumbHash.parseHexColor(dominantColorHex)
            ?: thumbHash?.let { ThumbHash.decodeBase64(it) }?.let { ThumbHash.averageArgb(it) }
            ?: return
        val seed = CoverColour.fromAverage(color)
        val palette = AlbumPalette(albumId, seed, seed, seed, hasArt = true)
        memory[albumId] = palette
        dao.upsert(palette.toEntity())
    }

    suspend fun paletteFor(songId: Long, albumId: Long): AlbumPalette? {
        if (albumId == 0L) return null
        memory[albumId]?.let { return it }
        invalidateStale()
        val palette = dao.get(albumId)?.toModel() ?: compute(songId, albumId).also { dao.upsert(it.toEntity()) }
        memory[albumId] = palette
        return palette
    }

    /** Fills in every album that has no stored colours yet. Low priority, one album at a time. */
    suspend fun precomputeMissing() {
        invalidateStale()
        dao.all().forEach { memory[it.albumId] = it.toModel() }
        for (album in dao.missing()) {
            dao.upsert(compute(album.songId, album.albumId).toEntity())
            yield()
        }
    }

    private suspend fun compute(songId: Long, albumId: Long): AlbumPalette = withContext(worker) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        val bitmap = ArtworkDecoder.decodeAsync(context, songId, albumId, SAMPLE_PX)
        if (bitmap == null) {
            val remoteSeed = RemoteArtworkBridge.dominantOrThumbColor(songId, albumId)
            return@withContext if (remoteSeed != null) {
                val seed = CoverColour.fromAverage(remoteSeed)
                AlbumPalette(albumId, seed, seed, seed, hasArt = true)
            } else {
                AlbumPalette(albumId, FALLBACK, FALLBACK, FALLBACK, hasArt = false)
            }
        }
        extract(albumId, bitmap)
    }

    private val imageAccents = android.util.LruCache<String, Int>(32)

    /** Artist hero photos have timestamped paths, so a replacement naturally gets a fresh palette. */
    suspend fun accentForImage(path: String): Int? = withContext(worker) {
        imageAccents.get(path) ?: decodeFile(path)?.let { bitmap ->
            extract(0, bitmap).primary.also { imageAccents.put(path, it) }
        }
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
        // The colour that covers most of the cover (not the most vivid one), from the same analysis that picks the page colour.
        val dominant = opaquePixels(bitmap)?.let { CoverColour.analyze(it).dominant }
        // Mean brightness (0..1, sRGB weights) of the bottom and top twelfth of the picture.
        val rows = (bitmap.height / 12).coerceAtLeast(1)
        val px = IntArray(bitmap.width * rows)
        fun edge(top: Boolean): Float {
            bitmap.getPixels(px, 0, bitmap.width, 0, if (top) 0 else bitmap.height - rows, bitmap.width, rows)
            var sum = 0f
            for (c in px) sum += (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
            return sum / px.size
        }
        CoverSample(dominant, bottomLuminance = edge(top = false), topLuminance = edge(top = true))
    }

    private fun decodeFile(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = (bounds.outWidth / (SAMPLE_PX * 2)).coerceAtLeast(1)
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** The page colour of a cover: a seed with real colour in it, or a grey one when the cover has none (see [CoverColour]). */
    private fun extract(albumId: Long, bitmap: Bitmap): AlbumPalette {
        val a = opaquePixels(bitmap)?.let(CoverColour::analyze) ?: return AlbumPalette(albumId, FALLBACK, FALLBACK, FALLBACK, hasArt = true)
        return AlbumPalette(albumId, a.seed, a.secondary, a.tertiary, hasArt = true)
    }

    /** Only the non-transparent pixels, made opaque, so transparent areas (0x00000000) do not count as black. Null when there are none. */
    private fun opaquePixels(bitmap: Bitmap): IntArray? {
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var count = 0
        for (c in px) {
            if ((c ushr 24) >= 128) px[count++] = c or OPAQUE
        }
        return if (count == 0) null else px.copyOf(count)
    }

    private companion object {
        const val SAMPLE_PX = 64
        const val RULE_VERSION = 7 // 2: vibrant over dominant; 3: secondary = light vibrant, tertiary = dark vibrant; 4: primary = muted; 5: Material quantizer + Score, grey seed for covers with no colour; 6: vivid covers lift muted cluster colours; 7: server average colours are hue hints (lifted if coloured)
        val OPAQUE = 0xFF000000.toInt()
        val FALLBACK = Color.rgb(60, 60, 66)
    }
}

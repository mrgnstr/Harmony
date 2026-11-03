package com.mrgnstr.harmony.core.artwork

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "diffused artwork" background: art decoded at ~64 px, blurred once on the CPU, kept in a tiny LRU.
 * Drawn upscaled with bilinear filtering it looks like a large-radius blur on every API level for almost no cost.
 */
@Singleton
class DiffusedArtCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = LruCache<String, Bitmap>(8)

    suspend fun get(songId: Long, albumId: Long): Bitmap? {
        val key = ArtworkKey(songId, albumId).cacheKey
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val src = ArtworkDecoder.decode(context, songId, albumId, SIZE) ?: return@withContext null
            val small = Bitmap.createScaledBitmap(src, SIZE, SIZE, true).copy(Bitmap.Config.ARGB_8888, true)
            val px = IntArray(SIZE * SIZE)
            small.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            StackBlur.blur(px, SIZE, SIZE, RADIUS)
            small.setPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
            cache.put(key, small)
            small
        }
    }

    private companion object {
        const val SIZE = 48
        const val RADIUS = 5
    }
}

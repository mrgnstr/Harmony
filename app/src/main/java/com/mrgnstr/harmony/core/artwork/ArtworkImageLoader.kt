package com.mrgnstr.harmony.core.artwork

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.mrgnstr.harmony.core.device.VisualTier

object ArtworkImageLoader {
    /** Memory cache is sized by tier: small devices cannot afford a fifth of RAM in bitmaps. */
    fun create(context: Context, tier: VisualTier): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(ArtworkKeyer())
            add(ArtworkFetcher.Factory())
        }
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context, if (tier == VisualTier.LOW) 0.08 else 0.18).build()
        }
        .build()
}

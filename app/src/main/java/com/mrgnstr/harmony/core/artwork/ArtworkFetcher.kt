package com.mrgnstr.harmony.core.artwork

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension

class NoArtworkException : Exception("no artwork")

class ArtworkKeyer : Keyer<ArtworkKey> {
    override fun key(data: ArtworkKey, options: Options): String = data.cacheKey
}

/** Throws [NoArtworkException] when there is no art, so AsyncImage shows its error state (the placeholder). */
class ArtworkFetcher(private val key: ArtworkKey, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: DEFAULT_PX
        val h = (options.size.height as? Dimension.Pixels)?.px ?: DEFAULT_PX
        val bitmap = ArtworkDecoder.decodeAsync(options.context, key.songId, key.albumId, maxOf(w, h))
            ?: throw NoArtworkException()
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<ArtworkKey> {
        override fun create(data: ArtworkKey, options: Options, imageLoader: ImageLoader): Fetcher = ArtworkFetcher(data, options)
    }

    private companion object { const val DEFAULT_PX = 512 }
}

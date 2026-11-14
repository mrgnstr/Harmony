package com.mrgnstr.harmony.core.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.net.URI
import java.net.URLDecoder

/**
 * The disk cache of streamed songs (Media3 [SimpleCache] under the app's cache dir, so Android may also clear it when storage runs low).
 * What the player has fetched stays, up to [MAX_BYTES] (least recently used goes first): replaying, seeking back and a dropped connection
 * all read from it instead of the server. Only streams with a known length are cached (see PlaybackService): a converted stream has none,
 * and the bytes of two conversions of the same song are not the same, so pieces of one must never be joined to another.
 */
object StreamCache {
    const val MAX_BYTES = 1L shl 30

    private var cache: SimpleCache? = null

    /** One instance per process: a second [SimpleCache] on the same folder throws, and the service can be recreated. */
    @Synchronized
    fun get(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.cacheDir, "stream_cache"),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context),
    ).also { cache = it }

    /** The player's HTTP layer with the cache in front of [upstream]: what is read is kept, and what is kept is read from disk. */
    fun dataSourceFactory(cache: Cache, upstream: DataSource.Factory = DefaultHttpDataSource.Factory()): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory { spec -> spec.key ?: keyOf(spec.uri.toString()) ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Marks [spec] as not to be cached when the server does not say how long the data is (converted streams, live radio). */
    fun skipIfLengthUnknown(spec: DataSpec): DataSpec =
        spec.buildUpon().setFlags(spec.flags or DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN).build()

    /**
     * What identifies a stream in the cache: the server, the song and the quality asked for. The credentials in the URL are left out on
     * purpose (they are made when the library syncs and change with it, and a URL as the key would then never be found again).
     * Null for anything that is not a `/rest/stream` URL.
     */
    fun keyOf(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val path = uri.path ?: return null
        if (!path.endsWith("/rest/stream")) return null
        val params = (uri.rawQuery ?: return null).split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        val id = params["id"] ?: return null
        return "${uri.host}:${uri.port}$path|id=$id|maxBitRate=${params["maxBitRate"].orEmpty()}|format=${params["format"].orEmpty()}"
    }
}

package com.mrgnstr.harmony.core.artwork

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Static registry bridging `ArtworkDecoder` (which is called by Coil's `ArtworkFetcher` with `(songId, albumId)`)
 * to Navidrome's remote `getCoverArt` endpoint and `ThumbHash` placeholders.
 */
object RemoteArtworkBridge {
    data class ArtEntry(
        val coverArtId: String?,
        val thumbHash: String? = null,
        val dominantColorArgb: Int? = null,
    )

    private val albumEntries = ConcurrentHashMap<Long, ArtEntry>()
    private val songEntries = ConcurrentHashMap<Long, ArtEntry>()

    /** Callback wired by `NavidromeRepository` / `AppViewModel` to download `getCoverArt` bytes for a `coverArtId`. */
    @Volatile var remoteByteFetcher: (suspend (coverArtId: String, sizePx: Int) -> ByteArray?)? = null

    fun registerAlbum(albumId: Long, coverArtId: String?, thumbHash: String? = null, dominantColorHex: String? = null) {
        if (albumId == 0L) return
        val dom = ThumbHash.parseHexColor(dominantColorHex)
        if (coverArtId != null || thumbHash != null || dom != null) {
            albumEntries[albumId] = ArtEntry(coverArtId, thumbHash, dom)
        }
    }

    fun registerSong(songId: Long, albumId: Long, coverArtId: String?, thumbHash: String? = null, dominantColorHex: String? = null) {
        if (songId == 0L) return
        val dom = ThumbHash.parseHexColor(dominantColorHex)
        if (coverArtId != null || thumbHash != null || dom != null) {
            songEntries[songId] = ArtEntry(coverArtId, thumbHash, dom)
            if (albumId != 0L && !albumEntries.containsKey(albumId)) {
                albumEntries[albumId] = ArtEntry(coverArtId, thumbHash, dom)
            }
        }
    }

    fun entryFor(songId: Long, albumId: Long): ArtEntry? =
        albumEntries[albumId] ?: songEntries[songId]

    fun dominantOrThumbColor(songId: Long, albumId: Long): Int? {
        val entry = entryFor(songId, albumId) ?: return null
        entry.dominantColorArgb?.let { return it }
        entry.thumbHash?.let { b64 ->
            ThumbHash.decodeBase64(b64)?.let { return ThumbHash.averageArgb(it) }
        }
        return null
    }

    fun decodeThumbHashFallback(songId: Long, albumId: Long): Bitmap? {
        val th = entryFor(songId, albumId)?.thumbHash ?: return null
        return ThumbHash.decodeToBitmap(th)
    }

    fun clear() {
        albumEntries.clear()
        songEntries.clear()
    }
}

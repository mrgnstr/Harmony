package com.mrgnstr.harmony.core.artwork

/** What the UI asks Coil to load. Art is per album; [songId] is only the embedded-art fallback. */
data class ArtworkKey(val songId: Long, val albumId: Long) {
    /** Songs of one album share a cache entry (both positive MediaStore IDs and negative Navidrome IDs). */
    val cacheKey: String get() = if (albumId != 0L) "album:$albumId" else "song:$songId"
}

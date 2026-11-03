package com.mrgnstr.harmony.core.model

/** Aggregates derived from the songs table; the property names double as Room column aliases. */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    /** A song of the album, only used as the embedded-art fallback. */
    val artSongId: Long,
    val songCount: Int,
    val addedSec: Long,
    val durationMs: Long,
    /** Newest release year among its songs, 0 when untagged. */
    val year: Int = 0,
)

data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val artAlbumId: Long,
    val artSongId: Long,
    /** The user's own photo, shown instead of an album cover. */
    val imagePath: String? = null,
)

data class Genre(val name: String, val songCount: Int)

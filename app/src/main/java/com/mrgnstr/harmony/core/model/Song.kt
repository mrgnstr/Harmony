package com.mrgnstr.harmony.core.model

/** Pure Kotlin on purpose: this package has no Android dependencies. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val genre: String? = null,
    /** Release year from the file tags, 0 when unknown. */
    val year: Int = 0,
    /** True for FLAC/WAV/AIFF files (ALAC inside .m4a is not detected). */
    val lossless: Boolean = false,
) {
    /** content:// URI is derived from the id so the model stays Android-free. */
    val uriPath: String get() = "content://media/external/audio/media/$id"
}

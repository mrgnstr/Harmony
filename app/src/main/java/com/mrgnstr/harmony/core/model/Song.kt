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
    /** 0 for local MediaStore files, >0 for a Navidrome server account. */
    val serverId: Long = 0L,
    /** Navidrome/Subsonic track ID when [serverId] > 0. */
    val remoteId: String? = null,
    /** Navidrome/Subsonic coverArt ID (e.g. `al-..._<hash>` or `mf-..._<hash>`). */
    val coverArtId: String? = null,
    /** Resolved streaming or downloaded URI for remote tracks. */
    val streamUri: String? = null,
    val codec: String? = null,
    val bitRateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 2,
    val userRating: Int = 0,
    val replayGain: ReplayGainInfo? = null,
    val bookmarkPositionMs: Long = 0L,
    val explicitStatus: String? = null,
    val bpm: Int = 0,
    val comment: String? = null,
    val discSubtitle: String? = null,
    val downloadedPath: String? = null,
) {
    val isRemote: Boolean get() = serverId > 0L || id < 0L
    val isDownloaded: Boolean get() = !downloadedPath.isNullOrEmpty()

    /** Local file/download path, remote stream URL, or MediaStore content:// URI. */
    val uriPath: String get() = downloadedPath ?: streamUri ?: "content://media/external/audio/media/$id"
}

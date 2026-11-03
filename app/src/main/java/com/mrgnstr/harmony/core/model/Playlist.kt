package com.mrgnstr.harmony.core.model

/** The id of the built-in Favourites playlist: the favourite songs seen as a playlist (never stored in the playlists table). */
const val FAVOURITES_PLAYLIST_ID = -1L

/** One song's artwork reference (the artwork pipeline is keyed by song id + album id). */
data class ArtRef(val songId: Long, val albumId: Long)

/**
 * A user playlist. [coverPath] is a custom cover image in app storage; without one the cover is made from [art], the first
 * four songs with different album covers.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val coverPath: String?,
    val songCount: Int,
    val durationMs: Long,
    val updatedAtMs: Long,
    val art: List<ArtRef>,
) {
    val isFavourites: Boolean get() = id == FAVOURITES_PLAYLIST_ID
}

/** A song at one position of a playlist; [entryId] tells duplicates apart (the same song can be added twice). */
data class PlaylistEntry(val entryId: Long, val song: Song)

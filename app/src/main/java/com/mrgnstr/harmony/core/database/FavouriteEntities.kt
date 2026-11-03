package com.mrgnstr.harmony.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A favourite song. [songId] is the MediaStore id, deliberately not a foreign key (see [PlaylistSongEntity]). */
@Entity(tableName = "favourite_songs")
data class FavouriteSongEntity(
    @PrimaryKey val songId: Long,
    val addedAtMs: Long,
)

/**
 * A favourite album, on its own: favouriting an album does not favourite its songs. Identified like an album everywhere
 * else (art is per album id, the title tells apart editions that share one).
 */
@Entity(tableName = "favourite_albums", primaryKeys = ["albumId", "title"])
data class FavouriteAlbumEntity(
    val albumId: Long,
    val title: String,
    val addedAtMs: Long,
)

/** A favourite song joined with its index row; songs missing from the index drop out. */
data class FavouriteSongRow(val addedAtMs: Long, @Embedded val song: SongEntity)

/** An artist is identified by name, like artist navigation and custom images; its songs and albums stay independent. */
@Entity(tableName = "favourite_artists")
data class FavouriteArtistEntity(
    @PrimaryKey val name: String,
    val addedAtMs: Long,
)

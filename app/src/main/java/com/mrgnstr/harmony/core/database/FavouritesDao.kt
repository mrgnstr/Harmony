package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavouritesDao {
    @Query("SELECT songId FROM favourite_songs")
    abstract fun observeSongIds(): Flow<List<Long>>

    @Query("SELECT albumId, title FROM favourite_albums ORDER BY addedAtMs DESC")
    abstract fun observeAlbumKeys(): Flow<List<AlbumKey>>

    /** Newest favourite first. */
    @Query(
        """SELECT f.addedAtMs AS addedAtMs, s.* FROM favourite_songs f JOIN songs s ON s.id = f.songId
           ORDER BY f.addedAtMs DESC, f.songId DESC""",
    )
    abstract fun observeSongs(): Flow<List<FavouriteSongRow>>

    @Query("SELECT COUNT(*) FROM favourite_songs WHERE songId = :songId")
    protected abstract suspend fun songCount(songId: Long): Int

    @Insert
    protected abstract suspend fun insertSong(row: FavouriteSongEntity)

    @Query("DELETE FROM favourite_songs WHERE songId = :songId")
    abstract suspend fun deleteSong(songId: Long)

    /** Favourites the song, or removes it when it already is one. */
    @Transaction
    open suspend fun toggleSong(songId: Long, nowMs: Long): Boolean {
        return if (songCount(songId) > 0) {
            deleteSong(songId)
            false
        } else {
            insertSong(FavouriteSongEntity(songId, nowMs))
            true
        }
    }

    @Transaction
    open suspend fun setSongFavourite(songId: Long, starred: Boolean, nowMs: Long) {
        if (starred) {
            if (songCount(songId) == 0) insertSong(FavouriteSongEntity(songId, nowMs))
        } else {
            deleteSong(songId)
        }
    }

    @Query("SELECT COUNT(*) FROM favourite_albums WHERE albumId = :albumId AND title = :title")
    protected abstract suspend fun albumCount(albumId: Long, title: String): Int

    @Insert
    protected abstract suspend fun insertAlbum(row: FavouriteAlbumEntity)

    @Query("DELETE FROM favourite_albums WHERE albumId = :albumId AND title = :title")
    protected abstract suspend fun deleteAlbum(albumId: Long, title: String)

    @Transaction
    open suspend fun toggleAlbum(albumId: Long, title: String, nowMs: Long): Boolean {
        return if (albumCount(albumId, title) > 0) {
            deleteAlbum(albumId, title)
            false
        } else {
            insertAlbum(FavouriteAlbumEntity(albumId, title, nowMs))
            true
        }
    }

    @Transaction
    open suspend fun setAlbumFavourite(albumId: Long, title: String, starred: Boolean, nowMs: Long) {
        if (starred) {
            if (albumCount(albumId, title) == 0) insertAlbum(FavouriteAlbumEntity(albumId, title, nowMs))
        } else {
            deleteAlbum(albumId, title)
        }
    }

    @Query("SELECT name FROM favourite_artists ORDER BY addedAtMs DESC, name ASC")
    abstract fun observeArtistNames(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM favourite_artists WHERE name = :name")
    protected abstract suspend fun artistCount(name: String): Int

    @Insert
    protected abstract suspend fun insertArtist(row: FavouriteArtistEntity)

    @Query("DELETE FROM favourite_artists WHERE name = :name")
    protected abstract suspend fun deleteArtist(name: String)

    @Transaction
    open suspend fun toggleArtist(name: String, nowMs: Long): Boolean {
        return if (artistCount(name) > 0) {
            deleteArtist(name)
            false
        } else {
            insertArtist(FavouriteArtistEntity(name, nowMs))
            true
        }
    }

    @Transaction
    open suspend fun setArtistFavourite(name: String, starred: Boolean, nowMs: Long) {
        if (starred) {
            if (artistCount(name) == 0) insertArtist(FavouriteArtistEntity(name, nowMs))
        } else {
            deleteArtist(name)
        }
    }
}

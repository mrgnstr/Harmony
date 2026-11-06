package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Genre
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query(
        """SELECT albumId AS id, album AS title,
           CASE WHEN albumArtist = '' THEN artist ELSE albumArtist END AS artist,
           MIN(id) AS artSongId, COUNT(*) AS songCount, MAX(dateAddedSec) AS addedSec, SUM(durationMs) AS durationMs, MAX(year) AS year
           FROM songs GROUP BY albumId, album ORDER BY album COLLATE NOCASE ASC""",
    )
    fun observeAlbums(): Flow<List<Album>>

    @Query(
        """SELECT genre AS name, COUNT(*) AS songCount FROM songs
           WHERE genre IS NOT NULL AND genre != '' GROUP BY genre ORDER BY genre COLLATE NOCASE ASC""",
    )
    fun observeGenres(): Flow<List<Genre>>

    /** The albums that have at least one song in [genre]. */
    @Query("SELECT DISTINCT albumId, album AS title FROM songs WHERE genre = :genre COLLATE NOCASE")
    fun observeGenreAlbumKeys(genre: String): Flow<List<AlbumKey>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId AND album = :album ORDER BY discNumber, trackNumber, title COLLATE NOCASE")
    fun observeAlbumSongs(albumId: Long, album: String): Flow<List<SongEntity>>

    @Query("SELECT id, dateModifiedSec, genre, year, lossless FROM songs WHERE serverId = 0")
    suspend fun stamps(): List<SongStamp>

    @Query("SELECT id, dateModifiedSec, genre, year, lossless FROM songs WHERE serverId = :serverId")
    suspend fun remoteStamps(serverId: Long): List<SongStamp>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE serverId = :serverId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(serverId: Long, remoteId: String): SongEntity?

    @Query("UPDATE songs SET userRating = :rating WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Int)

    @Query("UPDATE songs SET downloadedPath = :path WHERE id = :id")
    suspend fun updateDownloadedPath(id: Long, path: String?)

    @Query("UPDATE songs SET bookmarkPositionMs = :positionMs WHERE id = :id")
    suspend fun updateBookmarkPosition(id: Long, positionMs: Long)

    @Query("DELETE FROM songs WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Upsert
    suspend fun upsert(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

/** An album's identity (art is per album id; the title tells apart editions that share one). */
data class AlbumKey(val albumId: Long, val title: String)

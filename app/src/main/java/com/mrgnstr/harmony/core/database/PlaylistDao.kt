package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        """SELECT ps.playlistId AS playlistId, s.id AS songId, s.albumId AS albumId, s.durationMs AS durationMs
           FROM playlist_songs ps JOIN songs s ON s.id = ps.songId ORDER BY ps.playlistId, ps.position""",
    )
    fun observeMembers(): Flow<List<PlaylistMemberRow>>

    @Query(
        """SELECT ps.id AS entryId, s.* FROM playlist_songs ps JOIN songs s ON s.id = ps.songId
           WHERE ps.playlistId = :playlistId ORDER BY ps.position""",
    )
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntryRow>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertSongs(rows: List<PlaylistSongEntity>)

    @Query("SELECT MAX(position) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int?

    @Query("SELECT coverPath FROM playlists WHERE id = :id")
    suspend fun coverPath(id: Long): String?

    @Query("UPDATE playlists SET name = :name, updatedAtMs = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET coverPath = :path, updatedAtMs = :now WHERE id = :id")
    suspend fun setCover(id: Long, path: String?, now: Long)

    @Query("UPDATE playlists SET updatedAtMs = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlist_songs WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

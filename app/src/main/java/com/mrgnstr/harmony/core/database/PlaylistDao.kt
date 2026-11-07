package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE serverId = :serverId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(serverId: Long, remoteId: String): PlaylistEntity?

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("DELETE FROM playlists WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

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

    /** Server ids of a playlist's remote songs in order; local songs are not part of the server's copy. */
    @Query("SELECT s.remoteId FROM playlist_songs ps JOIN songs s ON s.id = ps.songId WHERE ps.playlistId = :playlistId AND s.remoteId IS NOT NULL ORDER BY ps.position, ps.id")
    suspend fun remoteSongIds(playlistId: Long): List<String>

    @Query("UPDATE playlists SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteId: String)

    @Query("SELECT id, remoteId FROM playlists WHERE serverId = :serverId AND remoteId IS NOT NULL")
    suspend fun serverPlaylistIds(serverId: Long): List<PlaylistRemoteRef>

    /** The pull replaces the server's songs only: local songs in the same playlist stay, after them. */
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId < 0")
    suspend fun clearRemoteSongs(playlistId: Long)

    @Query("UPDATE playlist_songs SET position = position + :by WHERE playlistId = :playlistId AND songId > 0")
    suspend fun shiftLocalSongs(playlistId: Long, by: Int)
}

data class PlaylistRemoteRef(val id: Long, val remoteId: String)

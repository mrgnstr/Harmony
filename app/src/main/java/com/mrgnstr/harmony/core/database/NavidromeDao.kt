package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NavidromeDao {
    // --- Server Accounts ---
    @Query("SELECT * FROM server_accounts ORDER BY id ASC")
    fun observeAccounts(): Flow<List<ServerAccountEntity>>

    @Query("SELECT * FROM server_accounts WHERE active = 1 ORDER BY id ASC LIMIT 1")
    fun observeActiveAccount(): Flow<ServerAccountEntity?>

    @Query("SELECT * FROM server_accounts WHERE active = 1 ORDER BY id ASC LIMIT 1")
    suspend fun activeAccount(): ServerAccountEntity?

    @Query("SELECT * FROM server_accounts WHERE id = :id")
    suspend fun accountById(id: Long): ServerAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: ServerAccountEntity): Long

    @Upsert
    suspend fun upsertAccount(account: ServerAccountEntity)

    @Query("UPDATE server_accounts SET active = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveAccount(activeId: Long)

    @Query("UPDATE server_accounts SET jwtToken = :jwt WHERE id = :serverId")
    suspend fun updateJwtToken(serverId: Long, jwt: String)

    @Query("UPDATE server_accounts SET lastSyncMs = :syncMs WHERE id = :serverId")
    suspend fun updateLastSync(serverId: Long, syncMs: Long)

    @Query("DELETE FROM server_accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    // --- Remote Albums ---
    @Query("SELECT * FROM remote_albums WHERE albumId = :albumId")
    fun observeAlbumExtra(albumId: Long): Flow<RemoteAlbumEntity?>

    @Query("SELECT * FROM remote_albums WHERE albumId = :albumId")
    suspend fun albumExtra(albumId: Long): RemoteAlbumEntity?

    @Query("SELECT * FROM remote_albums")
    suspend fun allAlbumExtras(): List<RemoteAlbumEntity>

    @Upsert
    suspend fun upsertAlbumExtras(items: List<RemoteAlbumEntity>)

    @Query("UPDATE remote_albums SET userRating = :rating WHERE albumId = :albumId")
    suspend fun updateAlbumRating(albumId: Long, rating: Int)

    @Query("DELETE FROM remote_albums WHERE serverId = :serverId")
    suspend fun deleteAlbumExtrasByServer(serverId: Long)

    // --- Remote Artists ---
    @Query("SELECT * FROM remote_artists WHERE name = :name COLLATE NOCASE LIMIT 1")
    fun observeArtistExtraByName(name: String): Flow<RemoteArtistEntity?>

    @Query("SELECT * FROM remote_artists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun artistExtraByName(name: String): RemoteArtistEntity?

    @Query("SELECT * FROM remote_artists")
    suspend fun allArtistExtras(): List<RemoteArtistEntity>

    @Query("SELECT name, coverArtId FROM remote_artists WHERE coverArtId IS NOT NULL")
    fun observeArtistCovers(): Flow<List<ArtistCoverRow>>

    @Upsert
    suspend fun upsertArtistExtras(items: List<RemoteArtistEntity>)

    @Query("UPDATE remote_artists SET userRating = :rating WHERE name = :name COLLATE NOCASE")
    suspend fun updateArtistRating(name: String, rating: Int)

    @Query("DELETE FROM remote_artists WHERE serverId = :serverId")
    suspend fun deleteArtistExtrasByServer(serverId: Long)

    // --- Radio Stations ---
    @Query("SELECT * FROM radio_stations ORDER BY name COLLATE NOCASE ASC")
    fun observeRadioStations(): Flow<List<RadioStationEntity>>

    @Upsert
    suspend fun upsertRadioStations(items: List<RadioStationEntity>)

    @Query("DELETE FROM radio_stations WHERE serverId = :serverId")
    suspend fun deleteRadioStationsByServer(serverId: Long)

    @Query("DELETE FROM radio_stations WHERE id = :id")
    suspend fun deleteRadioStation(id: Long)

    // --- Audio Bookmarks ---
    @Query("SELECT * FROM audio_bookmarks ORDER BY changedMs DESC")
    fun observeBookmarks(): Flow<List<AudioBookmarkEntity>>

    @Query("SELECT * FROM audio_bookmarks WHERE songId = :songId")
    fun observeBookmarkForSong(songId: Long): Flow<AudioBookmarkEntity?>

    @Upsert
    suspend fun upsertBookmarks(items: List<AudioBookmarkEntity>)

    @Upsert
    suspend fun upsertBookmark(item: AudioBookmarkEntity)

    @Query("DELETE FROM audio_bookmarks WHERE songId = :songId")
    suspend fun deleteBookmark(songId: Long)

    @Query("DELETE FROM audio_bookmarks WHERE serverId = :serverId")
    suspend fun deleteBookmarksByServer(serverId: Long)

    // --- Pending Mutations Outbox ---
    @Query("SELECT * FROM pending_mutations WHERE serverId = :serverId ORDER BY id ASC")
    suspend fun pendingMutations(serverId: Long): List<PendingMutationEntity>

    @Insert
    suspend fun insertMutation(mutation: PendingMutationEntity): Long

    @Query("DELETE FROM pending_mutations WHERE id = :id")
    suspend fun deleteMutation(id: Long)

    @Query("DELETE FROM pending_mutations WHERE serverId = :serverId")
    suspend fun deleteMutationsByServer(serverId: Long)
}

/** An artist's name with its server picture id. */
data class ArtistCoverRow(val name: String, val coverArtId: String)

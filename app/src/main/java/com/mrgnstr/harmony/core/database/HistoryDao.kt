package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {
    @Insert
    protected abstract suspend fun insert(row: HistoryEntity)

    // Two statements instead of an upsert: SQLite on API 24/25 predates ON CONFLICT DO UPDATE.
    @Query("UPDATE play_counts SET count = count + 1, lastPlayedMs = :playedAtMs WHERE songId = :songId")
    protected abstract suspend fun bumpCount(songId: Long, playedAtMs: Long): Int

    @Query("INSERT INTO play_counts (songId, count, lastPlayedMs) VALUES (:songId, 1, :playedAtMs)")
    protected abstract suspend fun insertCount(songId: Long, playedAtMs: Long)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT :keep)")
    protected abstract suspend fun prune(keep: Int)

    /** Appends a play, counts it, and drops the oldest history rows beyond [keep] (counts are kept). */
    @Transaction
    open suspend fun record(songId: Long, playedAtMs: Long, keep: Int) {
        insert(HistoryEntity(songId = songId, playedAtMs = playedAtMs))
        if (bumpCount(songId, playedAtMs) == 0) insertCount(songId, playedAtMs)
        prune(keep)
    }

    @Query("SELECT songId, count FROM play_counts")
    abstract fun observePlayCounts(): Flow<List<PlayCount>>

    /** The latest [limit] plays, newest first, joined with their index rows (songs missing from the index drop out). */
    @Query(
        """SELECT h.id AS entryId, s.* FROM history h JOIN songs s ON s.id = h.songId
           ORDER BY h.id DESC LIMIT :limit""",
    )
    abstract fun observeLatest(limit: Int): Flow<List<PlaylistEntryRow>>

    @Query("DELETE FROM history WHERE id = :id")
    abstract suspend fun delete(id: Long)
}

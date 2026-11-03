package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SearchDao {
    @Query("SELECT songs.* FROM songs JOIN songs_fts ON songs.id = songs_fts.rowid WHERE songs_fts MATCH :match ORDER BY songs.title COLLATE NOCASE, songs.id")
    abstract fun songs(match: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM recent_searches ORDER BY usedAtMs DESC, rowid DESC LIMIT 20")
    abstract fun recents(): Flow<List<RecentSearchEntity>>

    @Upsert
    protected abstract suspend fun upsertRecent(recent: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE key NOT IN (SELECT key FROM recent_searches ORDER BY usedAtMs DESC, rowid DESC LIMIT 20)")
    protected abstract suspend fun trimRecents()

    @Transaction
    open suspend fun remember(recent: RecentSearchEntity) {
        upsertRecent(recent)
        trimRecents()
    }

    @Query("DELETE FROM recent_searches WHERE key = :key")
    abstract suspend fun removeRecent(key: String)

    @Query("DELETE FROM recent_searches")
    abstract suspend fun clearRecents()
}

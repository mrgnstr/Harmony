package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class QueueDao {
    @Query("DELETE FROM queue_items WHERE list = :list")
    protected abstract suspend fun deleteList(list: Int)

    @Insert
    protected abstract suspend fun insertItems(rows: List<QueueItemEntity>)

    /** Swaps the whole list in one transaction, so a crash never leaves half a queue. */
    @Transaction
    open suspend fun replaceItems(list: Int, rows: List<QueueItemEntity>) {
        deleteList(list)
        // Chunked: the insert statement is fine at any size, but a huge single list is slow to bind on old devices.
        rows.chunked(500).forEach { insertItems(it) }
    }

    @Query("DELETE FROM queue_state")
    protected abstract suspend fun deleteState()

    /** Forgets the saved queue (it was cleared). */
    @Transaction
    open suspend fun clearAll() {
        deleteList(QueueItemEntity.LIST_PLAY)
        deleteList(QueueItemEntity.LIST_UNSHUFFLED)
        deleteState()
    }

    @Upsert
    abstract suspend fun saveState(state: QueueStateEntity)

    @Query("SELECT * FROM queue_state WHERE id = 0")
    abstract suspend fun state(): QueueStateEntity?

    @Query("SELECT * FROM queue_items WHERE list = :list ORDER BY position")
    abstract suspend fun items(list: Int): List<QueueItemEntity>

    @Query(
        """SELECT q.position AS position, q.queued AS queued, s.* FROM queue_items q JOIN songs s ON s.id = q.songId
           WHERE q.list = :list ORDER BY q.position""",
    )
    abstract suspend fun restoreRows(list: Int): List<QueueRestoreRow>
}

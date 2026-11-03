package com.mrgnstr.harmony.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One slot of the saved play order. [list] is [LIST_PLAY] (the order being played) or [LIST_UNSHUFFLED] (what turning shuffle
 * off goes back to). [songId] is the MediaStore id, deliberately not a foreign key (see [PlaylistSongEntity]).
 */
@Entity(tableName = "queue_items", primaryKeys = ["list", "position"])
data class QueueItemEntity(
    val list: Int,
    val position: Int,
    val songId: Long,
    /** Added with Play Next / Play Last, so it belongs to the queue view's "Queue" section. */
    val queued: Boolean,
) {
    companion object {
        const val LIST_PLAY = 0
        const val LIST_UNSHUFFLED = 1
    }
}

/** The single row (id 0) describing where in the saved queue playback was. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    /** Player.REPEAT_MODE_* */
    val repeatMode: Int,
    val shuffled: Boolean,
    val source: String?,
    val updatedAtMs: Long,
)

/** A saved queue slot joined with its index row. Songs missing from the index drop out. */
data class QueueRestoreRow(val position: Int, val queued: Boolean, @Embedded val song: SongEntity)

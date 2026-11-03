package com.mrgnstr.harmony.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One play, newest has the highest [id]. [songId] is the MediaStore id, deliberately not a foreign key (see
 * [PlaylistSongEntity]): a song missing from the index just drops out of the history view.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAtMs: Long,
)

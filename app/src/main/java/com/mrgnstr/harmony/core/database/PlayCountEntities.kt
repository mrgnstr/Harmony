package com.mrgnstr.harmony.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lifetime plays per song (the history keeps only the latest few hundred). [songId] is the MediaStore id, not a foreign key,
 * like the history's.
 */
@Entity(tableName = "play_counts")
data class PlayCountEntity(
    @PrimaryKey val songId: Long,
    val count: Int,
    val lastPlayedMs: Long,
)

data class PlayCount(val songId: Long, val count: Int)

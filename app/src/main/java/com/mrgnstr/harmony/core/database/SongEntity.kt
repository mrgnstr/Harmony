package com.mrgnstr.harmony.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mrgnstr.harmony.core.model.Song

/** Index of one MediaStore audio row. [id] is the MediaStore _ID, so user data can reference it stably. */
@Entity(tableName = "songs", indices = [Index("albumId"), Index("artist"), Index("album")])
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val genre: String?,
    @ColumnInfo(defaultValue = "0") val year: Int = 0,
    @ColumnInfo(defaultValue = "0") val lossless: Boolean = false,
)

/** What sync compares to decide whether a row changed. */
data class SongStamp(val id: Long, val dateModifiedSec: Long, val genre: String?, val year: Int = 0, val lossless: Boolean = false)

fun SongEntity.toModel() = Song(
    id, title, artist, albumArtist, album, albumId, durationMs, trackNumber, discNumber,
    dateAddedSec, dateModifiedSec, genre, year, lossless,
)

fun Song.toEntity() = SongEntity(
    id, title, artist, albumArtist, album, albumId, durationMs, trackNumber, discNumber,
    dateAddedSec, dateModifiedSec, genre, year, lossless,
)

package com.mrgnstr.harmony.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Absolute path of a custom cover in app storage, null for the generated one. */
    val coverPath: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    @ColumnInfo(defaultValue = "0") val serverId: Long = 0L,
    val remoteId: String? = null,
    val comment: String? = null,
    @ColumnInfo(defaultValue = "0") val isPublic: Boolean = false,
    val owner: String? = null,
    @ColumnInfo(defaultValue = "0") val isSmart: Boolean = false,
    @ColumnInfo(defaultValue = "0") val readonly: Boolean = false,
    val validUntilMs: Long? = null,
    val rulesJson: String? = null,
    val coverArtId: String? = null,
    @ColumnInfo(defaultValue = "0") val userRating: Int = 0,
    @ColumnInfo(defaultValue = "0") val starred: Boolean = false,
)

/**
 * A song in a playlist. [songId] is the MediaStore id (the same as songs.id) but deliberately not a foreign key: the song
 * index is rebuilt by sync, and a playlist should survive a song being temporarily absent from it.
 */
@Entity(
    tableName = "playlist_songs",
    foreignKeys = [ForeignKey(PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("playlistId", "position")],
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAtMs: Long,
)

/** One playlist song joined with its index row, for cover art and durations. Songs missing from the index drop out. */
data class PlaylistMemberRow(val playlistId: Long, val songId: Long, val albumId: Long, val durationMs: Long)

data class PlaylistEntryRow(
    @ColumnInfo(name = "entryId") val entryId: Long,
    @Embedded val song: SongEntity,
)

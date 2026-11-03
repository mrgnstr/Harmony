package com.mrgnstr.harmony.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mrgnstr.harmony.core.model.AlbumPalette

@Entity(tableName = "album_colors")
data class AlbumColorEntity(
    @PrimaryKey val albumId: Long,
    val c1: Int,
    val c2: Int,
    val c3: Int,
    val hasArt: Boolean,
)

/** An album with no stored colours yet, plus one of its songs (for embedded-art fallback). */
data class MissingAlbum(val albumId: Long, val songId: Long)

fun AlbumColorEntity.toModel() = AlbumPalette(albumId, c1, c2, c3, hasArt)
fun AlbumPalette.toEntity() = AlbumColorEntity(albumId, primary, secondary, tertiary, hasArt)

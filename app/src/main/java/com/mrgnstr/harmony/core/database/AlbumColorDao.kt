package com.mrgnstr.harmony.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AlbumColorDao {
    @Query("SELECT * FROM album_colors WHERE albumId = :albumId")
    suspend fun get(albumId: Long): AlbumColorEntity?

    @Query("SELECT * FROM album_colors")
    suspend fun all(): List<AlbumColorEntity>

    @Query("DELETE FROM album_colors")
    suspend fun clear()

    @Upsert
    suspend fun upsert(entity: AlbumColorEntity)

    @Query(
        "SELECT albumId, MIN(id) AS songId FROM songs " +
            "WHERE albumId > 0 AND albumId NOT IN (SELECT albumId FROM album_colors) GROUP BY albumId",
    )
    suspend fun missing(): List<MissingAlbum>
}

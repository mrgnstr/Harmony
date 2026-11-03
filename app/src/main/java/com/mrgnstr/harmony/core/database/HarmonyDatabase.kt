package com.mrgnstr.harmony.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Schemas are exported to app/schemas. Never add destructive fallback: from the playlists
 * milestone on this file also holds user data, so every bump needs a real Migration.
 */
@Database(entities = [SongEntity::class, AlbumColorEntity::class, PlaylistEntity::class, PlaylistSongEntity::class, QueueItemEntity::class, QueueStateEntity::class, HistoryEntity::class, FavouriteSongEntity::class, FavouriteAlbumEntity::class, FavouriteArtistEntity::class, SongSearchEntity::class, RecentSearchEntity::class, PlayCountEntity::class], version = 11)
abstract class HarmonyDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumColorDao(): AlbumColorDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchDao(): SearchDao
    abstract fun favouritesDao(): FavouritesDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HarmonyDatabase =
        Room.databaseBuilder(context, HarmonyDatabase::class.java, "harmony.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()

    @Provides
    fun songDao(db: HarmonyDatabase): SongDao = db.songDao()

    @Provides
    fun albumColorDao(db: HarmonyDatabase): AlbumColorDao = db.albumColorDao()

    @Provides
    fun playlistDao(db: HarmonyDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun queueDao(db: HarmonyDatabase): QueueDao = db.queueDao()

    @Provides
    fun historyDao(db: HarmonyDatabase): HistoryDao = db.historyDao()

    @Provides
    fun searchDao(db: HarmonyDatabase): SearchDao = db.searchDao()

    @Provides
    fun favouritesDao(db: HarmonyDatabase): FavouritesDao = db.favouritesDao()
}

/** v2: per-album artwork colours. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `album_colors` (`albumId` INTEGER NOT NULL, `c1` INTEGER NOT NULL, " +
                "`c2` INTEGER NOT NULL, `c3` INTEGER NOT NULL, `hasArt` INTEGER NOT NULL, PRIMARY KEY(`albumId`))",
        )
    }
}

/** v3: release year per song. Existing rows read 0 until the next sync stamps them with the MediaStore value. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `year` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v4: lossless flag (FLAC/WAV/AIFF by MIME type), filled in by the next sync. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `lossless` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v5: user playlists. These tables hold user data, so they are never rebuilt by sync. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`coverPath` TEXT, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `addedAtMs` INTEGER NOT NULL, " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId_position` ON `playlist_songs` (`playlistId`, `position`)")
    }
}

/** v6: the saved play queue (order, unshuffled order, position, modes), restored on launch. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `queue_items` (`list` INTEGER NOT NULL, `position` INTEGER NOT NULL, `songId` INTEGER NOT NULL, " +
                "`queued` INTEGER NOT NULL, PRIMARY KEY(`list`, `position`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `queue_state` (`id` INTEGER NOT NULL, `currentIndex` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, " +
                "`repeatMode` INTEGER NOT NULL, `shuffled` INTEGER NOT NULL, `source` TEXT, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/** v7: the play history shown above the queue. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `songId` INTEGER NOT NULL, " +
                "`playedAtMs` INTEGER NOT NULL)",
        )
    }
}

/** v8: favourite songs and favourite albums (separate: an album being a favourite says nothing about its songs). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `favourite_songs` (`songId` INTEGER NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favourite_albums` (`albumId` INTEGER NOT NULL, `title` TEXT NOT NULL, `addedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`albumId`, `title`))",
        )
    }
}

/** v9: favourite artists, independent of favourite songs and albums. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `favourite_artists` (`name` TEXT NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY KEY(`name`))")
    }
}

/** v10: full-text library search and persistent recent queries. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `songs_fts` USING FTS4(`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `albumArtist` TEXT NOT NULL, tokenize=unicode61, content=`songs`)")
        db.execSQL("INSERT INTO `songs_fts`(`songs_fts`) VALUES('rebuild')")
        db.execSQL("CREATE TABLE IF NOT EXISTS `recent_searches` (`key` TEXT NOT NULL, `query` TEXT NOT NULL, `usedAtMs` INTEGER NOT NULL, PRIMARY KEY(`key`))")
    }
}

/** v11 (M3 app): lifetime play counts for Top Songs, seeded from the history that exists. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `play_counts` (`songId` INTEGER NOT NULL, `count` INTEGER NOT NULL, `lastPlayedMs` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
        db.execSQL("INSERT INTO `play_counts` (`songId`, `count`, `lastPlayedMs`) SELECT `songId`, COUNT(*), MAX(`playedAtMs`) FROM `history` GROUP BY `songId`")
    }
}

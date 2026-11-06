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
@Database(
    entities = [
        SongEntity::class,
        AlbumColorEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        QueueItemEntity::class,
        QueueStateEntity::class,
        HistoryEntity::class,
        FavouriteSongEntity::class,
        FavouriteAlbumEntity::class,
        FavouriteArtistEntity::class,
        SongSearchEntity::class,
        RecentSearchEntity::class,
        PlayCountEntity::class,
        ServerAccountEntity::class,
        RemoteAlbumEntity::class,
        RemoteArtistEntity::class,
        RadioStationEntity::class,
        AudioBookmarkEntity::class,
        PendingMutationEntity::class,
    ],
    version = 12,
)
abstract class HarmonyDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumColorDao(): AlbumColorDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchDao(): SearchDao
    abstract fun favouritesDao(): FavouritesDao
    abstract fun navidromeDao(): NavidromeDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HarmonyDatabase =
        Room.databaseBuilder(context, HarmonyDatabase::class.java, "harmony.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                MIGRATION_11_12,
            )
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

    @Provides
    fun navidromeDao(db: HarmonyDatabase): NavidromeDao = db.navidromeDao()
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

/** v12: Navidrome / OpenSubsonic accounts, remote catalog metadata, radio stations, bookmarks, and offline mutation outbox. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Extend songs
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `serverId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `remoteId` TEXT")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `coverArtId` TEXT")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `streamUri` TEXT")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `codec` TEXT")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `bitRateKbps` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `sampleRateHz` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `bitDepth` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `channels` INTEGER NOT NULL DEFAULT 2")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `userRating` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `rgTrackGain` REAL")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `rgTrackPeak` REAL")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `rgAlbumGain` REAL")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `rgAlbumPeak` REAL")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `bookmarkPositionMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `explicitStatus` TEXT")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `bpm` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `comment` TEXT")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `discSubtitle` TEXT")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `downloadedPath` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_serverId` ON `songs` (`serverId`)")

        // Extend playlists
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `serverId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `remoteId` TEXT")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `comment` TEXT")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `isPublic` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `owner` TEXT")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `isSmart` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `readonly` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `validUntilMs` INTEGER")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `rulesJson` TEXT")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `coverArtId` TEXT")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `userRating` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `starred` INTEGER NOT NULL DEFAULT 0")

        // Server accounts
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `server_accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`baseUrl` TEXT NOT NULL, `username` TEXT NOT NULL, `authMethod` TEXT NOT NULL, `passwordOrToken` TEXT NOT NULL, " +
                "`subsonicSalt` TEXT NOT NULL, `subsonicToken` TEXT NOT NULL, `apiKey` TEXT, `jwtToken` TEXT, `serverType` TEXT NOT NULL, " +
                "`serverVersion` TEXT NOT NULL, `openSubsonic` INTEGER NOT NULL, `extensionsCsv` TEXT NOT NULL, `selectedFoldersCsv` TEXT NOT NULL, " +
                "`isAdmin` INTEGER NOT NULL, `downloadRole` INTEGER NOT NULL, `shareRole` INTEGER NOT NULL, `jukeboxRole` INTEGER NOT NULL, " +
                "`scrobblingEnabled` INTEGER NOT NULL, `allowSelfSignedTls` INTEGER NOT NULL, `active` INTEGER NOT NULL, `lastSyncMs` INTEGER NOT NULL)",
        )

        // Remote album extras
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `remote_albums` (`albumId` INTEGER NOT NULL, `serverId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, " +
                "`coverArtId` TEXT, `thumbHash` TEXT, `dominantColor` TEXT, `releaseTypesJson` TEXT NOT NULL, `version` TEXT, " +
                "`recordLabelsJson` TEXT NOT NULL, `catalogNum` TEXT, `originalReleaseDate` TEXT, `releaseDate` TEXT, `isCompilation` INTEGER NOT NULL, " +
                "`description` TEXT, `mbzAlbumId` TEXT, `externalUrl` TEXT, `userRating` INTEGER NOT NULL, `averageRating` REAL NOT NULL, " +
                "`discTitlesJson` TEXT NOT NULL, `participantsJson` TEXT NOT NULL, PRIMARY KEY(`albumId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_albums_serverId` ON `remote_albums` (`serverId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_albums_remoteId` ON `remote_albums` (`remoteId`)")

        // Remote artist extras
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `remote_artists` (`remoteId` TEXT NOT NULL, `serverId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`sortName` TEXT, `coverArtId` TEXT, `thumbHash` TEXT, `dominantColor` TEXT, `biography` TEXT, `mbzArtistId` TEXT, " +
                "`externalUrl` TEXT, `rolesJson` TEXT NOT NULL, `similarArtistsJson` TEXT NOT NULL, `userRating` INTEGER NOT NULL, " +
                "`averageRating` REAL NOT NULL, PRIMARY KEY(`remoteId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_artists_name` ON `remote_artists` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_artists_serverId` ON `remote_artists` (`serverId`)")

        // Radio stations
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `radio_stations` (`id` INTEGER NOT NULL, `serverId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `streamUrl` TEXT NOT NULL, `homePageUrl` TEXT, `coverArtId` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_radio_stations_serverId` ON `radio_stations` (`serverId`)")

        // Audio bookmarks
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audio_bookmarks` (`songId` INTEGER NOT NULL, `serverId` INTEGER NOT NULL, `remoteSongId` TEXT NOT NULL, " +
                "`songTitle` TEXT NOT NULL, `artist` TEXT NOT NULL, `albumId` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, " +
                "`comment` TEXT NOT NULL, `createdMs` INTEGER NOT NULL, `changedMs` INTEGER NOT NULL, PRIMARY KEY(`songId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_bookmarks_serverId` ON `audio_bookmarks` (`serverId`)")

        // Offline mutation outbox
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_mutations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `serverId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `targetId` TEXT NOT NULL, `targetKind` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_mutations_serverId` ON `pending_mutations` (`serverId`)")
    }
}

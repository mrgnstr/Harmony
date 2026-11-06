package com.mrgnstr.harmony

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.MIGRATION_11_12
import com.mrgnstr.harmony.core.database.ServerAccountEntity
import com.mrgnstr.harmony.core.database.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that upgrading from Room v11 to v12 preserves local songs/playlists/counts and creates all Navidrome tables. */
@RunWith(AndroidJUnit4::class)
class Migration11To12DeviceTest {
    @Test
    fun migration11To12PreservesLocalDataAndSupportsRemoteEntities() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-11-12-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        try {
            val json = InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.mrgnstr.harmony.core.database.HarmonyDatabase/11.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                val entities = json.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) {
                        old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = json.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL(
                    "INSERT INTO songs (id, title, artist, albumArtist, album, albumId, durationMs, trackNumber, discNumber, dateAddedSec, dateModifiedSec, genre, year, lossless) " +
                        "VALUES (42, 'Local Track', 'Local Artist', 'Local Artist', 'Local Album', 100, 180000, 1, 1, 1000, 1000, 'Electronic', 2024, 1)",
                )
                old.version = 11
            }

            val db = Room.databaseBuilder(context, HarmonyDatabase::class.java, name)
                .addMigrations(MIGRATION_11_12)
                .build()
            try {
                val localSongs = db.songDao().observeAll().first()
                assertEquals(1, localSongs.size)
                assertEquals("Local Track", localSongs.first().title)
                assertEquals(0L, localSongs.first().serverId)

                // Insert a Navidrome server account and a remote song
                val serverId = db.navidromeDao().insertAccount(
                    ServerAccountEntity(
                        name = "Home Navidrome",
                        baseUrl = "https://music.example.com",
                        username = "admin",
                        authMethod = "Password",
                        passwordOrToken = "secret",
                        subsonicSalt = "salt",
                        subsonicToken = "token",
                        apiKey = null,
                        jwtToken = "jwt",
                        serverType = "navidrome",
                        serverVersion = "0.64.2",
                        openSubsonic = true,
                        extensionsCsv = "transcoding,songLyrics,indexBasedQueue",
                        selectedFoldersCsv = "",
                        isAdmin = true,
                        downloadRole = true,
                        shareRole = true,
                        jukeboxRole = true,
                        scrobblingEnabled = true,
                        allowSelfSignedTls = false,
                        active = true,
                        lastSyncMs = 123456L,
                    ),
                )
                db.songDao().upsert(
                    listOf(
                        SongEntity(
                            id = -9001L,
                            title = "Remote Track",
                            artist = "Remote Artist",
                            albumArtist = "Remote Artist",
                            album = "Remote Album",
                            albumId = -500L,
                            durationMs = 240_000L,
                            trackNumber = 2,
                            discNumber = 1,
                            dateAddedSec = 2000L,
                            dateModifiedSec = 2000L,
                            genre = "Synthwave",
                            year = 2025,
                            lossless = true,
                            serverId = serverId,
                            remoteId = "nd_song_1",
                            coverArtId = "al-nd_alb_1",
                            streamUri = "https://music.example.com/rest/stream?id=nd_song_1",
                            codec = "FLAC",
                            bitRateKbps = 980,
                            sampleRateHz = 96_000,
                            bitDepth = 24,
                            channels = 2,
                            userRating = 5,
                        ),
                    ),
                )

                // Local stamps only return serverId = 0, so local sync never deletes remote songs
                assertEquals(listOf(42L), db.songDao().stamps().map { it.id })
                assertEquals(listOf(-9001L), db.songDao().remoteStamps(serverId).map { it.id })
                assertNotNull(db.navidromeDao().activeAccount())
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

package com.mrgnstr.harmony

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.MIGRATION_9_10
import com.mrgnstr.harmony.core.database.MIGRATION_10_11
import com.mrgnstr.harmony.core.database.RecentSearchEntity
import com.mrgnstr.harmony.core.database.SongEntity
import com.mrgnstr.harmony.core.model.SearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchDeviceTest {
    private lateinit var context: Context
    private lateinit var db: HarmonyDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HarmonyDatabase::class.java).build()
    }

    @After fun tearDown() { db.close() }

    private fun song(id: Long, title: String) = SongEntity(id, title, "Beyoncé", "Beyoncé", "Déjà Vu", 10, 60_000, 1, 1, 1, 1, null)
    private suspend fun search(query: String) = db.searchDao().songs(requireNotNull(SearchQuery.match(query))).first().map { it.id }

    @Test fun searchHandlesPrefixesAccentsAndIndexChanges() = runBlocking {
        db.songDao().upsert(listOf(song(1, "Compass"), song(2, "Rock 'n' Roll")))
        assertEquals(listOf(1L), search("comp"))
        assertEquals(listOf(1L, 2L), search("bey"))
        assertEquals(listOf(1L, 2L), search("deja"))
        assertEquals(listOf(1L, 2L), search("bey deja"))
        assertEquals(listOf(2L), search("rock n"))
        assertTrue(search("foo OR bar").isEmpty())
        db.songDao().upsert(listOf(song(1, "North")))
        assertTrue(search("comp").isEmpty())
        assertEquals(listOf(1L), search("north"))
        db.songDao().deleteByIds(listOf(1))
        assertTrue(search("north").isEmpty())
    }

    @Test fun recentsAreDeduplicatedOrderedCappedAndRemovable() = runBlocking {
        val dao = db.searchDao()
        repeat(25) { dao.remember(RecentSearchEntity("q$it", "q$it", it.toLong())) }
        assertEquals(20, dao.recents().first().size)
        assertEquals("q24", dao.recents().first().first().query)
        dao.remember(RecentSearchEntity("q10", "Q10", 100))
        assertEquals(20, dao.recents().first().size)
        assertEquals("Q10", dao.recents().first().first().query)
        dao.removeRecent("q10")
        assertEquals(19, dao.recents().first().size)
        dao.clearRecents()
        assertTrue(dao.recents().first().isEmpty())
    }

    @Test fun migrationIndexesExistingSongsAndPreservesFavourites() = runBlocking {
        val name = "search-migration-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        try {
            val json = InstrumentationRegistry.getInstrumentation().context.assets
                .open("com.mrgnstr.harmony.core.database.HarmonyDatabase/9.json")
                .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                val entities = json.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices")
                    if (indices != null) for (j in 0 until indices.length()) {
                        old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}", table))
                    }
                }
                val setup = json.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.execSQL("INSERT INTO songs VALUES (1, 'Compass', 'Mili', 'Mili', 'Album', 10, 60000, 1, 1, 1, 1, NULL, 2026, 0)")
                old.execSQL("INSERT INTO favourite_artists VALUES ('Mili', 100)")
                old.version = 9
            }
            val upgraded = Room.databaseBuilder(context, HarmonyDatabase::class.java, name).addMigrations(MIGRATION_9_10, MIGRATION_10_11).build()
            try {
                assertEquals(listOf(1L), upgraded.searchDao().songs(requireNotNull(SearchQuery.match("comp"))).first().map { it.id })
                assertEquals(listOf("Mili"), upgraded.favouritesDao().observeArtistNames().first())
                upgraded.songDao().upsert(listOf(song(2, "New Song")))
                assertEquals(listOf(2L), upgraded.searchDao().songs(requireNotNull(SearchQuery.match("new"))).first().map { it.id })
                upgraded.songDao().deleteByIds(listOf(1))
                assertTrue(upgraded.searchDao().songs(requireNotNull(SearchQuery.match("comp"))).first().isEmpty())
                upgraded.searchDao().remember(RecentSearchEntity("mili", "Mili", 200))
            } finally {
                upgraded.close()
            }
            val reopened = Room.databaseBuilder(context, HarmonyDatabase::class.java, name).build()
            try {
                assertEquals("Mili", reopened.searchDao().recents().first().single().query)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}

package com.mrgnstr.harmony

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.SongDao
import com.mrgnstr.harmony.core.database.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongDaoTest {
    private lateinit var db: HarmonyDatabase
    private lateinit var dao: SongDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, HarmonyDatabase::class.java).build()
        dao = db.songDao()
    }

    @After fun tearDown() = db.close()

    private fun song(id: Long, title: String = "t$id", modified: Long = 1, genre: String? = null) = SongEntity(
        id, title, "artist", "artist", "album", 1, 1000, 1, 1, 1, modified, genre,
    )

    @Test fun upsertInsertsThenUpdatesInPlace() = runBlocking {
        dao.upsert(listOf(song(1), song(2)))
        dao.upsert(listOf(song(1, title = "renamed", modified = 9, genre = "Pop")))
        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("renamed", all.first { it.id == 1L }.title)
        assertEquals("Pop", all.first { it.id == 1L }.genre)
    }

    @Test fun stampsReflectModifiedTimeAndGenre() = runBlocking {
        dao.upsert(listOf(song(1, modified = 5, genre = "Rock"), song(2)))
        val stamps = dao.stamps().associateBy { it.id }
        assertEquals(5L, stamps.getValue(1).dateModifiedSec)
        assertEquals("Rock", stamps.getValue(1).genre)
        assertNull(stamps.getValue(2).genre)
    }

    @Test fun observeAllSortsCaseInsensitively() = runBlocking {
        dao.upsert(listOf(song(1, "banana"), song(2, "Apple"), song(3, "cherry")))
        assertEquals(listOf("Apple", "banana", "cherry"), dao.observeAll().first().map { it.title })
    }

    @Test fun deleteByIdsRemovesOnlyThose() = runBlocking {
        dao.upsert((1L..10L).map { song(it) })
        dao.deleteByIds(listOf(2, 4, 6))
        assertEquals(listOf(1L, 3L, 5L, 7L, 8L, 9L, 10L), dao.observeAll().first().map { it.id }.sorted())
    }

    @Test fun deleteWorksJustUnderOldSqliteVariableLimit() = runBlocking {
        dao.upsert((1L..900L).map { song(it) })
        dao.deleteByIds((1L..900L).toList())
        assertEquals(0, dao.observeAll().first().size)
    }
}

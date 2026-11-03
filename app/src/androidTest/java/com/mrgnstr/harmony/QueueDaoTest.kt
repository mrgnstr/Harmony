package com.mrgnstr.harmony

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.QueueItemEntity
import com.mrgnstr.harmony.core.database.QueueStateEntity
import com.mrgnstr.harmony.core.database.SongEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueDaoTest {
    private lateinit var db: HarmonyDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HarmonyDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    private fun song(id: Long) = SongEntity(id, "t$id", "a", "a", "alb", 1, 60_000, 1, 1, 1, 1, null)

    @Test fun replaceKeepsOrderDuplicatesAndDropsMissingSongs() = runBlocking {
        val dao = db.queueDao()
        db.songDao().upsert(listOf(song(1), song(2), song(3)))
        dao.replaceItems(QueueItemEntity.LIST_PLAY, listOf(3L, 1L, 99L, 1L, 2L).mapIndexed { i, id -> QueueItemEntity(0, i, id, queued = i == 4) })
        val rows = dao.restoreRows(QueueItemEntity.LIST_PLAY)
        assertEquals(listOf(3L, 1L, 1L, 2L), rows.map { it.song.id })
        assertEquals(listOf(false, false, false, true), rows.map { it.queued })
        // Replacing shrinks the list and leaves the other list alone.
        dao.replaceItems(QueueItemEntity.LIST_UNSHUFFLED, listOf(QueueItemEntity(1, 0, 2, false)))
        dao.replaceItems(QueueItemEntity.LIST_PLAY, listOf(QueueItemEntity(0, 0, 2, false)))
        assertEquals(1, dao.items(QueueItemEntity.LIST_PLAY).size)
        assertEquals(1, dao.items(QueueItemEntity.LIST_UNSHUFFLED).size)
    }

    @Test fun largeListsSurviveChunking() = runBlocking {
        val dao = db.queueDao()
        dao.replaceItems(QueueItemEntity.LIST_PLAY, List(5000) { QueueItemEntity(0, it, it.toLong(), false) })
        assertEquals(5000, dao.items(QueueItemEntity.LIST_PLAY).size)
    }

    @Test fun stateIsASingleUpsertedRow() = runBlocking {
        val dao = db.queueDao()
        assertNull(dao.state())
        dao.saveState(QueueStateEntity(currentIndex = 2, positionMs = 1234, repeatMode = 1, shuffled = true, source = "Songs", updatedAtMs = 1))
        dao.saveState(QueueStateEntity(currentIndex = 3, positionMs = 5, repeatMode = 0, shuffled = false, source = null, updatedAtMs = 2))
        val s = dao.state()!!
        assertEquals(3, s.currentIndex)
        assertNull(s.source)
    }
}

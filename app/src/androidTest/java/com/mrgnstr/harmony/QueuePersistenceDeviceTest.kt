package com.mrgnstr.harmony

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.MIGRATION_5_6
import com.mrgnstr.harmony.core.database.MIGRATION_6_7
import com.mrgnstr.harmony.core.database.MIGRATION_9_10
import com.mrgnstr.harmony.core.database.MIGRATION_10_11
import com.mrgnstr.harmony.core.database.MIGRATION_8_9
import com.mrgnstr.harmony.core.database.MIGRATION_7_8
import com.mrgnstr.harmony.core.database.QueueItemEntity
import com.mrgnstr.harmony.core.mediastore.MediaStoreScanner
import com.mrgnstr.harmony.core.playback.PlayerConnection
import com.mrgnstr.harmony.core.playback.QueueStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real service and reads what it saved into the app's own database. Overwrites the saved queue. */
@RunWith(AndroidJUnit4::class)
class QueuePersistenceDeviceTest {
    private val main = Handler(Looper.getMainLooper())

    private fun onMain(block: () -> Unit) {
        val latch = CountDownLatch(1)
        main.post { try { block() } finally { latch.countDown() } }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
    }

    private fun await(what: String, timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(100) }
        throw AssertionError("timed out waiting for $what")
    }

    @Test fun queueAndModesAreSaved() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val songs = runBlocking { MediaStoreScanner(ctx).scan() }.take(40)
        assertTrue("need at least 6 songs", songs.size >= 6)
        val db = Room.databaseBuilder(ctx, HarmonyDatabase::class.java, "harmony.db").addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build()
        val dao = db.queueDao()
        val startedAt = System.currentTimeMillis()
        // Rows from an earlier run must not satisfy the waits below.
        fun fresh() = runBlocking { dao.state() }?.takeIf { it.updatedAtMs >= startedAt }
        val conn = PlayerConnection(ctx, appQueueStore(ctx))
        try {
            onMain { conn.playQueue(songs, 1, source = "Test source") }
            await("playback") { conn.state.value.isPlaying }
            onMain { conn.skipToNext() }
            await("next") { conn.state.value.mediaId == songs[2].id.toString() }
            onMain { conn.playNext(listOf(songs[5])) }
            await("queued item") { conn.queue.value.items.size == songs.size + 1 }
            // Saves are debounced; wait for the final state to land.
            await("saved state") { fresh()?.currentIndex == 2 && fresh()?.source == "Test source" }
            await("saved queue") { runBlocking { dao.items(QueueItemEntity.LIST_PLAY).size } == songs.size + 1 }
            val play = runBlocking { dao.items(QueueItemEntity.LIST_PLAY) }
            assertEquals(songs.map { it.id } + songs[5].id, play.map { it.songId })
            assertEquals(listOf(false, true), listOf(play.first().queued, play.last().queued))
            assertEquals("Test source", runBlocking { dao.state() }!!.source)

            onMain { conn.cycleRepeat() }
            await("repeat saved") { fresh()?.repeatMode == 2 }
            onMain { conn.toggleShuffle() }
            await("shuffle saved") { fresh()?.shuffled == true }
            assertEquals(songs.size + 1, runBlocking { dao.items(QueueItemEntity.LIST_UNSHUFFLED).size })
            // Left shuffled on purpose: QueueRestoreDeviceTest (a fresh process) restores this and turns it off.
        } finally {
            onMain { if (conn.state.value.isPlaying) conn.togglePlayPause() }
            db.close()
        }
    }
}

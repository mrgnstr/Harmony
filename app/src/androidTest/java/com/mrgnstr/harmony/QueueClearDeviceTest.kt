package com.mrgnstr.harmony

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.MIGRATION_5_6
import com.mrgnstr.harmony.core.database.MIGRATION_9_10
import com.mrgnstr.harmony.core.database.MIGRATION_10_11
import com.mrgnstr.harmony.core.database.MIGRATION_8_9
import com.mrgnstr.harmony.core.database.MIGRATION_7_8
import com.mrgnstr.harmony.core.database.MIGRATION_6_7
import com.mrgnstr.harmony.core.database.QueueItemEntity
import com.mrgnstr.harmony.core.mediastore.MediaStoreScanner
import com.mrgnstr.harmony.core.playback.PlayerConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clearing the queue (the mini player swiped away) must also clear the saved one. Run [clearingForgetsTheSavedQueue] first, then
 * [nothingComesBackAfterARestart] in its own `am instrument` call (a fresh process).
 */
@RunWith(AndroidJUnit4::class)
class QueueClearDeviceTest {
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

    private fun db(ctx: Context) = Room.databaseBuilder(ctx, HarmonyDatabase::class.java, "harmony.db").addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build()

    @Test fun clearingForgetsTheSavedQueue() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val songs = runBlocking { MediaStoreScanner(ctx).scan() }.take(10)
        assertTrue("need at least 3 songs", songs.size >= 3)
        val db = db(ctx)
        val dao = db.queueDao()
        val conn = PlayerConnection(ctx, appQueueStore(ctx))
        try {
            onMain { conn.playQueue(songs, 0, source = "Clear test") }
            await("playback") { conn.state.value.isPlaying }
            await("queue saved") { runBlocking { dao.items(QueueItemEntity.LIST_PLAY).size } == songs.size }
            onMain { conn.clearAll() }
            await("player emptied") { !conn.state.value.hasMedia }
            assertFalse(conn.state.value.isPlaying)
            await("saved queue gone") { runBlocking { dao.items(QueueItemEntity.LIST_PLAY).isEmpty() && dao.state() == null } }
        } finally {
            db.close()
        }
    }

    @Test fun nothingComesBackAfterARestart() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val conn = PlayerConnection(ctx, appQueueStore(ctx))
        onMain { conn.connect() }
        Thread.sleep(3_000)
        assertFalse(conn.state.value.hasMedia)
        assertEquals(0, conn.queue.value.items.size)
        assertNull(conn.queue.value.source)
    }
}

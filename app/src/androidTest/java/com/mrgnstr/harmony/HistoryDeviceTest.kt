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
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.mediastore.MediaStoreScanner
import com.mrgnstr.harmony.core.playback.PlayerConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real service and reads the history it recorded in the app's own database. */
@RunWith(AndroidJUnit4::class)
class HistoryDeviceTest {
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

    @Test fun playsAreLoggedWhenLeftAfterFiveSecondsAndHistoryEntriesPlay() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val songs = runBlocking { MediaStoreScanner(ctx).scan() }.take(40)
        assertTrue("need at least 6 songs", songs.size >= 6)
        val db = Room.databaseBuilder(ctx, HarmonyDatabase::class.java, "harmony.db").addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build()
        val dao = db.historyDao()
        fun latest() = runBlocking { dao.observeLatest(500).first() }
        val conn = PlayerConnection(ctx, appQueueStore(ctx))
        try {
            val before = latest().size
            onMain { conn.playQueue(songs, 1) }
            await("playback") { conn.state.value.isPlaying }
            Thread.sleep(5_500)
            onMain { conn.skipToNext() }
            await("logged after 5 s") { latest().size == before + 1 }
            assertEquals(songs[1].id, latest().first().song.id)

            // Skipped straight away: not a play.
            onMain { conn.skipToNext() }
            await("third song") { conn.state.value.mediaId == songs[3].id.toString() }
            Thread.sleep(1_500)
            assertEquals(before + 1, latest().size)

            // Tapping a history entry plays it now, right after the current song, which carries on after it.
            val entry = latest().first()
            onMain { conn.playFromHistory(entry.song.toModel()) }
            await("history song playing") { conn.state.value.mediaId == songs[1].id.toString() && conn.queue.value.currentIndex == 4 }
            assertEquals(songs[4].id.toString(), conn.queue.value.upcoming.first().mediaId)

            // Removing an entry.
            runBlocking { dao.delete(entry.entryId) }
            assertTrue(latest().none { it.entryId == entry.entryId })
        } finally {
            onMain { if (conn.state.value.isPlaying) conn.togglePlayPause() }
            db.close()
        }
    }
}

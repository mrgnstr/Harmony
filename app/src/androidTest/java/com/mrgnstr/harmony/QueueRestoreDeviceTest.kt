package com.mrgnstr.harmony

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.mediastore.MediaStoreScanner
import com.mrgnstr.harmony.core.playback.PlayerConnection
import com.mrgnstr.harmony.core.playback.RepeatMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run after [QueuePersistenceDeviceTest], in its own `am instrument` call so the process (and the service) start fresh:
 * connecting must bring back the saved queue, paused.
 */
@RunWith(AndroidJUnit4::class)
class QueueRestoreDeviceTest {
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

    @Test fun savedQueueComesBackPausedAndShuffleUndoes() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val songs = runBlocking { MediaStoreScanner(ctx).scan() }.take(40)
        val conn = PlayerConnection(ctx, appQueueStore(ctx))
        onMain { conn.connect() }
        await("restored queue") { conn.queue.value.items.size == songs.size + 1 }
        val q = conn.queue.value
        assertFalse(conn.state.value.isPlaying)
        assertEquals(2, q.currentIndex)
        assertEquals(songs[2].id.toString(), conn.state.value.mediaId)
        assertEquals("Test source", q.source)
        assertEquals(RepeatMode.All, q.repeat)
        assertTrue(q.shuffled)
        assertEquals(songs[5].id, q.items.last().songId)
        assertTrue(q.items.last().queued)

        // The unshuffled order came back too: turning shuffle off restores the original order.
        onMain { conn.toggleShuffle() }
        await("unshuffled") { !conn.queue.value.shuffled && conn.queue.value.items.map { it.songId } == songs.map { it.id } + songs[5].id }
    }
}

package com.mrgnstr.harmony

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.mediastore.MediaStoreScanner
import com.mrgnstr.harmony.core.playback.PlayerConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Plays the device's whole library as one queue: guards against Binder transaction-size limits. */
@RunWith(AndroidJUnit4::class)
class PlaybackDeviceTest {
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

    @Test fun wholeLibraryQueuePlaysAndSkips() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val songs = runBlocking { MediaStoreScanner(ctx).scan() }
        assertTrue("need at least 3 songs", songs.size >= 3)
        val conn = PlayerConnection(ctx, com.mrgnstr.harmony.core.playback.QueueStore())
        val start = 1
        try {
            onMain { conn.playQueue(songs, start) }
            await("playback to start") { conn.state.value.isPlaying }
            assertEquals(songs[start].id.toString(), conn.state.value.mediaId)

            onMain { conn.skipToNext() }
            await("next item") { conn.state.value.mediaId == songs[start + 1].id.toString() }

            onMain { conn.togglePlayPause() }
            await("pause") { !conn.state.value.isPlaying }
        } finally {
            onMain { conn.togglePlayPause().takeIf { conn.state.value.isPlaying } }
        }
    }
}

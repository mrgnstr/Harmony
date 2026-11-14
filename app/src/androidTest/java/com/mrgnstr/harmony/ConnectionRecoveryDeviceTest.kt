package com.mrgnstr.harmony

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.playback.ConnectionRecovery
import com.mrgnstr.harmony.core.playback.HistoryRecorder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real player streaming a WAV from a server on the device that can drop the connection, refuse new ones and come back:
 * the switch to the downloaded file, and the resume when there is none. No real server or library is touched.
 */
@RunWith(AndroidJUnit4::class)
class ConnectionRecoveryDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wav = wavBytes(seconds = 8)
    private val online = MutableStateFlow(true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var server: WavServer
    private lateinit var localFile: File
    private lateinit var player: ExoPlayer
    private var recovery: ConnectionRecovery? = null

    @Before fun setUp() {
        server = WavServer(wav).also { it.start() }
        localFile = File(context.cacheDir, "recovery_test_${System.nanoTime()}.wav").also { it.writeBytes(wav) }
    }

    @After fun tearDown() {
        main { recovery?.release(); if (::player.isInitialized) player.release() }
        scope.cancel()
        server.stop()
        localFile.delete()
    }

    private fun <T> main(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun waitFor(what: String, timeoutMs: Long = 40_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (main(condition)) return
            Thread.sleep(50)
        }
        val dump = main { if (::player.isInitialized) "state=${player.playbackState} pos=${player.currentPosition} pwr=${player.playWhenReady} playing=${player.isPlaying} " +
            "error=${player.playerError?.errorCodeName} uri=${player.currentMediaItem?.localConfiguration?.uri} reqs=${server.requests.get()} online=${online.value}" else "no player" }
        throw AssertionError("timed out waiting for: $what [$dump]")
    }

    private fun item(uri: String) = MediaItem.Builder()
        .setMediaId("-5")
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle("t").setExtras(Bundle().apply { putString("harmony.customUri", uri) }).build())
        .build()

    private fun streamUrl() = "http://127.0.0.1:${server.port}/rest/stream?id=a"

    private fun start(withCopy: Boolean) {
        val local = item("file://${localFile.absolutePath}")
        main {
            player = ExoPlayer.Builder(context).build()
            recovery = ConnectionRecovery(
                player, online, scope,
                localCopy = { id -> if (withCopy && id == -5L) local else null },
                retryDelaysMs = listOf(300, 300, 300, 300, 300, 300, 300, 300),
            ).also { it.start() }
            player.setMediaItem(item(streamUrl()))
            player.prepare()
            player.play()
        }
    }

    private fun scheme() = main { player.currentMediaItem?.localConfiguration?.uri?.scheme }

    @Test fun offlineSwitchesAStalledStreamToItsDownload() {
        server.stallFirstAfterBytes = 40_000
        start(withCopy = true)
        // The connection goes: the first request is cut off and no new one gets through, and the device knows it is offline.
        waitFor("the first request") { server.requests.get() >= 1 }
        waitFor("a second of playing") { player.currentPosition >= 1_000 }
        server.refuse = true
        online.value = false
        waitFor("the switch to the downloaded file") { player.currentMediaItem?.localConfiguration?.uri?.scheme == "file" }
        val position = main { player.currentPosition }
        assertTrue("it went on from where it was, not from the start: $position ms", position >= 1_000)
        waitFor("the end of the song") { player.playbackState == Player.STATE_ENDED }
        assertTrue(main { player.playerError } == null)
    }

    @Test fun aFailedStreamSwitchesToItsDownloadEvenWhenNoOneKnowsWeAreOffline() {
        server.dropFirstAfterBytes = 40_000
        server.refuseAfterFirst = true
        start(withCopy = true)
        waitFor("the first request") { server.requests.get() >= 1 }
        server.refuse = true // online stays true: only the error shows it
        waitFor("the switch to the downloaded file") { player.currentMediaItem?.localConfiguration?.uri?.scheme == "file" }
        assertTrue("went on from where it was", main { player.currentPosition } >= 500)
        waitFor("the end of the song") { player.playbackState == Player.STATE_ENDED }
    }

    @Test fun withoutADownloadItWaitsForTheConnectionAndResumesWhereItStopped() {
        server.dropFirstAfterBytes = 40_000
        server.refuseAfterFirst = true
        start(withCopy = false)
        waitFor("the first request") { server.requests.get() >= 1 }
        server.refuse = true
        online.value = false
        waitFor("the player to give up (an error)") { player.playerError != null }
        val stoppedAt = main { player.currentPosition }
        // Offline: no request is made while it waits.
        val before = server.requests.get()
        Thread.sleep(3_000)
        assertEquals("no request while offline", before, server.requests.get())
        assertTrue(main { player.playerError } != null)
        // Back online: it prepares itself again and plays on from where it was.
        server.refuse = false
        online.value = true
        waitFor("the end of the song") { player.playbackState == Player.STATE_ENDED }
        assertTrue("the new request starts inside the song (${server.lastRangeStart}), not at 0, stopped at $stoppedAt ms", server.lastRangeStart > 0)
        assertEquals("still the stream", "http", scheme())
    }

    @Test fun withoutADownloadItRetriesByItselfWhileOnline() {
        server.dropFirstAfterBytes = 40_000
        server.refuseAfterFirst = true
        start(withCopy = false)
        waitFor("the first request") { server.requests.get() >= 1 }
        server.refuse = true // a bad patch: the player gives up, the device says it is online
        waitFor("the player to give up (an error)") { player.playerError != null }
        server.refuse = false
        waitFor("the end of the song") { player.playbackState == Player.STATE_ENDED }
        assertTrue("resumed inside the song: ${server.lastRangeStart}", server.lastRangeStart > 0)
    }

    private fun plays(db: HarmonyDatabase): Int = runBlocking { db.historyDao().observePlayCounts().first().sumOf { it.count } }

    private fun swapAfterFiveSecondsOfPlaying(db: HarmonyDatabase, muted: Boolean): Pair<Int, Int> {
        // 100 kB is about 6 s of this audio: it has played past the 5 s a play needs when the connection goes.
        server.stallFirstAfterBytes = 100_000
        start(withCopy = true)
        main { HistoryRecorder(player, db.historyDao(), CoroutineScope(Dispatchers.IO), isMuted = { muted && recovery!!.swapping }) }
        waitFor("5.5 s of playing") { player.currentPosition >= 5_500 }
        server.refuse = true
        online.value = false
        waitFor("the switch to the downloaded file") { player.currentMediaItem?.localConfiguration?.uri?.scheme == "file" }
        Thread.sleep(1_000)
        val atSwap = plays(db)
        waitFor("the end of the song") { player.playbackState == Player.STATE_ENDED }
        Thread.sleep(1_000)
        return atSwap to plays(db)
    }

    @Test fun swappingTheSongThatIsPlayingIsNotCountedAsAPlay() {
        val db = Room.inMemoryDatabaseBuilder(context, HarmonyDatabase::class.java).build()
        try {
            val (atSwap, atEnd) = swapAfterFiveSecondsOfPlaying(db, muted = true)
            assertEquals("the swap is not a play", 0, atSwap)
            assertEquals("one play when it ends", 1, atEnd)
        } finally { db.close() }
    }

    @Test fun withoutTheMuteTheSwapWouldHaveBeenCountedAsAPlay() {
        val db = Room.inMemoryDatabaseBuilder(context, HarmonyDatabase::class.java).build()
        try {
            val (atSwap, atEnd) = swapAfterFiveSecondsOfPlaying(db, muted = false)
            assertEquals("why the history recorder is muted during a swap", 1, atSwap)
            assertEquals("and the end would count it again", 2, atEnd)
        } finally { db.close() }
    }

    @Test fun aPausedPlayerIsNeverStartedByAReconnect() {
        server.dropFirstAfterBytes = 40_000
        server.refuseAfterFirst = true
        start(withCopy = false)
        waitFor("a second of playing") { player.currentPosition >= 1_000 }
        main { player.pause() }
        online.value = false
        Thread.sleep(2_000)
        server.refuse = false
        online.value = true
        // ExoPlayer may go on loading by itself while paused (that is not this class); what must not happen is playing.
        Thread.sleep(4_000)
        assertTrue("the user paused: it stays paused", main { !player.playWhenReady && !player.isPlaying && player.playbackState != Player.STATE_ENDED })
    }

    /** Loopback HTTP server for one WAV: Range, Content-Length, a request counter, one cut-off request, and a switch that refuses connections. */
    private class WavServer(private val body: ByteArray) {
        private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val port get() = socket.localPort
        val requests = AtomicInteger()
        @Volatile var refuse = false
        /** The connection is gone for good after the first request (until a test lets it back): the player's own quick retry must not get through. */
        @Volatile var refuseAfterFirst = false
        @Volatile var dropFirstAfterBytes = -1
        /** Like losing the signal: the first request sends this many bytes and then goes quiet, with the connection still open. */
        @Volatile var stallFirstAfterBytes = -1
        @Volatile var lastRangeStart = 0
        @Volatile private var running = true
        private val first = AtomicInteger()
        private val thread = Thread {
            while (running) {
                val client = try { socket.accept() } catch (e: IOException) { break }
                if (refuse) { runCatching { client.close() }; continue }
                if (requests.incrementAndGet() == 1 && refuseAfterFirst) refuse = true
                Thread { runCatching { handle(client) } }.start()
            }
        }

        fun start() { thread.start() }
        fun stop() { running = false; runCatching { socket.close() } }

        private fun handle(client: Socket) {
            client.use {
            val input = it.getInputStream().bufferedReader()
            input.readLine() ?: return
            var from = 0
            var ranged = false
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    from = line.substringAfter("bytes=").substringBefore('-').toInt()
                    ranged = true
                }
            }
            lastRangeStart = from
            val payload = body.copyOfRange(from, body.size)
            val head = (if (ranged) "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $from-${body.size - 1}/${body.size}\r\n" else "HTTP/1.1 200 OK\r\n") +
                "Content-Type: audio/wav\r\nAccept-Ranges: bytes\r\nConnection: close\r\nContent-Length: ${payload.size}\r\n\r\n"
            val out = it.getOutputStream()
            out.write(head.toByteArray())
            val isFirst = first.getAndIncrement() == 0
            val stall = stallFirstAfterBytes.takeIf { limit -> limit >= 0 && isFirst }
            if (stall != null) {
                runCatching { out.write(payload, 0, stall.coerceAtMost(payload.size)); out.flush() }
                while (running && !it.isClosed) Thread.sleep(100)
                return@use
            }
            val cut = dropFirstAfterBytes.takeIf { limit -> limit >= 0 && isFirst }
            // The cut-off request ends early: the connection closes with bytes still owed.
            runCatching { out.write(payload, 0, cut?.coerceAtMost(payload.size) ?: payload.size); out.flush() }
            }
        }
    }

    private companion object {
        /** 16-bit mono PCM at 8 kHz: a valid, seekable WAV of a sine tone. */
        fun wavBytes(seconds: Int): ByteArray {
            val rate = 8000
            val samples = rate * seconds
            val data = ByteArray(samples * 2)
            for (i in 0 until samples) {
                val v = (sin(2 * PI * 440 * i / rate) * 12000).toInt()
                data[i * 2] = v.toByte(); data[i * 2 + 1] = (v shr 8).toByte()
            }
            fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
            fun le16(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte())
            return "RIFF".toByteArray() + le(36 + data.size) + "WAVEfmt ".toByteArray() + le(16) + le16(1) + le16(1) + le(rate) + le(rate * 2) + le16(2) + le16(16) +
                "data".toByteArray() + le(data.size) + data
        }
    }
}

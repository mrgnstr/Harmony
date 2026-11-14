package com.mrgnstr.harmony

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.playback.StreamCache
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The stream cache with a real HTTP server on the device (its own throwaway cache folder; the app's stream cache and every real server
 * are left alone): what was streamed is read back with the server gone, and streams without a length are not kept.
 */
@RunWith(AndroidJUnit4::class)
class StreamCacheDeviceTest {
    private lateinit var dir: File
    private lateinit var cache: SimpleCache
    private lateinit var server: TestServer
    private val body = ByteArray(300_000) { (it * 31 + 7).toByte() }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dir = File(context.cacheDir, "stream_cache_test_${System.nanoTime()}")
        cache = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(64L shl 20), StandaloneDatabaseProvider(context))
        server = TestServer(body).also { it.start() }
    }

    @After fun tearDown() {
        server.stop()
        cache.release()
        dir.deleteRecursively()
    }

    private fun source(): DataSource = StreamCache.dataSourceFactory(cache).createDataSource()

    /** Reads [length] bytes (or all) of [url] from [position], the way the service asks (flagged, as its resolver does). */
    private fun read(url: String, position: Long = 0, length: Long = androidx.media3.common.C.LENGTH_UNSET.toLong()): ByteArray {
        val src = source()
        val spec = StreamCache.skipIfLengthUnknown(DataSpec.Builder().setUri(url.toUri()).setPosition(position).setLength(length).build())
        try {
            src.open(spec)
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = src.read(buf, 0, buf.size)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            src.close()
        }
    }

    private fun url(auth: String, query: String = "") = "http://127.0.0.1:${server.port}/rest/stream?id=song1&$auth$query"

    @Test fun aStreamedSongIsReadBackFromDiskWithTheServerGone() {
        assertArrayEquals(body, read(url("u=me&t=aaa&s=111")))
        assertEquals(1, server.requests.get())
        server.stop()
        // Another sync has made other credentials for the same song: still the same cache entry.
        assertArrayEquals(body, read(url("u=me&t=bbb&s=222")))
        // And a seek inside it, as the player does.
        assertArrayEquals(body.copyOfRange(120_000, 150_000), read(url("u=me&t=ccc&s=333"), 120_000, 30_000))
    }

    @Test fun aDifferentQualityIsAnotherEntry() {
        read(url("u=me&t=aaa&s=111"))
        server.stop()
        try {
            read(url("u=me&t=aaa&s=111", "&maxBitRate=128&format=opus"))
            fail("a 128 kbps stream must not be answered by the original's cache entry")
        } catch (expected: IOException) {
        }
    }

    @Test fun aPartlyReadSongKeepsWhatWasRead() {
        // The connection drops after the first 100 kB: the player gets an error, but those bytes stay for the retry.
        server.dropAfterBytes = 100_000
        try {
            read(url("u=me&t=aaa&s=111"))
        } catch (expected: IOException) {
        }
        server.dropAfterBytes = -1
        server.stop()
        assertArrayEquals(body.copyOfRange(0, 90_000), read(url("u=me&t=aaa&s=111"), 0, 90_000))
    }

    @Test fun aStreamWithoutALengthIsNotKept() {
        server.chunked = true
        assertArrayEquals(body, read(url("u=me&t=aaa&s=111")))
        server.stop()
        try {
            read(url("u=me&t=aaa&s=111"))
            fail("a stream of unknown length must not come from the cache")
        } catch (expected: IOException) {
        }
        assertTrue(cache.keys.isEmpty() || cache.cacheSpace == 0L)
    }

    /** Minimal HTTP/1.1 server on loopback: Range support, Content-Length (or none), a request counter, and an optional dropped connection. */
    private class TestServer(private val body: ByteArray) {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port get() = socket.localPort
        val requests = AtomicInteger()
        @Volatile var chunked = false
        @Volatile var dropAfterBytes = -1
        @Volatile private var running = true
        private val thread = Thread {
            while (running) {
                val client = try { socket.accept() } catch (e: IOException) { break }
                requests.incrementAndGet()
                Thread { handle(client) }.start()
            }
        }

        fun start() { thread.start() }
        fun stop() { running = false; runCatching { socket.close() } }

        private fun handle(client: java.net.Socket) {
            client.use {
            val input = it.getInputStream().bufferedReader()
            input.readLine() ?: return
            var range: Pair<Int, Int?>? = null
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    val r = line.substringAfter("bytes=").split('-')
                    range = r[0].toInt() to r.getOrNull(1)?.takeIf { s -> s.isNotEmpty() }?.toInt()
                }
            }
            val out = it.getOutputStream()
            val from = range?.first ?: 0
            val to = (range?.second ?: (body.size - 1)).coerceAtMost(body.size - 1)
            val payload = body.copyOfRange(from, to + 1)
            val head = StringBuilder()
            if (range != null && !chunked) head.append("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $from-$to/${body.size}\r\n")
            else head.append("HTTP/1.1 200 OK\r\n")
            head.append("Content-Type: audio/flac\r\nAccept-Ranges: ${if (chunked) "none" else "bytes"}\r\nConnection: close\r\n")
            if (!chunked) head.append("Content-Length: ${payload.size}\r\n")
            head.append("\r\n")
            out.write(head.toString().toByteArray())
            val limit = if (dropAfterBytes >= 0) dropAfterBytes.coerceAtMost(payload.size) else payload.size
            val src = if (chunked && range != null) payload.copyOfRange(0, payload.size) else payload
            runCatching { out.write(src, 0, limit); out.flush() }
            // Dropped: close without finishing a response that promised more bytes.
            }
        }
    }
}

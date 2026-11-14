package com.mrgnstr.harmony.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamCacheTest {
    private val base = "https://music.example.org/rest/stream"

    @Test fun credentialsAreNotPartOfTheKey() {
        val a = StreamCache.keyOf("$base?u=me&t=aaa&s=111&v=1.16.1&c=Harmony&id=abc")
        val b = StreamCache.keyOf("$base?u=me&t=bbb&s=222&v=1.16.1&c=Harmony&id=abc")
        assertEquals(a, b)
    }

    @Test fun qualityServerAndSongSeparateEntries() {
        val plain = StreamCache.keyOf("$base?id=abc&u=me")
        assertNotEquals(plain, StreamCache.keyOf("$base?id=abc&u=me&maxBitRate=128&format=opus"))
        assertNotEquals(StreamCache.keyOf("$base?id=abc&maxBitRate=128&format=opus"), StreamCache.keyOf("$base?id=abc&maxBitRate=320&format=opus"))
        assertNotEquals(plain, StreamCache.keyOf("$base?id=abd&u=me"))
        assertNotEquals(plain, StreamCache.keyOf("https://other.example.org/rest/stream?id=abc&u=me"))
        assertNotEquals(plain, StreamCache.keyOf("https://music.example.org:4533/rest/stream?id=abc&u=me"))
    }

    @Test fun idsWithEscapesAreDecoded() {
        assertEquals(
            StreamCache.keyOf("$base?id=a%2Fb&u=me"),
            StreamCache.keyOf("$base?u=me&id=a/b"),
        )
    }

    @Test fun otherUrlsHaveNoKey() {
        assertNull(StreamCache.keyOf("https://radio.example.org/live.mp3"))
        assertNull(StreamCache.keyOf("https://music.example.org/rest/getCoverArt?id=abc"))
        assertNull(StreamCache.keyOf("$base?u=me")) // no song id
        assertNull(StreamCache.keyOf("not a url"))
    }
}

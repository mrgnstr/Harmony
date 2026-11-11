package com.mrgnstr.harmony.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeModelsTest {
    @Test
    fun remoteIdsAreDeterministicAndNegativeAndCollisionFree() {
        val s1 = RemoteIds.songId(1L, "63sqASlAfjbGMuLP4JhnZU")
        val s2 = RemoteIds.songId(1L, "63sqASlAfjbGMuLP4JhnZU")
        val sOtherServer = RemoteIds.songId(2L, "63sqASlAfjbGMuLP4JhnZU")
        val a1 = RemoteIds.albumId(1L, "63sqASlAfjbGMuLP4JhnZU")

        assertTrue(s1 < 0L)
        assertTrue(a1 < 0L)
        assertEquals(s1, s2)
        assertNotEquals(s1, sOtherServer)
        assertNotEquals(s1, a1)
    }

    @Test
    fun replayGainComputesLinearMultiplierAndPreventsClipping() {
        val rg = ReplayGainInfo(
            trackGainDb = -6.0,
            albumGainDb = -3.0,
            trackPeak = 1.0,
            albumPeak = 1.0,
        )
        assertEquals(1.0f, rg.computeGainFactor(ReplayGainMode.Off, isAlbumSource = true), 0.001f)
        // -6 dB is ~0.501187
        assertEquals(0.501f, rg.computeGainFactor(ReplayGainMode.Track, isAlbumSource = true), 0.01f)
        // -3 dB is ~0.707945
        assertEquals(0.708f, rg.computeGainFactor(ReplayGainMode.Album, isAlbumSource = false), 0.01f)
        assertEquals(0.708f, rg.computeGainFactor(ReplayGainMode.Smart, isAlbumSource = true), 0.01f)
        assertEquals(0.501f, rg.computeGainFactor(ReplayGainMode.Smart, isAlbumSource = false), 0.01f)

        // Positive gain with peak=0.8 must clamp to 1 / 0.8 = 1.25 when preventClipping=true
        val loud = ReplayGainInfo(trackGainDb = 12.0, trackPeak = 0.8)
        assertEquals(1.25f, loud.computeGainFactor(ReplayGainMode.Track, isAlbumSource = false, preventClipping = true), 0.01f)
    }

    @Test
    fun serverUrlCandidatesTryHttpsFirstForPublicHosts() {
        assertEquals(listOf("https://music.example.com", "http://music.example.com"), serverUrlCandidates("music.example.com"))
        assertEquals(
            listOf("https://music.example.com/navidrome", "http://music.example.com/navidrome"),
            serverUrlCandidates("  music.example.com/navidrome/ "),
        )
    }

    @Test
    fun serverUrlCandidatesTryHttpFirstForLanHosts() {
        assertEquals(listOf("http://192.168.1.10:4533", "https://192.168.1.10:4533"), serverUrlCandidates("192.168.1.10:4533"))
        assertEquals("http://nas:4533", serverUrlCandidates("nas:4533").first())
        assertEquals("http://localhost:4533", serverUrlCandidates("localhost:4533").first())
        assertEquals("https://8.8.8.8:4533", serverUrlCandidates("8.8.8.8:4533").first())
    }

    @Test
    fun serverUrlCandidatesRespectAnExplicitScheme() {
        assertEquals(listOf("https://Music.Example.com"), serverUrlCandidates(" HTTPS://Music.Example.com/ "))
        assertEquals(listOf("http://x:4533"), serverUrlCandidates("http://x:4533"))
        assertEquals(emptyList<String>(), serverUrlCandidates("   "))
    }

    @Test
    fun normalizeServerUrlLeavesSavedUrlsAlone() {
        assertEquals("https://music.example.com", normalizeServerUrl("https://music.example.com/"))
        assertEquals("http://192.168.1.10:4533", normalizeServerUrl("192.168.1.10:4533"))
    }
}

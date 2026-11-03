package com.mrgnstr.harmony.core.model

import com.mrgnstr.harmony.core.mediastore.HeaderParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFormatTest {
    private fun flac(rate: Int, bits: Int) = AudioFormat("FLAC", true, rate, bits)

    @Test fun hiResIsStrictlyAbove24Bit48k() {
        assertEquals(false, flac(44_100, 16).hiRes)
        assertEquals(false, flac(48_000, 24).hiRes)
        assertEquals(true, flac(96_000, 24).hiRes)
        assertEquals(true, flac(48_000, 32).hiRes)
        assertEquals(false, AudioFormat("AAC", false, 96_000, 0).hiRes)
    }

    @Test fun labels() {
        assertEquals(FormatSummary(false, "Lossless FLAC", true), AudioFormat.summarize(listOf(flac(44_100, 16), flac(44_100, 16))))
        assertEquals(FormatSummary(false, "Hi-Res Lossless FLAC", true), AudioFormat.summarize(listOf(flac(96_000, 24), flac(96_000, 24), flac(44_100, 16))))
        assertEquals(FormatSummary(false, "AAC", false), AudioFormat.summarize(listOf(AudioFormat("AAC", false, 44_100, 0))))
        assertEquals(FormatSummary(true, null, false), AudioFormat.summarize(listOf(AudioFormat(AudioFormat.codecName("audio/eac3-joc"), false, 48_000, 0))))
        assertEquals(FormatSummary(false, "Lossless ALAC", true), AudioFormat.summarize(listOf(AudioFormat("ALAC", true, 44_100, 16))))
        assertNull(AudioFormat.summarize(emptyList()))
    }

    @Test fun codecNames() {
        assertEquals("FLAC", AudioFormat.codecName("audio/flac"))
        assertEquals("PCM", AudioFormat.codecName("audio/raw"))
        assertEquals("MP3", AudioFormat.codecName("audio/mpeg"))
        assertEquals("Opus", AudioFormat.codecName("audio/opus"))
        assertEquals("Dolby Atmos", AudioFormat.codecName("audio/eac3"))
        assertEquals("Dolby Atmos", AudioFormat.codecName("audio/eac3-joc"))
    }

    @Test fun flacStreamInfo() {
        // 96 kHz (0x17700), stereo, 24 bit (stored as 23).
        val b = ByteArray(42)
        "fLaC".toByteArray().copyInto(b)
        b[18] = 0x17; b[19] = 0x70; b[20] = 0x03; b[21] = 0x70
        assertEquals(96_000 to 24, HeaderParser.flac(b))
        assertNull(HeaderParser.flac(ByteArray(42)))
    }
}

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

    private fun texts(vararg f: AudioFormat) = AudioFormat.tags(f.toList()).map { it.text }

    @Test fun tags() {
        assertEquals(listOf("Lossless FLAC"), texts(flac(44_100, 16), flac(44_100, 16)))
        assertEquals(listOf("Hi-Res Lossless FLAC"), texts(flac(96_000, 24), flac(96_000, 24), flac(44_100, 16)))
        assertEquals(listOf("AAC"), texts(AudioFormat("AAC", false, 44_100, 0)))
        assertEquals(listOf("Lossless ALAC"), texts(AudioFormat("ALAC", true, 44_100, 16)))
        assertEquals(listOf("Lossless WAV"), texts(AudioFormat("WAV", true, 44_100, 16)))
        assertEquals(emptyList<String>(), texts())
    }

    @Test fun multichannelTagsLeadWithTheLayout() {
        assertEquals(listOf("5.1 FLAC"), texts(AudioFormat("FLAC", true, 48_000, 24, channels = 6)))
        assertEquals(listOf("7.1 E-AC-3"), texts(AudioFormat("E-AC-3", false, 48_000, 0, channels = 8)))
        assertEquals(listOf("5.1 AAC"), texts(AudioFormat("AAC", false, 48_000, 0, channels = 6)))
    }

    @Test fun immersiveLayoutsFromTheChannelCount() {
        assertEquals(listOf("7.1.4 Opus"), texts(AudioFormat("Opus", false, 48_000, 0, channels = 12)))
        assertEquals("7.1", AudioFormat.channelLayout(8))
        assertEquals("7.1.2", AudioFormat.channelLayout(10))
        assertEquals("9.1.6", AudioFormat.channelLayout(16))
        assertEquals("9ch", AudioFormat.channelLayout(9))
        assertNull(AudioFormat.channelLayout(2))
    }

    @Test fun atmosIsJustDolbyAtmos() {
        val atmos = AudioFormat(AudioFormat.codecName("audio/eac3-joc"), false, 48_000, 0, channels = 6)
        assertEquals(listOf("Dolby Atmos"), texts(atmos, atmos.copy(channels = 8)))
    }

    @Test fun mixedAlbumListsEachFormatMostCommonFirst() {
        val mp3 = AudioFormat("MP3", false, 44_100, 0)
        assertEquals(listOf("MP3", "Lossless FLAC"), texts(mp3, mp3, flac(44_100, 16)))
    }

    @Test fun tagIcons() {
        fun icon(f: AudioFormat) = AudioFormat.tags(listOf(f)).single().icon
        assertEquals(FormatIcon.Lossless, icon(flac(44_100, 16)))
        assertEquals(FormatIcon.Lossy, icon(AudioFormat("MP3", false, 44_100, 0)))
        assertEquals(FormatIcon.Lossy, icon(AudioFormat("Opus", false, 48_000, 0)))
        assertEquals(FormatIcon.Surround, icon(AudioFormat("FLAC", true, 48_000, 24, channels = 6)))
        assertEquals(FormatIcon.Dolby, icon(AudioFormat("E-AC-3", false, 48_000, 0, channels = 6)))
        assertEquals(FormatIcon.Dolby, icon(AudioFormat(AudioFormat.DOLBY_ATMOS, false, 48_000, 0, channels = 8)))
    }

    @Test fun eac3Joc() {
        // dec3 with one independent substream and no extension: plain E-AC-3. With the 2-byte extension: JOC.
        fun dec3(vararg payload: Int) = byteArrayOf(0, 0, 0, (8 + payload.size).toByte()) + "dec3".toByteArray() + payload.map { it.toByte() }.toByteArray()
        val plain = dec3(0x06, 0x00, 0x10, 0x3E, 0x00)
        val joc = dec3(0x06, 0x00, 0x10, 0x3E, 0x00, 0x01, 0x10)
        assertEquals(false, HeaderParser.eac3Joc(plain))
        assertEquals(true, HeaderParser.eac3Joc(joc))
        assertNull(HeaderParser.eac3Joc(ByteArray(64)))
    }

    @Test fun codecNames() {
        assertEquals("FLAC", AudioFormat.codecName("audio/flac"))
        assertEquals("PCM", AudioFormat.codecName("audio/raw"))
        assertEquals("MP3", AudioFormat.codecName("audio/mpeg"))
        assertEquals("Opus", AudioFormat.codecName("audio/opus"))
        assertEquals("E-AC-3", AudioFormat.codecName("audio/eac3"))
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

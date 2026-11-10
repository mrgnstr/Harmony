package com.mrgnstr.harmony.core.model

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tag readers on small hand-built files: only the structure that matters is there, no audio. */
class EmbeddedLyricsTest {
    private val lrc = "[00:01.00]Hello\n[00:03.50]World"

    private fun read(bytes: ByteArray) = EmbeddedLyrics.read(BytesSource(bytes))

    private fun assertLrc(l: Lyrics?) {
        assertTrue(l != null)
        assertTrue(l!!.synced)
        assertEquals(listOf("Hello", "World"), l.lines.map { it.text })
        assertEquals(listOf(1000L, 3500L), l.lines.map { it.startMs })
    }

    // ---- helpers ----

    private fun be32(n: Int) = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
    private fun le32(n: Int) = byteArrayOf(n.toByte(), (n ushr 8).toByte(), (n ushr 16).toByte(), (n ushr 24).toByte())
    private fun synchsafe(n: Int) = byteArrayOf(((n ushr 21) and 0x7F).toByte(), ((n ushr 14) and 0x7F).toByte(), ((n ushr 7) and 0x7F).toByte(), (n and 0x7F).toByte())
    private fun bytes(vararg parts: ByteArray) = ByteArrayOutputStream().also { o -> parts.forEach { o.write(it) } }.toByteArray()

    private fun id3(major: Int, frames: ByteArray, flags: Int = 0) =
        bytes("ID3".toByteArray(), byteArrayOf(major.toByte(), 0, flags.toByte()), synchsafe(frames.size), frames, ByteArray(64) { 0x55 })

    private fun frame(major: Int, id: String, data: ByteArray) =
        bytes(id.toByteArray(), if (major == 4) synchsafe(data.size) else be32(data.size), byteArrayOf(0, 0), data)

    private fun uslt(enc: Int, text: ByteArray, terminator: ByteArray) =
        bytes(byteArrayOf(enc.toByte()), "eng".toByteArray(), terminator, text)

    // ---- ID3 ----

    @Test fun id3v23UsltLatin1HoldingAnLrc() {
        val data = uslt(0, lrc.toByteArray(Charsets.ISO_8859_1), byteArrayOf(0))
        assertLrc(read(id3(3, frame(3, "USLT", data))))
    }

    @Test fun id3v23UsltUtf16WithBomAndDescriptor() {
        val desc = bytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), "d".toByteArray(Charsets.UTF_16LE), byteArrayOf(0, 0))
        val text = bytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), lrc.toByteArray(Charsets.UTF_16LE))
        val data = bytes(byteArrayOf(1), "eng".toByteArray(), desc, text)
        assertLrc(read(id3(3, frame(3, "USLT", data))))
    }

    @Test fun plainUsltIsUnsyncedLines() {
        val l = read(id3(3, frame(3, "USLT", uslt(0, "one\ntwo\nthree".toByteArray(), byteArrayOf(0)))))!!
        assertFalse(l.synced)
        assertEquals(listOf("one", "two", "three"), l.lines.map { it.text })
    }

    @Test fun id3v24SyltInMillisecondsBecomesSyncedLines() {
        fun entry(text: String, ms: Int) = bytes(text.toByteArray(Charsets.UTF_8), byteArrayOf(0), be32(ms))
        val data = bytes(byteArrayOf(3), "eng".toByteArray(), byteArrayOf(2, 1), byteArrayOf(0), entry("\nHello", 1000), entry("\nWorld", 3500))
        assertLrc(read(id3(4, frame(4, "SYLT", data))))
    }

    @Test fun syltSyllablesAreJoinedIntoTheirLine() {
        fun entry(text: String, ms: Int) = bytes(text.toByteArray(Charsets.UTF_8), byteArrayOf(0), be32(ms))
        val data = bytes(byteArrayOf(3), "eng".toByteArray(), byteArrayOf(2, 1), byteArrayOf(0),
            entry("\nHel", 1000), entry("lo", 1400), entry("\nWor", 3500), entry("ld", 3900))
        assertLrc(read(id3(4, frame(4, "SYLT", data))))
    }

    @Test fun syncedBeatsPlainWhenBothArePresent() {
        val plain = frame(4, "USLT", uslt(3, "just text".toByteArray(), byteArrayOf(0)))
        fun entry(text: String, ms: Int) = bytes(text.toByteArray(), byteArrayOf(0), be32(ms))
        val sylt = frame(4, "SYLT", bytes(byteArrayOf(3), "eng".toByteArray(), byteArrayOf(2, 1), byteArrayOf(0), entry("\nHello", 1000), entry("\nWorld", 3500)))
        assertLrc(read(id3(4, bytes(plain, sylt))))
    }

    @Test fun txxxLyricsIsRead() {
        val data = bytes(byteArrayOf(0), "LYRICS".toByteArray(), byteArrayOf(0), lrc.toByteArray())
        assertLrc(read(id3(3, frame(3, "TXXX", data))))
    }

    @Test fun otherTxxxIsIgnored() {
        val data = bytes(byteArrayOf(0), "MOOD".toByteArray(), byteArrayOf(0), lrc.toByteArray())
        assertNull(read(id3(3, frame(3, "TXXX", data))))
    }

    @Test fun framesBeforeTheLyricsAreSkipped() {
        val picture = frame(3, "APIC", ByteArray(300) { 7 })
        val title = frame(3, "TIT2", bytes(byteArrayOf(0), "Song".toByteArray()))
        val data = uslt(0, lrc.toByteArray(), byteArrayOf(0))
        assertLrc(read(id3(3, bytes(title, picture, frame(3, "USLT", data)))))
    }

    @Test fun ttmlInATagIsParsedAsTtml() {
        val ttml = """<?xml version="1.0"?><tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="0:01.000" end="0:02.000">Hello</p><p begin="0:03.000" end="0:04.000">World</p></div></body></tt>"""
        val l = read(id3(3, frame(3, "USLT", uslt(0, ttml.toByteArray(), byteArrayOf(0)))))!!
        assertTrue(l.synced)
        assertEquals(listOf("Hello", "World"), l.lines.map { it.text })
    }

    // ---- FLAC ----

    private fun vorbisComments(vararg entries: String): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "test".toByteArray()
        out.write(le32(vendor.size)); out.write(vendor); out.write(le32(entries.size))
        for (e in entries) { val b = e.toByteArray(); out.write(le32(b.size)); out.write(b) }
        return out.toByteArray()
    }

    private fun flacBlock(type: Int, last: Boolean, body: ByteArray) =
        bytes(byteArrayOf((type or if (last) 0x80 else 0).toByte(), (body.size ushr 16).toByte(), (body.size ushr 8).toByte(), body.size.toByte()), body)

    @Test fun flacVorbisCommentLyrics() {
        val file = bytes("fLaC".toByteArray(), flacBlock(0, false, ByteArray(34)), flacBlock(6, false, ByteArray(500)),
            flacBlock(4, true, vorbisComments("TITLE=Song", "lyrics=$lrc")), ByteArray(100))
        assertLrc(read(file))
    }

    @Test fun flacUnsyncedLyricsKey() {
        val file = bytes("fLaC".toByteArray(), flacBlock(4, true, vorbisComments("UNSYNCEDLYRICS=a\nb")))
        val l = read(file)!!
        assertFalse(l.synced)
        assertEquals(listOf("a", "b"), l.lines.map { it.text })
    }

    @Test fun flacWithoutLyricsIsNull() {
        assertNull(read(bytes("fLaC".toByteArray(), flacBlock(4, true, vorbisComments("TITLE=Song")))))
    }

    @Test fun flacBehindAnId3TagIsFound() {
        val tag = id3(3, frame(3, "TIT2", bytes(byteArrayOf(0), "x".toByteArray())))
        // id3() pads with 64 bytes outside the declared size, so the declared end is where fLaC has to start.
        val declared = 10 + (tag.size - 10 - 64)
        val file = bytes(tag.copyOfRange(0, declared), "fLaC".toByteArray(), flacBlock(4, true, vorbisComments("LYRICS=$lrc")))
        assertLrc(read(file))
    }

    // ---- Ogg ----

    private fun oggPage(sequence: Int, packets: List<ByteArray>, continued: Boolean = false): ByteArray {
        val lacing = ByteArrayOutputStream()
        for ((i, p) in packets.withIndex()) {
            var left = p.size
            while (left >= 255) { lacing.write(255); left -= 255 }
            // A packet that goes on in the next page ends in a 255 and has no short closing segment.
            if (!(continued && i == packets.lastIndex && p.size % 255 == 0)) lacing.write(left)
        }
        val body = bytes(*packets.toTypedArray())
        return bytes("OggS".toByteArray(), byteArrayOf(0, if (sequence == 0) 2 else 0), ByteArray(8), le32(1), le32(sequence), ByteArray(4),
            byteArrayOf(lacing.size().toByte()), lacing.toByteArray(), body)
    }

    @Test fun opusTagsLyrics() {
        val head = "OpusHead".toByteArray() + ByteArray(11)
        val tags = "OpusTags".toByteArray() + vorbisComments("LYRICS=$lrc")
        assertLrc(read(bytes(oggPage(0, listOf(head)), oggPage(1, listOf(tags)))))
    }

    @Test fun vorbisCommentHeaderOverSeveralSegments() {
        val long = (1..60).joinToString("\n") { "[%02d:%02d.00]line number $it".format(it / 60, it % 60) }
        val ident = byteArrayOf(1) + "vorbis".toByteArray() + ByteArray(23)
        val comment = byteArrayOf(3) + "vorbis".toByteArray() + vorbisComments("LYRICS=$long") + byteArrayOf(1)
        assertTrue(comment.size > 600)
        val l = read(bytes(oggPage(0, listOf(ident)), oggPage(1, listOf(comment))))!!
        assertEquals(60, l.lines.size)
        assertEquals("line number 60", l.lines.last().text)
    }

    // ---- MP4 ----

    private fun box(type: ByteArray, vararg children: ByteArray): ByteArray {
        val body = bytes(*children)
        return bytes(be32(8 + body.size), type, body)
    }

    private fun box(type: String, vararg children: ByteArray) = box(type.toByteArray(Charsets.ISO_8859_1), *children)

    @Test fun mp4LyricsAtom() {
        val lyr = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val data = box("data", be32(1), ByteArray(4), lrc.toByteArray())
        val ilst = box("ilst", box("©nam".toByteArray(Charsets.ISO_8859_1), box("data", be32(1), ByteArray(4), "Song".toByteArray())), box(lyr, data))
        val moov = box("moov", box("mvhd", ByteArray(100)), box("udta", box("meta", ByteArray(4), box("hdlr", ByteArray(20)), ilst)))
        val file = bytes(box("ftyp", "M4A ".toByteArray(), ByteArray(4)), box("mdat", ByteArray(1000)), moov)
        assertLrc(read(file))
    }

    @Test fun mp4WithoutLyricsIsNull() {
        val ilst = box("ilst", box("©nam".toByteArray(Charsets.ISO_8859_1), box("data", be32(1), ByteArray(4), "Song".toByteArray())))
        val moov = box("moov", box("udta", box("meta", ByteArray(4), ilst)))
        assertNull(read(bytes(box("ftyp", "M4A ".toByteArray(), ByteArray(4)), moov)))
    }

    // ---- junk ----

    @Test fun unknownAndTruncatedDataIsNull() {
        assertNull(read(ByteArray(0)))
        assertNull(read(ByteArray(200) { 3 }))
        assertNull(read("ID3".toByteArray() + byteArrayOf(3, 0, 0, 0, 0, 0, 100)))
        assertNull(read("fLaC".toByteArray() + byteArrayOf(4, 0, 0xFF.toByte(), 0xFF.toByte())))
        assertNull(read("OggS".toByteArray() + ByteArray(30)))
    }
}

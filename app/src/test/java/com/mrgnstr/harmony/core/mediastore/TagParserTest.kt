package com.mrgnstr.harmony.core.mediastore

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagParserTest {
    private fun be32(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun le32(n: Int) = be32(n).reversedArray()
    private fun synchsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())
    private fun bytes(vararg parts: ByteArray) = ByteArrayOutputStream().also { o -> parts.forEach(o::write) }.toByteArray()
    private fun credits(b: ByteArray) = TagParser.credits(BytesSource(b))

    private fun id3Frame(id: String, text: String): ByteArray {
        val body = bytes(byteArrayOf(3), text.toByteArray())
        return bytes(id.toByteArray(), synchsafe(body.size), byteArrayOf(0, 0), body)
    }

    @Test fun id3v24() {
        val frames = bytes(id3Frame("TIT2", "Song"), id3Frame("TPUB", "Some Label"), id3Frame("TCOP", "© 2024 Some Label"))
        val tag = bytes("ID3".toByteArray(), byteArrayOf(4, 0, 0), synchsafe(frames.size), frames)
        assertEquals(AlbumCredits("Some Label", "© 2024 Some Label"), credits(tag))
    }

    @Test fun noCreditsIsNull() {
        val tag = bytes("ID3".toByteArray(), byteArrayOf(4, 0, 0), synchsafe(0))
        assertNull(credits(tag))
        assertNull(credits(ByteArray(32)))
    }

    private fun vorbisBlock(vararg entries: String): ByteArray {
        val vendor = "test".toByteArray()
        val body = bytes(le32(vendor.size), vendor, le32(entries.size), *entries.map { bytes(le32(it.toByteArray().size), it.toByteArray()) }.toTypedArray())
        return bytes(byteArrayOf((0x80 or 4).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()), body)
    }

    @Test fun flacVorbisComment() {
        val streamInfo = bytes(byteArrayOf(0, 0, 0, 34), ByteArray(34))
        val file = bytes("fLaC".toByteArray(), streamInfo, vorbisBlock("TITLE=Song", "label=Indie Records", "COPYRIGHT=(C) 2020"))
        assertEquals(AlbumCredits("Indie Records", "(C) 2020"), credits(file))
    }

    private fun box(type: ByteArray, vararg payload: ByteArray): ByteArray {
        val body = bytes(*payload)
        return bytes(be32(8 + body.size), type, body)
    }
    private fun box(type: String, vararg payload: ByteArray) = box(type.toByteArray(Charsets.ISO_8859_1), *payload)
    private fun data(text: String) = box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0), text.toByteArray())

    @Test fun mp4Atoms() {
        val freeform = box("----", box("mean", ByteArray(4), "com.apple.iTunes".toByteArray()), box("name", ByteArray(4), "LABEL".toByteArray()), data("Big Label"))
        val ilst = box("ilst", box("cprt", data("℗ 2023 Big Label")), freeform)
        val moov = box("moov", box("udta", box("meta", ByteArray(4), ilst)))
        val file = bytes(box("ftyp", "M4A ".toByteArray(), ByteArray(4)), moov, box("mdat", ByteArray(100)))
        assertEquals(AlbumCredits("Big Label", "℗ 2023 Big Label"), credits(file))
    }
}

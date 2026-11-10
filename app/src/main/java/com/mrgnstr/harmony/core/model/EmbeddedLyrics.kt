package com.mrgnstr.harmony.core.model

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** Random access to a file's bytes. */
interface ByteSource {
    val length: Long

    /** Up to [size] bytes at [position]: fewer at the end of the data, none past it. */
    fun read(position: Long, size: Int): ByteArray
}

class BytesSource(private val bytes: ByteArray) : ByteSource {
    override val length: Long get() = bytes.size.toLong()
    override fun read(position: Long, size: Int): ByteArray {
        if (position >= bytes.size || size <= 0) return ByteArray(0)
        val from = position.toInt()
        return bytes.copyOfRange(from, minOf(bytes.size.toLong(), position + size).toInt())
    }
}

/**
 * Lyrics stored in a song's own tags: ID3v2 (MP3, and FLAC with a leading tag) `USLT` / `SYLT` / `TXXX`, Vorbis comments (FLAC,
 * Ogg Vorbis, Opus) `LYRICS` / `UNSYNCEDLYRICS` / `SYNCEDLYRICS`, and MP4 `©lyr`. Taggers often store a whole LRC (or TTML) file
 * in the plain-text field, so every text goes through the same parsers as the files beside the song; a synced result is
 * preferred over a plain one. Only the tag is read (frames and blocks that are not wanted are skipped by position), never the audio.
 */
object EmbeddedLyrics {
    /** Tags over this size are not read (a tag of several MB is nearly always a cover). */
    private const val MAX_FIELD = 4 * 1024 * 1024

    /** The ID3 / Vorbis comment names that carry lyrics, with everything but letters and digits removed. */
    private val lyricKeys = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "LYRICSSYNCED", "LYRICSLRC", "LYRICSTTML")

    fun read(source: ByteSource): Lyrics? {
        val texts = try { texts(source) } catch (e: Exception) { emptyList() }
        val parsed = texts.mapNotNull { lyricsOf(it) }
        return parsed.firstOrNull { it.synced } ?: parsed.firstOrNull()
    }

    /** [text] read the way a lyrics file is: TTML if it is one, else LRC (plain text when it has no time tags). */
    fun lyricsOf(text: String): Lyrics? {
        val t = text.replace("\u0000", "").trim()
        if (t.isEmpty()) return null
        if (t.startsWith("<") && t.contains("<tt")) TtmlParser.parse(t)?.let { return it }
        return LrcParser.parse(t)
    }

    private fun texts(src: ByteSource): List<String> {
        val head = src.read(0, 12)
        return when {
            head.hasPrefix("ID3") -> {
                val tagEnd = id3End(head)
                id3(src) + if (src.read(tagEnd, 4).hasPrefix("fLaC")) flac(src, tagEnd) else emptyList()
            }
            head.hasPrefix("fLaC") -> flac(src, 0)
            head.hasPrefix("OggS") -> ogg(src)
            head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(src)
            else -> emptyList()
        }
    }

    // ---- ID3v2 ----

    private fun id3End(h: ByteArray): Long = 10L + synchsafe(h, 6) + if (h[3].toInt() == 4 && (h[5].toInt() and 0x10) != 0) 10 else 0

    private fun id3(src: ByteSource): List<String> {
        val h = src.read(0, 10)
        if (h.size < 10) return emptyList()
        val major = h[3].toInt()
        if (major !in 2..4) return emptyList()
        val flags = h[5].toInt()
        var size = synchsafe(h, 6)
        if (size <= 0 || 10L + size > src.length) return emptyList()
        // A tag-wide unsynchronisation (v2.2 / v2.3) has to be undone before frame sizes mean anything.
        val whole = if (flags and 0x80 != 0 && major < 4 && size <= MAX_FIELD * 4) unsync(src.read(10, size)) else null
        if (whole != null) size = whole.size
        fun at(offset: Int, len: Int): ByteArray = if (whole != null) whole.copyOfRange(offset, minOf(whole.size, offset + len)) else src.read(10L + offset, len)

        var pos = 0
        if (flags and 0x40 != 0 && major >= 3) {
            val e = at(0, 4)
            if (e.size < 4) return emptyList()
            pos = if (major == 4) synchsafe(e, 0) else be32(e, 0) + 4
        }
        val idLen = if (major == 2) 3 else 4
        val headerLen = if (major == 2) 6 else 10
        val synced = ArrayList<String>()
        val plain = ArrayList<String>()
        while (pos + headerLen <= size) {
            val fh = at(pos, headerLen)
            if (fh.size < headerLen || fh[0].toInt() == 0) break
            val id = String(fh, 0, idLen, Charsets.ISO_8859_1)
            val frameSize = when (major) { 2 -> (u8(fh, 3) shl 16) or (u8(fh, 4) shl 8) or u8(fh, 5); 3 -> be32(fh, 4); else -> synchsafe(fh, 4) }
            if (frameSize <= 0 || pos + headerLen + frameSize > size) break
            val wanted = id == "USLT" || id == "ULT" || id == "SYLT" || id == "SLT" || id == "TXXX" || id == "TXX"
            if (wanted && frameSize <= MAX_FIELD) {
                var data = at(pos + headerLen, frameSize)
                var skip = false
                if (major == 4) {
                    val f = u8(fh, 9)
                    if (f and 0x0C != 0) skip = true // encrypted or compressed
                    if (f and 0x40 != 0 && data.isNotEmpty()) data = data.copyOfRange(1, data.size) // grouping byte
                    if (f and 0x01 != 0 && data.size >= 4) data = data.copyOfRange(4, data.size) // data length indicator
                    if (f and 0x02 != 0) data = unsync(data)
                } else if (major == 3) {
                    val f = u8(fh, 9)
                    if (f and 0xC0 != 0) skip = true
                    if (f and 0x20 != 0 && data.isNotEmpty()) data = data.copyOfRange(1, data.size)
                }
                if (!skip) when (id) {
                    "SYLT", "SLT" -> sylt(data)?.let { synced += it }
                    "USLT", "ULT" -> uslt(data)?.let { plain += it }
                    else -> txxx(data)?.let { plain += it }
                }
            }
            pos += headerLen + frameSize
        }
        return synced + plain
    }

    /** Text encodings of ID3: 0 Latin-1, 1 UTF-16 with a byte order mark, 2 UTF-16BE, 3 UTF-8. */
    private fun charsetOf(enc: Int): Charset = when (enc) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }

    private fun wide(enc: Int) = enc == 1 || enc == 2

    /** Where the terminator of the string starting at [from] is, -1 if there is none. UTF-16 strings end on an aligned pair of zeros. */
    private fun terminator(b: ByteArray, from: Int, enc: Int): Int {
        if (wide(enc)) {
            var i = from
            while (i + 1 < b.size) { if (b[i].toInt() == 0 && b[i + 1].toInt() == 0) return i; i += 2 }
            return -1
        }
        for (i in from until b.size) if (b[i].toInt() == 0) return i
        return -1
    }

    private fun decode(b: ByteArray, from: Int, to: Int, enc: Int): String = if (to <= from) "" else String(b, from, to - from, charsetOf(enc))

    private fun uslt(d: ByteArray): String? {
        if (d.size < 5) return null
        val enc = u8(d, 0)
        val t = terminator(d, 4, enc)
        if (t < 0) return null
        return decode(d, t + if (wide(enc)) 2 else 1, d.size, enc).takeIf { it.isNotBlank() }
    }

    private fun txxx(d: ByteArray): String? {
        if (d.size < 2) return null
        val enc = u8(d, 0)
        val t = terminator(d, 1, enc)
        if (t < 0) return null
        val key = decode(d, 1, t, enc).uppercase().filter { it.isLetterOrDigit() }
        if (key !in lyricKeys) return null
        return decode(d, t + if (wide(enc)) 2 else 1, d.size, enc).takeIf { it.isNotBlank() }
    }

    /** SYLT in milliseconds as LRC text: a new line wherever a syllable starts with a newline, else one line per entry. */
    private fun sylt(d: ByteArray): String? {
        if (d.size < 7) return null
        val enc = u8(d, 0)
        // 1 = MPEG frames (needs the bitrate to mean anything); only milliseconds are read. Content type 1 = lyrics.
        if (u8(d, 4) != 2 || u8(d, 5) > 1) return null
        val tl = if (wide(enc)) 2 else 1
        var pos = terminator(d, 6, enc).takeIf { it >= 0 }?.plus(tl) ?: return null
        val entries = ArrayList<Pair<Long, String>>()
        while (pos < d.size) {
            val t = terminator(d, pos, enc)
            if (t < 0 || t + tl + 4 > d.size) break
            val text = decode(d, pos, t, enc)
            entries += (be32(d, t + tl).toLong() and 0xFFFFFFFFL) to text
            pos = t + tl + 4
        }
        if (entries.isEmpty()) return null
        val grouped = entries.drop(1).any { it.second.startsWith("\n") }
        val lines = ArrayList<Pair<Long, StringBuilder>>()
        for ((i, e) in entries.withIndex()) {
            val text = e.second.replace("\r", "")
            if (grouped && i > 0 && !text.startsWith("\n")) lines.last().second.append(text) else lines += e.first to StringBuilder(text.removePrefix("\n"))
        }
        return lines.joinToString("\n") { (ms, text) -> "[%02d:%02d.%02d]%s".format(ms / 60_000, ms / 1000 % 60, ms % 1000 / 10, text.toString().trim()) }
    }

    private fun unsync(b: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    // ---- FLAC / Vorbis comments ----

    private fun flac(src: ByteSource, start: Long): List<String> {
        var pos = start + 4
        while (pos + 4 <= src.length) {
            val h = src.read(pos, 4)
            if (h.size < 4) break
            val last = h[0].toInt() and 0x80 != 0
            val type = h[0].toInt() and 0x7F
            val len = (u8(h, 1) shl 16) or (u8(h, 2) shl 8) or u8(h, 3)
            if (type == 4) return if (len <= MAX_FIELD) vorbisComments(src.read(pos + 4, len), 0) else emptyList()
            if (last) break
            pos += 4L + len
        }
        return emptyList()
    }

    private fun vorbisComments(b: ByteArray, from: Int): List<String> {
        var p = from
        fun le32(): Int { if (p + 4 > b.size) return -1; return (u8(b, p) or (u8(b, p + 1) shl 8) or (u8(b, p + 2) shl 16) or (u8(b, p + 3) shl 24)).also { p += 4 } }
        val vendor = le32()
        if (vendor < 0 || p + vendor > b.size) return emptyList()
        p += vendor
        val count = le32()
        val found = ArrayList<String>()
        var i = 0
        while (i++ < count) {
            val len = le32()
            if (len < 0 || p + len > b.size) break
            val entry = String(b, p, len, Charsets.UTF_8)
            p += len
            val eq = entry.indexOf('=')
            if (eq <= 0) continue
            if (entry.substring(0, eq).uppercase().filter { it.isLetterOrDigit() } !in lyricKeys) continue
            val value = entry.substring(eq + 1)
            if (value.isNotBlank()) found += value
        }
        return found
    }

    // ---- Ogg (Vorbis, Opus): the comment header is the stream's second packet ----

    private fun ogg(src: ByteSource): List<String> {
        var pos = 0L
        var index = 0
        val packet = ByteArrayOutputStream()
        val limit = minOf(src.length, 8L * 1024 * 1024)
        while (pos + 27 <= limit) {
            val h = src.read(pos, 27)
            if (h.size < 27 || String(h, 0, 4, Charsets.ISO_8859_1) != "OggS") break
            val segments = u8(h, 26)
            val lacing = src.read(pos + 27, segments)
            if (lacing.size < segments) break
            val total = lacing.sumOf { it.toInt() and 0xFF }
            val data = src.read(pos + 27 + segments, total)
            var off = 0
            for (seg in lacing) {
                val l = seg.toInt() and 0xFF
                if (index == 1 && off + l <= data.size) packet.write(data, off, l)
                off += l
                if (l < 255) {
                    if (index == 1) return oggComments(packet.toByteArray())
                    index++
                }
            }
            pos += 27L + segments + total
        }
        return emptyList()
    }

    private fun oggComments(p: ByteArray): List<String> = when {
        p.hasPrefix("OpusTags") -> vorbisComments(p, 8)
        p.size > 7 && p[0].toInt() == 3 && String(p, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> vorbisComments(p, 7)
        else -> emptyList()
    }

    // ---- MP4 / M4A ----

    private class Atom(val start: Long, val end: Long)

    private fun mp4(src: ByteSource): List<String> {
        val moov = child(src, 0, src.length, "moov".toByteArray(Charsets.ISO_8859_1)) ?: return emptyList()
        val udta = child(src, moov.start, moov.end, "udta".toByteArray(Charsets.ISO_8859_1)) ?: return emptyList()
        val meta = child(src, udta.start, udta.end, "meta".toByteArray(Charsets.ISO_8859_1)) ?: return emptyList()
        // `meta` is a full box: four bytes of version and flags come before its children.
        val ilst = child(src, meta.start + 4, meta.end, "ilst".toByteArray(Charsets.ISO_8859_1)) ?: return emptyList()
        val lyr = child(src, ilst.start, ilst.end, byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())) ?: return emptyList()
        val data = child(src, lyr.start, lyr.end, "data".toByteArray(Charsets.ISO_8859_1)) ?: return emptyList()
        // Eight bytes (type indicator, locale) precede the text.
        val len = data.end - data.start - 8
        if (len <= 0 || len > MAX_FIELD) return emptyList()
        return listOf(String(src.read(data.start + 8, len.toInt()), Charsets.UTF_8))
    }

    private fun child(src: ByteSource, from: Long, to: Long, type: ByteArray): Atom? {
        var pos = from
        while (pos + 8 <= to) {
            val h = src.read(pos, 16)
            if (h.size < 8) return null
            var size = be32(h, 0).toLong() and 0xFFFFFFFFL
            var header = 8L
            if (size == 1L) {
                if (h.size < 16) return null
                size = (be32(h, 8).toLong() shl 32) or (be32(h, 12).toLong() and 0xFFFFFFFFL)
                header = 16
            } else if (size == 0L) size = to - pos
            if (size < header) return null
            if (h[4] == type[0] && h[5] == type[1] && h[6] == type[2] && h[7] == type[3]) return Atom(pos + header, minOf(pos + size, to))
            pos += size
        }
        return null
    }

    // ---- bytes ----

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun be32(b: ByteArray, i: Int) = (u8(b, i) shl 24) or (u8(b, i + 1) shl 16) or (u8(b, i + 2) shl 8) or u8(b, i + 3)
    private fun synchsafe(b: ByteArray, i: Int) = ((u8(b, i) and 0x7F) shl 21) or ((u8(b, i + 1) and 0x7F) shl 14) or ((u8(b, i + 2) and 0x7F) shl 7) or (u8(b, i + 3) and 0x7F)
    private fun ByteArray.hasPrefix(s: String) = size >= s.length && s.indices.all { this[it].toInt() == s[it].code }
}

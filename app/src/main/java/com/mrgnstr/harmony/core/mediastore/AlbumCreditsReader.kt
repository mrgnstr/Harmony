package com.mrgnstr.harmony.core.mediastore

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The record label and copyright line from a song's tags; either may be missing. */
data class AlbumCredits(val label: String?, val copyright: String?) {
    val isEmpty: Boolean get() = label == null && copyright == null
}

/**
 * Reads label and copyright from file tags (MediaStore has no columns for them): ID3v2 (MP3, and FLAC with an ID3 header),
 * FLAC Vorbis comments and MP4 / M4A atoms. Ogg Opus / Vorbis files are not read. Done on demand when an album opens.
 */
@Singleton
class AlbumCreditsReader @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = ConcurrentHashMap<Long, AlbumCredits>()

    suspend fun read(songId: Long): AlbumCredits? {
        cache[songId]?.let { return it }
        return withContext(Dispatchers.IO) { runCatching { load(songId) }.getOrNull() }?.also { cache[songId] = it }
    }

    private fun load(songId: Long): AlbumCredits? {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        return context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                TagParser.credits(object : TagSource {
                    override val size = channel.size()
                    override fun read(pos: Long, len: Int): ByteArray {
                        val n = minOf(len.toLong(), size - pos).toInt().coerceAtLeast(0)
                        val buf = ByteBuffer.allocate(n)
                        var at = pos
                        while (buf.hasRemaining()) { val r = channel.read(buf, at); if (r <= 0) break; at += r }
                        return buf.array().copyOf(buf.position())
                    }
                })
            }
        }
    }
}

/** Random access over a file (or, in tests, an array). Reads past the end return what is there. */
internal interface TagSource {
    val size: Long
    fun read(pos: Long, len: Int): ByteArray
}

internal class BytesSource(private val bytes: ByteArray) : TagSource {
    override val size get() = bytes.size.toLong()
    override fun read(pos: Long, len: Int): ByteArray =
        if (pos >= bytes.size) ByteArray(0) else bytes.copyOfRange(pos.toInt(), minOf(bytes.size.toLong(), pos + len).toInt())
}

/** Pure parsers: they only look at the headers and read the frames / blocks / atoms they want. */
internal object TagParser {
    private const val MAX_FRAME = 64 * 1024
    private const val MAX_MOOV = 16 * 1024 * 1024
    private val LabelKeys = setOf("LABEL", "ORGANIZATION", "PUBLISHER", "LABELNO")

    fun credits(src: TagSource): AlbumCredits? {
        val head = src.read(0, 12)
        var fromId3: AlbumCredits? = null
        var flacAt = 0L
        if (head.size >= 10 && String(head, 0, 3, Charsets.ISO_8859_1) == "ID3") {
            fromId3 = id3(src, head)
            flacAt = 10 + synchsafe(head, 6)
        }
        val credits = when {
            String(src.read(flacAt, 4), Charsets.ISO_8859_1) == "fLaC" -> flac(src, flacAt + 4) ?: fromId3
            head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(src)
            else -> fromId3
        }
        return credits?.takeUnless { it.isEmpty }
    }

    private fun synchsafe(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0x7F) shl 21) or ((b[at + 1].toLong() and 0x7F) shl 14) or ((b[at + 2].toLong() and 0x7F) shl 7) or (b[at + 3].toLong() and 0x7F)

    private fun be32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

    private fun le32(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

    private fun clean(s: String) = s.replace("\u0000", " ").trim().takeIf { it.isNotEmpty() }

    // ID3v2.3 / 2.4: TPUB = publisher (the label), TCOP = copyright.
    private fun id3(src: TagSource, head: ByteArray): AlbumCredits? {
        val v4 = head[3].toInt() == 4
        if (head[3].toInt() < 3) return null
        val end = minOf(src.size, 10 + synchsafe(head, 6))
        var pos = 10L
        if (head[5].toInt() and 0x40 != 0) { // extended header
            val ext = src.read(pos, 4).takeIf { it.size == 4 } ?: return null
            pos += if (v4) synchsafe(ext, 0) else be32(ext, 0) + 4
        }
        var label: String? = null
        var copyright: String? = null
        while (pos + 10 <= end && (label == null || copyright == null)) {
            val h = src.read(pos, 10)
            if (h.size < 10 || h[0].toInt() == 0) break
            val id = String(h, 0, 4, Charsets.ISO_8859_1)
            val size = if (v4) synchsafe(h, 4) else be32(h, 4)
            if (size <= 0 || pos + 10 + size > end) break
            if ((id == "TPUB" || id == "TCOP") && size <= MAX_FRAME) {
                val text = id3Text(src.read(pos + 10, size.toInt()))
                if (id == "TPUB") label = text else copyright = text
            }
            pos += 10 + size
        }
        return AlbumCredits(label, copyright)
    }

    private fun id3Text(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val body = data.copyOfRange(1, data.size)
        return clean(
            when (data[0].toInt()) {
                1 -> String(body, Charsets.UTF_16)
                2 -> String(body, Charsets.UTF_16BE)
                3 -> String(body, Charsets.UTF_8)
                else -> String(body, Charsets.ISO_8859_1)
            },
        )
    }

    // FLAC: metadata blocks after "fLaC"; type 4 is the Vorbis comment (LABEL=, COPYRIGHT=, ...).
    private fun flac(src: TagSource, start: Long): AlbumCredits? {
        var pos = start
        while (pos + 4 <= src.size) {
            val h = src.read(pos, 4)
            val last = h[0].toInt() and 0x80 != 0
            val type = h[0].toInt() and 0x7F
            val len = ((h[1].toLong() and 0xFF) shl 16) or ((h[2].toLong() and 0xFF) shl 8) or (h[3].toLong() and 0xFF)
            if (type == 4 && len <= MAX_FRAME) return vorbisComment(src.read(pos + 4, len.toInt()))
            if (last) break
            pos += 4 + len
        }
        return null
    }

    private fun vorbisComment(b: ByteArray): AlbumCredits? {
        if (b.size < 8) return null
        var p = 4 + le32(b, 0).toInt()
        if (p + 4 > b.size) return null
        var count = le32(b, p)
        p += 4
        var label: String? = null
        var copyright: String? = null
        while (count-- > 0 && p + 4 <= b.size) {
            val n = le32(b, p).toInt()
            p += 4
            if (n < 0 || p + n > b.size) break
            val entry = String(b, p, n, Charsets.UTF_8)
            p += n
            val key = entry.substringBefore('=').uppercase()
            val value = clean(entry.substringAfter('=', ""))
            if (key in LabelKeys && label == null) label = value else if (key == "COPYRIGHT" && copyright == null) copyright = value
        }
        return AlbumCredits(label, copyright)
    }

    // MP4: moov > udta > meta > ilst. "cprt" is the copyright; label is "©pub" or a freeform ---- atom named LABEL / PUBLISHER / ORGANIZATION.
    private fun mp4(src: TagSource): AlbumCredits? {
        val moov = findBox(src, 0, src.size, "moov") ?: return null
        if (moov.second - moov.first > MAX_MOOV) return null
        val m = BytesSource(src.read(moov.first, (moov.second - moov.first).toInt()))
        val udta = findBox(m, 0, m.size, "udta") ?: return null
        val meta = findBox(m, udta.first, udta.second, "meta") ?: return null
        val ilst = findBox(m, meta.first + 4, meta.second, "ilst") ?: return null
        var label: String? = null
        var copyright: String? = null
        forEachBox(m, ilst.first, ilst.second) { type, from, to ->
            when (type) {
                "cprt" -> copyright = dataText(m, from, to)
                "©pub" -> label = dataText(m, from, to)
                "----" -> {
                    val name = findBox(m, from, to, "name")?.let { String(m.read(it.first + 4, (it.second - it.first - 4).toInt()), Charsets.UTF_8).uppercase() }
                    if (name in LabelKeys && label == null) label = dataText(m, from, to)
                }
            }
            true
        }
        return AlbumCredits(label, copyright)
    }

    private fun dataText(m: TagSource, from: Long, to: Long): String? =
        findBox(m, from, to, "data")?.let { d -> clean(String(m.read(d.first + 8, (d.second - d.first - 8).toInt().coerceAtLeast(0)), Charsets.UTF_8)) }

    /** The payload range of the first box of [type] between [from] and [to]. */
    private fun findBox(src: TagSource, from: Long, to: Long, type: String): Pair<Long, Long>? {
        var found: Pair<Long, Long>? = null
        forEachBox(src, from, to) { t, s, e -> if (t == type) { found = s to e; false } else true }
        return found
    }

    private fun forEachBox(src: TagSource, from: Long, to: Long, block: (type: String, start: Long, end: Long) -> Boolean) {
        var pos = from
        while (pos + 8 <= to) {
            val h = src.read(pos, 16)
            if (h.size < 8) return
            var size = be32(h, 0)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var header = 8L
            if (size == 1L && h.size >= 16) { size = (be32(h, 8) shl 32) or be32(h, 12); header = 16 } else if (size == 0L) size = to - pos
            if (size < header || pos + size > to) return
            if (!block(type, pos + header, pos + size)) return
            pos += size
        }
    }
}

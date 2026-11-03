package com.mrgnstr.harmony.core.mediastore

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.provider.MediaStore
import com.mrgnstr.harmony.core.model.AudioFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads codec, sample rate and bit depth from a file's header. Done on demand (when an album opens) rather than during
 * library sync: it touches every file, and MediaStore has none of these columns below API 31.
 */
@Singleton
class AudioFormatProbe @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = ConcurrentHashMap<Long, AudioFormat>()

    suspend fun probe(songId: Long): AudioFormat? {
        cache[songId]?.let { return it }
        return withContext(Dispatchers.IO) { runCatching { read(songId) }.getOrNull() }?.also { cache[songId] = it }
    }

    private fun read(songId: Long): AudioFormat? {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val format = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
            // "bits-per-sample" (KEY_BITS_PER_SAMPLE, API 33) is only filled in by some extractors.
            var bits = if (format.containsKey("bits-per-sample")) format.getInteger("bits-per-sample") else 0
            if (bits == 0 && mime == "audio/raw" && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                bits = when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                    android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                    android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                    else -> 16
                }
            }
            // Containers whose header states depth/rate exactly: read it ourselves, which also works below API 33.
            val head = if (mime == "audio/flac" || mime == "audio/raw") context.contentResolver.openInputStream(uri)?.use { it.readHead() } else null
            if (head != null) {
                (if (mime == "audio/flac") HeaderParser.flac(head) else HeaderParser.wav(head))?.let { (r, b) ->
                    rate = r; bits = b
                }
            }
            return AudioFormat(AudioFormat.codecName(mime), AudioFormat.isLossless(mime), rate, bits)
        } finally {
            extractor.release()
        }
    }

    private fun InputStream.readHead(): ByteArray {
        val buffer = ByteArray(HEAD_BYTES)
        var n = 0
        while (n < buffer.size) { val r = read(buffer, n, buffer.size - n); if (r <= 0) break; n += r }
        return buffer.copyOf(n)
    }

    private companion object { const val HEAD_BYTES = 4096 }
}

/** Pure parsers over the first bytes of a file; each returns (sampleRateHz, bitDepth) or null. */
internal object HeaderParser {
    fun flac(b: ByteArray): Pair<Int, Int>? {
        var o = 0
        // An ID3v2 tag may precede the "fLaC" marker; its size is a 28-bit synchsafe integer.
        if (b.size > 10 && b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) {
            o = 10 + ((b[6].toInt() and 0x7F shl 21) or (b[7].toInt() and 0x7F shl 14) or (b[8].toInt() and 0x7F shl 7) or (b[9].toInt() and 0x7F))
        }
        if (o + 22 > b.size || String(b, o, 4, Charsets.ISO_8859_1) != "fLaC") return null
        // Marker(4) + block header(4) + min/max block size(4) + min/max frame size(6) = STREAMINFO bit fields at +18.
        val p = o + 18
        fun u(i: Int) = b[p + i].toInt() and 0xFF
        val rate = (u(0) shl 12) or (u(1) shl 4) or (u(2) shr 4)
        val bits = (((u(2) and 1) shl 4) or (u(3) shr 4)) + 1
        return rate to bits
    }

    fun wav(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 12 || String(b, 0, 4, Charsets.ISO_8859_1) != "RIFF") return null
        fun le32(i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
        fun le16(i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
        var o = 12
        while (o + 8 <= b.size) {
            val size = le32(o + 4)
            if (String(b, o, 4, Charsets.ISO_8859_1) == "fmt " && o + 24 <= b.size) return le32(o + 12) to le16(o + 22)
            if (size < 0) return null
            o += 8 + size + (size and 1)
        }
        return null
    }
}

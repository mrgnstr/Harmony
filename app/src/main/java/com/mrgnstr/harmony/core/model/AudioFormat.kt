package com.mrgnstr.harmony.core.model

/** Technical format of one audio file. [bitDepth] is 0 when it could not be determined. */
data class AudioFormat(val codec: String, val lossless: Boolean, val sampleRateHz: Int, val bitDepth: Int) {
    /** Beyond 24-bit / 48 kHz, the same line Apple draws between Lossless and Hi-Res Lossless. */
    val hiRes: Boolean get() = lossless && (sampleRateHz > 48_000 || bitDepth > 24)

    val atmos: Boolean get() = codec == DOLBY_ATMOS

    companion object {
        const val DOLBY_ATMOS = "Dolby Atmos"

        /** Display name from a MediaFormat mime type, written the way the codec is traditionally spelled. */
        fun codecName(mime: String): String = when (mime.lowercase()) {
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/alac" -> "ALAC"
            "audio/raw" -> "PCM"
            "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2" -> "MP3"
            "audio/mp4a-latm", "audio/aac" -> "AAC"
            "audio/opus" -> "Opus"
            "audio/vorbis" -> "Vorbis"
            "audio/ac3" -> "AC-3"
            // Android's extractor reports Atmos-carrying E-AC-3 (JOC) as plain eac3, so every E-AC-3 file is treated as Atmos.
            "audio/eac3", "audio/eac3-joc" -> DOLBY_ATMOS
            else -> mime.substringAfter('/').removePrefix("x-").uppercase()
        }

        fun isLossless(mime: String) = mime.lowercase() in setOf("audio/flac", "audio/x-flac", "audio/alac", "audio/raw")

        /**
         * What the album header's format badge shows, from the album's main codec: Dolby Atmos (no text), or the codec
         * name prefixed "Lossless" / "Hi-Res Lossless" when that codec is lossless (hi-res when most songs are).
         * Null when nothing could be probed.
         */
        fun summarize(formats: List<AudioFormat>): FormatSummary? {
            if (formats.isEmpty()) return null
            val main = formats.groupingBy { it.codec }.eachCount().maxBy { it.value }.key
            val ofMain = formats.filter { it.codec == main }
            val first = ofMain.first()
            if (first.atmos) return FormatSummary(atmos = true, text = null, lossless = false)
            if (!first.lossless) return FormatSummary(atmos = false, text = main, lossless = false)
            val hiRes = ofMain.count { it.hiRes } * 2 > ofMain.size
            return FormatSummary(atmos = false, text = (if (hiRes) "Hi-Res Lossless " else "Lossless ") + main, lossless = true)
        }
    }
}

/** [atmos] shows the Dolby Atmos item; otherwise [text] is the codec item, with the lossless logo when [lossless]. */
data class FormatSummary(val atmos: Boolean, val text: String?, val lossless: Boolean)

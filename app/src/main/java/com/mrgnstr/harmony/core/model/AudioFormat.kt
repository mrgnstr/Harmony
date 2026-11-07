package com.mrgnstr.harmony.core.model

/** Technical format of one audio file. [bitDepth] is 0 when it could not be determined. */
data class AudioFormat(val codec: String, val lossless: Boolean, val sampleRateHz: Int, val bitDepth: Int, val channels: Int = 2, val bitRateKbps: Int = 0) {
    /** Beyond 24-bit / 48 kHz, the same line Apple draws between Lossless and Hi-Res Lossless. */
    val hiRes: Boolean get() = lossless && (sampleRateHz > 48_000 || bitDepth > 24)

    val atmos: Boolean get() = codec == DOLBY_ATMOS

    /**
     * Now Playing's pill: "[layout ]codec" and then bit depth + sample rate for lossless, bitrate + sample rate for lossy
     * (each part left out when unknown).
     */
    val pill: FormatTag get() {
        val layout = channelLayout(channels)
        val dolby = codec in DolbyCodecs
        val name = if (layout != null && !atmos) "$layout $codec" else codec
        val rate = sampleRateHz.takeIf { it > 0 }?.let { hz -> "${(hz / 1000.0).toString().removeSuffix(".0")} kHz" }
        val detail = if (lossless) bitDepth.takeIf { it > 0 }?.let { "$it-bit" } else bitRateKbps.takeIf { it > 0 }?.let { "$it kbps" }
        val icon = when {
            dolby -> FormatIcon.Dolby
            layout != null -> FormatIcon.Surround
            lossless -> FormatIcon.Lossless
            else -> FormatIcon.Lossy
        }
        return FormatTag(listOfNotNull(name, detail, rate).joinToString(" · "), icon)
    }

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
            // Plain E-AC-3 stays E-AC-3; the probe upgrades it to Atmos when the file carries JOC (Android's extractor often reports it as plain eac3).
            "audio/eac3" -> "E-AC-3"
            "audio/eac3-joc" -> DOLBY_ATMOS
            "audio/true-hd" -> "TrueHD"
            "audio/vnd.dts" -> "DTS"
            "audio/vnd.dts.hd" -> "DTS-HD"
            else -> mime.substringAfter('/').removePrefix("x-").uppercase()
        }

        fun isLossless(mime: String) = mime.lowercase() in setOf("audio/flac", "audio/x-flac", "audio/alac", "audio/raw")

        /**
         * "5.1", "7.1", "7.1.4", ... for [channels] discrete channels; null for mono and stereo. Files only carry a count (Opus mapping
         * family 255 and most others have no layout), so beyond 8 the usual immersive home layouts are assumed (10 = 7.1.2, 12 = 7.1.4,
         * 14 = 9.1.4, 16 = 9.1.6) and any other count is shown as "9ch".
         */
        fun channelLayout(channels: Int): String? = when (channels) {
            in 0..2 -> null
            3 -> "3.0"
            4 -> "4.0"
            5 -> "5.0"
            6 -> "5.1"
            7 -> "6.1"
            8 -> "7.1"
            10 -> "7.1.2"
            12 -> "7.1.4"
            14 -> "9.1.4"
            16 -> "9.1.6"
            else -> "${channels}ch"
        }

        /**
         * The album header's format tags, most common first (at most [limit]): Dolby Atmos on its own, "Lossless FLAC" /
         * "Hi-Res Lossless FLAC" for stereo lossless, the plain codec name for lossy stereo, and "[layout] [codec]" ("5.1 FLAC",
         * "7.1 E-AC-3") for anything multichannel.
         */
        fun tags(formats: List<AudioFormat>, limit: Int = 3): List<FormatTag> {
            val groups = formats.groupBy { if (it.atmos) DOLBY_ATMOS to null else it.codec to channelLayout(it.channels) }
            return groups.values.sortedByDescending { it.size }.map { group ->
                val first = group.first()
                val layout = channelLayout(first.channels)
                val dolby = first.codec in DolbyCodecs
                when {
                    first.atmos -> FormatTag(DOLBY_ATMOS, FormatIcon.Dolby)
                    layout != null -> FormatTag("$layout ${first.codec}", if (dolby) FormatIcon.Dolby else FormatIcon.Surround)
                    dolby -> FormatTag(first.codec, FormatIcon.Dolby)
                    !first.lossless -> FormatTag(first.codec, FormatIcon.Lossy)
                    else -> {
                        val hiRes = group.count { it.hiRes } * 2 > group.size
                        FormatTag((if (hiRes) "Hi-Res Lossless " else "Lossless ") + first.codec, FormatIcon.Lossless)
                    }
                }
            }.distinct().take(limit)
        }

        internal val DolbyCodecs = setOf(DOLBY_ATMOS, "E-AC-3", "AC-3", "TrueHD")
    }
}

/** Which icon a format tag wears: the Dolby mark (any Dolby format), surround (other multichannel), HD (lossless) or a plain note (lossy stereo). */
enum class FormatIcon { Dolby, Surround, Lossless, Lossy }

/** One quick tag in the album header. */
data class FormatTag(val text: String, val icon: FormatIcon)

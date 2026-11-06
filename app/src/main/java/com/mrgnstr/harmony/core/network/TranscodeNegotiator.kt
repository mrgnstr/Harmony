package com.mrgnstr.harmony.core.network

import com.mrgnstr.harmony.core.model.ServerAccount
import com.mrgnstr.harmony.core.model.StreamQuality
import com.mrgnstr.harmony.core.model.TranscodeDecisionModel
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedStreamUrl(
    val url: String,
    val decision: TranscodeDecisionModel? = null,
    val isDirectPlay: Boolean = true,
)

/**
 * OpenSubsonic `transcoding` extension negotiator (`POST /rest/getTranscodeDecision` + `GET /rest/getTranscodeStream`),
 * with automatic fallback to `/rest/stream` when the server does not advertise the `transcoding` extension.
 */
@Singleton
class TranscodeNegotiator @Inject constructor(
    private val subsonic: SubsonicApiClient,
) {
    /**
     * Builds the OpenSubsonic `ClientInfo` JSON payload for `getTranscodeDecision`.
     * Note: OpenSubsonic expects bitrates in bits-per-second (bps), whereas `/rest/stream` uses kbps.
     */
    fun buildClientInfo(
        quality: StreamQuality,
        maxChannels: Int = 8,
    ): JsonObj {
        val maxBps = if (quality.maxBitRateKbps > 0) quality.maxBitRateKbps * 1000 else 0
        val allowLossless = quality == StreamQuality.Lossless || quality == StreamQuality.ServerDefault

        val directPlayProfiles = buildList {
            if (allowLossless) {
                add(
                    buildJsonObj {
                        putStrings("containers", listOf("flac", "wav", "aiff", "mp4", "m4a", "ogg", "opus", "mp3"))
                        putStrings("audioCodecs", listOf("flac", "alac", "pcm", "opus", "vorbis", "mp3", "aac", "eac3", "ac3"))
                        putStrings("protocols", listOf("http"))
                        put("maxAudioChannels", maxChannels)
                    },
                )
            } else {
                add(
                    buildJsonObj {
                        putStrings("containers", listOf("ogg", "opus", "mp3", "mp4", "m4a"))
                        putStrings("audioCodecs", listOf("opus", "vorbis", "mp3", "aac"))
                        putStrings("protocols", listOf("http"))
                        put("maxAudioChannels", 2)
                    },
                )
            }
        }

        val transcodingProfiles = listOf(
            buildJsonObj {
                put("container", if (quality.preferredFormat == "mp3") "mp3" else "opus")
                put("audioCodec", if (quality.preferredFormat == "mp3") "mp3" else "opus")
                put("protocol", "http")
                put("maxAudioChannels", 2)
            },
            buildJsonObj {
                put("container", "mp3")
                put("audioCodec", "mp3")
                put("protocol", "http")
                put("maxAudioChannels", 2)
            },
            buildJsonObj {
                put("container", "flac")
                put("audioCodec", "flac")
                put("protocol", "http")
                put("maxAudioChannels", maxChannels)
            },
        )

        val codecProfiles = if (maxBps > 0) {
            listOf(
                buildJsonObj {
                    put("type", "AudioCodec")
                    put("name", "*")
                    putObjects(
                        "limitations",
                        listOf(
                            buildJsonObj {
                                put("name", "audioBitrate")
                                put("comparison", "LessThanEqual")
                                putStrings("values", listOf(maxBps.toString()))
                                put("required", true)
                            },
                        ),
                    )
                },
            )
        } else emptyList()

        return buildJsonObj {
            put("name", SubsonicAuth.CLIENT_NAME)
            put("platform", "Android")
            if (maxBps > 0) {
                put("maxAudioBitrate", maxBps)
                put("maxTranscodingAudioBitrate", maxBps)
            }
            putObjects("directPlayProfiles", directPlayProfiles)
            putObjects("transcodingProfiles", transcodingProfiles)
            if (codecProfiles.isNotEmpty()) {
                putObjects("codecProfiles", codecProfiles)
            }
        }
    }

    suspend fun resolveStream(
        account: ServerAccount,
        songRemoteId: String,
        quality: StreamQuality,
        offsetSec: Int = 0,
    ): ResolvedStreamUrl {
        if (account.supportsExtension("transcoding")) {
            val decision = runCatching {
                subsonic.getTranscodeDecision(account, songRemoteId, buildClientInfo(quality))
            }.getOrNull()
            if (decision != null && !decision.transcodeParams.isNullOrBlank() && (decision.canDirectPlay || decision.canTranscode)) {
                return ResolvedStreamUrl(
                    url = subsonic.buildTranscodeStreamUrl(account, songRemoteId, decision.transcodeParams, offsetSec),
                    decision = decision,
                    isDirectPlay = decision.canDirectPlay,
                )
            }
        }
        val maxBitRate = quality.maxBitRateKbps.coerceAtLeast(0)
        val format = quality.preferredFormat.takeIf { it.isNotBlank() }
        return ResolvedStreamUrl(
            url = subsonic.buildStreamUrl(account, songRemoteId, maxBitRate, format, offsetSec),
            decision = null,
            isDirectPlay = maxBitRate == 0 && (format == null || format == "raw"),
        )
    }
}

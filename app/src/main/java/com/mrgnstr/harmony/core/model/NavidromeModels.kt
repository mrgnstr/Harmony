package com.mrgnstr.harmony.core.model

import kotlin.math.min
import kotlin.math.pow

/**
 * Deterministic 63-bit negative IDs for remote Navidrome entities so they coexist with positive MediaStore IDs
 * in Room, PlayerConnection, ArtworkKey, queues, playlists, favourites, and history without collisions.
 */
object RemoteIds {
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L

    fun toLocalId(serverId: Long, kind: String, remoteId: String): Long {
        val input = "$serverId:$kind:${remoteId.trim()}"
        var h = FNV_OFFSET
        for (i in input.indices) {
            h = h xor input[i].code.toLong()
            h *= FNV_PRIME
        }
        // Mix bits (MurmurHash3 finalizer) for avalanche distribution across 63 bits.
        h = h xor (h ushr 33)
        h *= -0xae502812aa7333L
        h = h xor (h ushr 33)
        h *= -0x3b314601e57a13adL
        h = h xor (h ushr 33)
        val positive = (h and Long.MAX_VALUE).coerceAtLeast(1L)
        return -positive
    }

    fun songId(serverId: Long, remoteId: String): Long = toLocalId(serverId, "song", remoteId)
    fun albumId(serverId: Long, remoteId: String): Long = toLocalId(serverId, "album", remoteId)
    fun artistId(serverId: Long, remoteId: String): Long = toLocalId(serverId, "artist", remoteId)
    fun playlistId(serverId: Long, remoteId: String): Long = toLocalId(serverId, "playlist", remoteId)
    fun radioId(serverId: Long, remoteId: String): Long = toLocalId(serverId, "radio", remoteId)

    fun isRemoteId(id: Long): Boolean = id < 0L
}

/** Which music source(s) Library, Home and Search display. */
enum class LibrarySourceMode(val label: String) {
    All("All Music"),
    LocalOnly("On This Device"),
    NavidromeOnly("Navidrome"),
    DownloadedOnly("Downloaded"),
}

/** How Harmony authenticates against a Navidrome / OpenSubsonic server. */
enum class AuthMethod(val label: String) {
    Password("Password / Token"),
    ApiKey("API Key (nds_…)"),
    QuickConnect("Quick Connect"),
}

/** A configured Navidrome / OpenSubsonic server account. */
data class ServerAccount(
    val id: Long = 0L,
    val name: String,
    val baseUrl: String,
    val username: String,
    val authMethod: AuthMethod = AuthMethod.Password,
    val passwordOrToken: String = "",
    val subsonicSalt: String = "",
    val subsonicToken: String = "",
    val apiKey: String? = null,
    val jwtToken: String? = null,
    val serverType: String = "navidrome",
    val serverVersion: String = "",
    val openSubsonic: Boolean = true,
    val extensions: Set<String> = emptySet(),
    val selectedMusicFolders: Set<Int> = emptySet(),
    val isAdmin: Boolean = false,
    val downloadRole: Boolean = true,
    val shareRole: Boolean = false,
    val jukeboxRole: Boolean = false,
    val scrobblingEnabled: Boolean = true,
    val allowSelfSignedTls: Boolean = false,
    val active: Boolean = true,
    val lastSyncMs: Long = 0L,
) {
    val normalizedBaseUrl: String get() = normalizeServerUrl(baseUrl)
    fun supportsExtension(name: String): Boolean = name in extensions
}

fun normalizeServerUrl(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

/** A Navidrome library / Subsonic musicFolder. */
data class MusicFolder(
    val id: Int,
    val name: String,
    val serverId: Long = 1L,
    val selected: Boolean = true,
)

/** OpenSubsonic extension capability reported by getOpenSubsonicExtensions. */
data class OpenSubsonicExtension(
    val name: String,
    val versions: List<Int>,
)

/** Streaming bitrate / format preset for Wi-Fi or Mobile Data. */
enum class StreamQuality(val label: String, val maxBitRateKbps: Int, val preferredFormat: String) {
    Lossless("Lossless (Original)", 0, "raw"),
    High("High (320 kbps)", 320, "mp3"),
    Balanced("Balanced (192 kbps Opus)", 192, "opus"),
    DataSaver("Data Saver (128 kbps Opus)", 128, "opus"),
    ServerDefault("Server Default", -1, ""),
}

/** What offline downloads are converted to on the server; only formats the user ticked are converted, the rest download as they are. */
enum class DownloadQuality(val label: String, val bitrateKbps: Int) {
    Original("Original (no conversion)", 0),
    Opus320("Opus 320 kbps", 320),
    Opus192("Opus 192 kbps", 192),
    Opus128("Opus 128 kbps", 128),
}

/** ReplayGain loudness normalization mode. */
enum class ReplayGainMode(val label: String) {
    Off("Off"),
    Track("Track Gain"),
    Album("Album Gain"),
    Smart("Smart (Album / Track)"),
}

/** ReplayGain tags from OpenSubsonic / Navidrome. */
data class ReplayGainInfo(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
    val baseGainDb: Double? = null,
    val fallbackGainDb: Double? = null,
) {
    val isEmpty: Boolean
        get() = trackGainDb == null && albumGainDb == null && trackPeak == null && albumPeak == null && baseGainDb == null && fallbackGainDb == null

    /** Linear amplitude multiplier (1.0 = unity) for [mode], with optional preamp and peak clipping protection. */
    fun computeGainFactor(
        mode: ReplayGainMode,
        isAlbumSource: Boolean,
        preampDb: Float = 0f,
        preventClipping: Boolean = true,
    ): Float {
        if (mode == ReplayGainMode.Off) return 1f
        val preferAlbum = mode == ReplayGainMode.Album || (mode == ReplayGainMode.Smart && isAlbumSource)
        val gainDb = if (preferAlbum) {
            albumGainDb ?: trackGainDb ?: fallbackGainDb
        } else {
            trackGainDb ?: albumGainDb ?: fallbackGainDb
        } ?: return 1f
        val peak = if (preferAlbum) {
            albumPeak ?: trackPeak
        } else {
            trackPeak ?: albumPeak
        }
        val totalDb = gainDb + (baseGainDb ?: 0.0) + preampDb.toDouble()
        var factor = 10.0.pow(totalDb / 20.0).toFloat()
        if (preventClipping && peak != null && peak > 0.0) {
            factor = min(factor, (1.0 / peak).toFloat())
        }
        return factor.coerceIn(0.05f, 4.0f)
    }
}

/** Audio stream details returned by OpenSubsonic getTranscodeDecision. */
data class StreamDetailsModel(
    val protocol: String = "http",
    val container: String = "",
    val codec: String = "",
    val audioChannels: Int = 2,
    val audioBitrateBps: Int = 0,
    val audioProfile: String = "",
    val audioSamplerate: Int = 0,
    val audioBitdepth: Int = 0,
) {
    val bitrateKbps: Int get() = (audioBitrateBps + 500) / 1000
}

/** Result of OpenSubsonic getTranscodeDecision negotiation. */
data class TranscodeDecisionModel(
    val canDirectPlay: Boolean,
    val canTranscode: Boolean,
    val transcodeReasons: List<String> = emptyList(),
    val errorReason: String? = null,
    val transcodeParams: String? = null,
    val sourceStream: StreamDetailsModel? = null,
    val transcodeStream: StreamDetailsModel? = null,
) {
    /** Human-readable summary of the active stream for the Now Playing badge. */
    val badgeText: String
        get() = when {
            canDirectPlay && sourceStream != null -> {
                val codec = sourceStream.codec.uppercase().ifEmpty { sourceStream.container.uppercase() }
                val hiRes = if (sourceStream.audioBitdepth > 0 && sourceStream.audioSamplerate > 0) {
                    " ${sourceStream.audioBitdepth}-bit/${sourceStream.audioSamplerate / 1000}kHz"
                } else ""
                "$codec$hiRes · Direct"
            }
            canTranscode && transcodeStream != null -> {
                val src = sourceStream?.codec?.uppercase()?.takeIf { it.isNotEmpty() } ?: "Audio"
                val dst = transcodeStream.codec.uppercase().ifEmpty { transcodeStream.container.uppercase() }
                val kbps = transcodeStream.bitrateKbps.takeIf { it > 0 }?.let { " ${it}k" }.orEmpty()
                "$src → $dst$kbps"
            }
            else -> "Stream"
        }
}

/** A participant credit on a song or album (Composer, Producer, Conductor, Performer, etc.). */
data class ParticipantCredit(
    val id: String,
    val name: String,
    val role: String,
    val subRole: String? = null,
) {
    val displayRole: String
        get() = role.replaceFirstChar { it.uppercase() } + (subRole?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
}

/** Disc subtitle and optional per-disc coverArt ID from OpenSubsonic discTitles. */
data class DiscTitleInfo(
    val disc: Int,
    val title: String,
    val coverArtId: String? = null,
)

/** Rich Navidrome/OpenSubsonic album metadata beyond what MediaStore holds. */
data class AlbumExtraInfo(
    val albumId: Long,
    val serverId: Long,
    val remoteId: String,
    val coverArtId: String? = null,
    val thumbHash: String? = null,
    val dominantColor: String? = null,
    val releaseTypes: List<String> = emptyList(),
    val version: String? = null,
    val recordLabels: List<String> = emptyList(),
    val catalogNum: String? = null,
    val originalReleaseDate: String? = null,
    val releaseDate: String? = null,
    val isCompilation: Boolean = false,
    val description: String? = null,
    val mbzAlbumId: String? = null,
    val externalUrl: String? = null,
    val userRating: Int = 0,
    val averageRating: Double = 0.0,
    val discTitles: List<DiscTitleInfo> = emptyList(),
    val participants: List<ParticipantCredit> = emptyList(),
)

/** Reference to a similar artist returned by getArtistInfo2. */
data class SimilarArtistRef(
    val id: String,
    val name: String,
    val coverArtId: String? = null,
    val albumCount: Int = 0,
    /** The server's picture of the artist (a signed, public URL), for artists the library does not have. */
    val imageUrl: String? = null,
)

/** Rich Navidrome/OpenSubsonic artist metadata (biography, roles, similar artists, images). */
data class ArtistExtraInfo(
    val name: String,
    val serverId: Long,
    val remoteId: String,
    val sortName: String? = null,
    val coverArtId: String? = null,
    val thumbHash: String? = null,
    val dominantColor: String? = null,
    val biography: String? = null,
    val mbzArtistId: String? = null,
    val externalUrl: String? = null,
    val roles: List<String> = emptyList(),
    val similarArtists: List<SimilarArtistRef> = emptyList(),
    val userRating: Int = 0,
    val averageRating: Double = 0.0,
)

/** Sonic similarity match from getSonicSimilarTracks. */
data class SonicMatchItem(
    val song: Song,
    val similarity: Double,
)

/** Internet Radio station managed on Navidrome. */
data class RadioStation(
    val id: Long,
    val serverId: Long,
    val remoteId: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
    val coverArtId: String? = null,
)

/** A public share link created on Navidrome (/share/<id>). */
data class ShareLink(
    val id: String,
    val url: String,
    val description: String,
    val username: String,
    val createdMs: Long,
    val expiresMs: Long? = null,
    val lastVisitedMs: Long? = null,
    val visitCount: Int = 0,
    val downloadable: Boolean = true,
    val itemCount: Int = 0,
)

/** A saved playback position bookmark on Navidrome. */
data class AudioBookmark(
    val songId: Long,
    val remoteSongId: String,
    val songTitle: String,
    val artist: String,
    val albumId: Long,
    val positionMs: Long,
    val comment: String,
    val createdMs: Long,
    val changedMs: Long,
)

/** Active playback session reported by getNowPlaying. */
data class NowPlayingSession(
    val song: Song,
    val username: String,
    val minutesAgo: Int,
    val playerId: Int,
    val playerName: String,
    val state: String,
    val positionMs: Long,
    val playbackRate: Double = 1.0,
)

/** Server library scanner status from getScanStatus or SSE scanStatus. */
data class ScanStatusInfo(
    val scanning: Boolean = false,
    val count: Long = 0L,
    val folderCount: Long = 0L,
    val lastScanMs: Long? = null,
    val error: String? = null,
    val scanType: String? = null,
    val elapsedTimeMs: Long = 0L,
)

/** Missing track/file reported by Navidrome's /api/missing maintenance endpoint. */
data class MissingFileItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val size: Long,
)

/** Quick Connect authorization session. */
data class QuickConnectChallenge(
    val code: String,
    val secret: String,
    val authenticated: Boolean = false,
    val deviceName: String = "",
    val appName: String = "",
)

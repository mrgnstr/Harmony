package com.mrgnstr.harmony.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mrgnstr.harmony.core.database.SongDao
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.mediastore.AudioFormatProbe
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.StreamQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Whether a remote song may be converted by the server (streaming and downloads alike). Conversion downmixes Dolby Atmos and throws
 * its object data away, so by default only the formats on the Transcoding Allowlist are converted; with the allowlist off, everything is.
 */
@Singleton
class TranscodePolicy @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsStore,
    private val probe: AudioFormatProbe,
    private val songDao: SongDao,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** True when [song]'s real format is on the allowlist, or the allowlist is off. A format that can't be told is never converted. */
    suspend fun allows(song: Song): Boolean {
        if (!settings.transcodeAllowlistEnabled.first()) return true
        val format = realFormat(song) ?: return false
        return format in settings.transcodeAllowlist.first()
    }

    /** Lower-case key of the song's real format, or null when it can't be told (an m4a whose header can't be read). */
    suspend fun realFormat(song: Song): String? {
        val tag = song.codec?.takeIf { it.isNotBlank() }
        if (tag != null && !isAmbiguous(tag)) return tag.lowercase()
        val url = song.streamUri ?: return null
        return probe.probeRemote(song.id, url)?.codec?.lowercase()
    }

    /** What [song] streams as on the current network, or null when it streams as the original file. */
    suspend fun streamQuality(song: Song): StreamQuality? {
        val quality = if (onUnmeteredNetwork()) settings.wifiStreamQuality.first() else settings.mobileStreamQuality.first()
        // Lossless is the original; Server Default leaves the choice to the server's own settings.
        if (quality.maxBitRateKbps <= 0) return null
        return quality.takeIf { allows(song) }
    }

    suspend fun streamQuality(remoteId: String): StreamQuality? =
        songDao.byRemoteId(remoteId)?.toModel()?.let { streamQuality(it) }

    private fun onUnmeteredNetwork(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return true
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    companion object {
        /** Navidrome reads an Atmos (E-AC-3 JOC) .m4a as AAC, so these tags can't be trusted. */
        fun isAmbiguous(codec: String) = codec.equals("AAC", true) || codec.equals("ALAC", true)
    }
}

package com.mrgnstr.harmony.core.playback

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mrgnstr.harmony.core.model.RadioStation
import com.mrgnstr.harmony.core.model.ReplayGainInfo
import com.mrgnstr.harmony.core.model.Song

internal const val EXTRA_ALBUM_ID = "harmony.albumId"
/** Set on songs added with Play Next: they make up the queue view's "Queue" section. */
internal const val EXTRA_QUEUED = "harmony.queued"
internal const val EXTRA_CUSTOM_URI = "harmony.customUri"
internal const val EXTRA_REMOTE_ID = "harmony.remoteId"
internal const val EXTRA_SERVER_ID = "harmony.serverId"
internal const val EXTRA_CODEC = "harmony.codec"
internal const val EXTRA_BITRATE = "harmony.bitRate"
internal const val EXTRA_TRACK_GAIN = "harmony.rgTrackGain"
internal const val EXTRA_ALBUM_GAIN = "harmony.rgAlbumGain"
internal const val EXTRA_TRACK_PEAK = "harmony.rgTrackPeak"
internal const val EXTRA_ALBUM_PEAK = "harmony.rgAlbumPeak"

/** mediaId is the Song id as a string, which is how the UI maps player state back to songs. */
fun Song.toMediaItem(): MediaItem {
    val customUri = downloadedPath ?: streamUri
    val resolvedUri = if (!customUri.isNullOrBlank()) Uri.parse(customUri) else uriOf(id)
    val extras = Bundle().apply {
        putLong(EXTRA_ALBUM_ID, albumId)
        if (!customUri.isNullOrBlank()) putString(EXTRA_CUSTOM_URI, customUri)
        if (!remoteId.isNullOrBlank()) putString(EXTRA_REMOTE_ID, remoteId)
        if (serverId > 0L) putLong(EXTRA_SERVER_ID, serverId)
        if (!codec.isNullOrBlank()) putString(EXTRA_CODEC, codec)
        if (bitRateKbps > 0) putInt(EXTRA_BITRATE, bitRateKbps)
        replayGain?.let { rg ->
            rg.trackGainDb?.let { putDouble(EXTRA_TRACK_GAIN, it) }
            rg.albumGainDb?.let { putDouble(EXTRA_ALBUM_GAIN, it) }
            rg.trackPeak?.let { putDouble(EXTRA_TRACK_PEAK, it) }
            rg.albumPeak?.let { putDouble(EXTRA_ALBUM_PEAK, it) }
        }
    }
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(resolvedUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setTrackNumber(trackNumber)
                .setDiscNumber(discNumber)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun RadioStation.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putLong(EXTRA_ALBUM_ID, id)
        putString(EXTRA_CUSTOM_URI, streamUrl)
        putString(EXTRA_REMOTE_ID, remoteId)
        putLong(EXTRA_SERVER_ID, serverId)
        putString(EXTRA_CODEC, "LIVE RADIO")
    }
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(streamUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(homePageUrl ?: "Internet Radio")
                .setAlbumTitle("Internet Radio")
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(extras)
                .build(),
        )
        .build()
}

private fun uriOf(id: Long) = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

/**
 * A copy with its playback URI rebuilt from the custom stream/download URI or MediaStore id, and with
 * the Play Next mark set to [queued] when given.
 */
internal fun MediaItem.rebuilt(queued: Boolean? = null): MediaItem {
    val customUri = mediaMetadata.extras?.getString(EXTRA_CUSTOM_URI)
    val uri = if (!customUri.isNullOrBlank()) Uri.parse(customUri) else uriOf(mediaId.toLongOrNull() ?: 0L)
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(
            if (queued == null) mediaMetadata
            else mediaMetadata.buildUpon().setExtras(Bundle(mediaMetadata.extras ?: Bundle()).apply { putBoolean(EXTRA_QUEUED, queued) }).build(),
        )
        .build()
}

internal val MediaItem.isQueued: Boolean get() = mediaMetadata.extras?.getBoolean(EXTRA_QUEUED) == true

internal fun MediaItem.replayGainInfo(): ReplayGainInfo? {
    val extras = mediaMetadata.extras ?: return null
    val tg = if (extras.containsKey(EXTRA_TRACK_GAIN)) extras.getDouble(EXTRA_TRACK_GAIN) else null
    val ag = if (extras.containsKey(EXTRA_ALBUM_GAIN)) extras.getDouble(EXTRA_ALBUM_GAIN) else null
    val tp = if (extras.containsKey(EXTRA_TRACK_PEAK)) extras.getDouble(EXTRA_TRACK_PEAK) else null
    val ap = if (extras.containsKey(EXTRA_ALBUM_PEAK)) extras.getDouble(EXTRA_ALBUM_PEAK) else null
    if (tg == null && ag == null && tp == null && ap == null) return null
    return ReplayGainInfo(trackGainDb = tg, albumGainDb = ag, trackPeak = tp, albumPeak = ap)
}

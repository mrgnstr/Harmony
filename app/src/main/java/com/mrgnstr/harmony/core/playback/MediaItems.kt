package com.mrgnstr.harmony.core.playback

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mrgnstr.harmony.core.model.Song

internal const val EXTRA_ALBUM_ID = "harmony.albumId"
/** Set on songs added with Play Next: they make up the queue view's "Queue" section. */
internal const val EXTRA_QUEUED = "harmony.queued"

/** mediaId is the MediaStore id as a string, which is how the UI maps player state back to songs. */
fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uriOf(id))
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
            .setExtras(Bundle().apply { putLong(EXTRA_ALBUM_ID, albumId) })
            .build(),
    )
    .build()

private fun uriOf(id: Long) = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

/**
 * A copy with its playback URI rebuilt from the id (items read back from the controller may not carry one), and with
 * the Play Next mark set to [queued] when given.
 */
internal fun MediaItem.rebuilt(queued: Boolean? = null): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(uriOf(mediaId.toLongOrNull() ?: 0L))
    .setMediaMetadata(
        if (queued == null) mediaMetadata
        else mediaMetadata.buildUpon().setExtras(Bundle(mediaMetadata.extras ?: Bundle()).apply { putBoolean(EXTRA_QUEUED, queued) }).build(),
    )
    .build()

internal val MediaItem.isQueued: Boolean get() = mediaMetadata.extras?.getBoolean(EXTRA_QUEUED) == true

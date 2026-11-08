package com.mrgnstr.harmony.core.playback

import androidx.media3.common.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/** What a queue was started from; picks the icon in the queue sheet. Stored by name, anything unknown reads as [Other]. */
enum class QueueSourceKind {
    Album, Artist, Playlist, Favourites, Radio, Other;

    companion object {
        fun from(name: String?): QueueSourceKind = entries.firstOrNull { it.name == name } ?: Other
    }
}

/**
 * Queue facts that live outside the player's item list. Shared by [PlayerConnection] (the UI) and [PlaybackService]
 * (which saves and restores them); both run on the main thread of the one process, so plain fields are enough.
 */
@Singleton
class QueueStore @Inject constructor() {
    /** The album, playlist or list playback was started from ("Continue Playing From ..."). */
    var source: String? = null

    /** What [source] is, so the queue can name it and pick its icon. */
    var sourceKind: QueueSourceKind = QueueSourceKind.Other

    /**
     * Shuffle reorders the songs after the current one for real (instead of Media3's shuffle mode), so the queue shown,
     * the order played and drag-reordering all agree. This is the order to go back to when shuffle is turned off.
     */
    var unshuffled: List<MediaItem>? = null

    /** Set by the service's persister: called when something here changed that the player's own events don't announce. */
    var onChanged: (() -> Unit)? = null
}

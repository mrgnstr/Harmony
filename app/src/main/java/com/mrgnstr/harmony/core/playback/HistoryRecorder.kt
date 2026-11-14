package com.mrgnstr.harmony.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.mrgnstr.harmony.core.database.HistoryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Logs a song into the play history when playback leaves it (it finished, was skipped, or the queue was replaced) after
 * it had played for at least [MinPlayedMs]; the song playing right now is not history yet. The service owns it, so skips
 * from the notification or Bluetooth count too.
 */
internal class HistoryRecorder(
    private val player: Player,
    private val dao: HistoryDao,
    writeScope: CoroutineScope,
    /** True while the queue is changed without a song having been played (a stream swapped for its download). */
    private val isMuted: () -> Boolean = { false },
    private val onScrobble: (suspend (songId: Long, playedAtMs: Long) -> Unit)? = null,
) {
    /** One consumer, so plays are written in the order they happened. */
    private val plays = Channel<Long>(Channel.UNLIMITED)

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (isMuted()) return
            val left = old.mediaItem ?: return
            when (reason) {
                // Repeat-one loops the same song: that is a finished play as well.
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION, Player.DISCONTINUITY_REASON_REMOVE -> Unit
                // Seeking inside the song is not leaving it.
                Player.DISCONTINUITY_REASON_SEEK -> if (old.mediaItemIndex == new.mediaItemIndex) return
                else -> return
            }
            if (old.positionMs >= MinPlayedMs) record(left)
        }

        override fun onPlaybackStateChanged(state: Int) {
            // The last song ending produces no transition.
            if (state == Player.STATE_ENDED) player.currentMediaItem?.let(::record)
        }
    }

    init {
        writeScope.launch {
            for (songId in plays) {
                val now = System.currentTimeMillis()
                dao.record(songId, now, Keep)
                onScrobble?.invoke(songId, now)
            }
        }
        player.addListener(listener)
    }

    private fun record(item: MediaItem) {
        item.mediaId.toLongOrNull()?.let { plays.trySend(it) }
    }

    fun release() {
        player.removeListener(listener)
        plays.close()
    }

    companion object {
        const val MinPlayedMs = 5_000L
        /** Older plays than this many are dropped. */
        const val Keep = 200
    }
}

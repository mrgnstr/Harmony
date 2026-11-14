package com.mrgnstr.harmony.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * What the player does when the connection goes. Without it a stream that runs dry simply stops (ExoPlayer ends in an error and waits).
 *  - **Downloaded copy:** a streamed queue item that has a downloaded file is switched to the file, at the same position, so the song
 *    plays on without a gap in time: the upcoming ones as soon as the device is offline, the current one when it stalls or fails.
 *    (A song queued before it was downloaded keeps its stream address otherwise.)
 *  - **No copy:** after a connection error the position is kept and playback is prepared again when the device is back online (or, if
 *    it is online, after a few growing pauses), as long as the user has not paused or moved on.
 * Main thread only ([scope] must be the player's thread). [localCopy] returns the queue item of a song's downloaded file, if there is one.
 */
internal class ConnectionRecovery(
    private val player: ExoPlayer,
    private val online: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val localCopy: suspend (songId: Long) -> MediaItem?,
    private val retryDelaysMs: List<Long> = DefaultRetryDelaysMs,
) : Player.Listener {

    /**
     * True while this class changes the queue: the history recorder must not count the song that is swapped as played. It stays true until
     * the next turn of the main loop: when a swap runs inside one of the player's own callbacks (a stall, an error), the player holds the
     * "song removed" event back until that callback is done, which is after the swap call has returned.
     */
    val swapping: Boolean get() = mutes > 0
    @Volatile private var mutes = 0

    private fun muteUntilEventsAreDelivered(change: () -> Unit) {
        mutes++
        try {
            change()
        } finally {
            scope.launch(Dispatchers.Main) { mutes-- }
        }
    }

    private var resumeMediaId: String? = null
    private var resumePositionMs = 0L
    private var checkPosition = false
    private var attempts = 0
    private var retryJob: Job? = null

    fun start() {
        player.addListener(this)
        scope.launch { online.collect { onOnline(it) } }
    }

    fun release() {
        player.removeListener(this)
        retryJob?.cancel()
    }

    private fun onOnline(up: Boolean) {
        if (!up) {
            scope.launch {
                swapUpcoming()
                if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) swapCurrent()
            }
        } else if (resumeMediaId != null) {
            attempts = 0
            scheduleRetry(RECONNECT_SETTLE_MS)
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val status = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
        if (!isConnectionError(error.errorCode, status)) return
        val item = player.currentMediaItem ?: return
        resumeMediaId = item.mediaId
        resumePositionMs = player.currentPosition.coerceAtLeast(0L)
        scope.launch {
            if (swapCurrent()) { clearPending(); return@launch }
            if (online.value) scheduleRetry(retryDelaysMs.getOrNull(attempts) ?: run { clearPending(); return@launch })
            // Offline: onOnline(true) takes it from there.
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_READY -> {
                if (resumeMediaId != null && player.currentMediaItem?.mediaId == resumeMediaId) {
                    // A converted stream can come back from its start: put it where it was, once.
                    if (checkPosition && resumePositionMs > 0 && player.currentPosition < resumePositionMs - POSITION_SLACK_MS) {
                        checkPosition = false
                        player.seekTo(resumePositionMs)
                    } else clearPending()
                }
            }
            Player.STATE_BUFFERING -> if (!online.value && player.playWhenReady) scope.launch { swapCurrent() }
            else -> Unit
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem?.mediaId != resumeMediaId) clearPending()
    }

    private fun clearPending() {
        retryJob?.cancel()
        resumeMediaId = null
        checkPosition = false
        attempts = 0
    }

    private fun scheduleRetry(afterMs: Long) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(afterMs)
            retry()
        }
    }

    private fun retry() {
        val id = resumeMediaId ?: return
        // The user paused, moved on, or something else already restarted it: nothing left to do.
        if (player.currentMediaItem?.mediaId != id || !player.playWhenReady || player.playerError == null) { clearPending(); return }
        if (!online.value) return
        attempts++
        checkPosition = true
        player.prepare()
    }

    /** Switches the current item to its downloaded file at the same position. False when there is none (or it is not a stream). */
    private suspend fun swapCurrent(): Boolean {
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return false
        if (!isStream(item)) return false
        val local = localCopy(item.mediaId.toLongOrNull() ?: return false)?.rebuilt(queued = item.isQueued) ?: return false
        if (player.currentMediaItem?.mediaId != item.mediaId || player.currentMediaItemIndex != index) return false
        val position = player.currentPosition.coerceAtLeast(0L)
        muteUntilEventsAreDelivered {
            player.replaceMediaItem(index, local)
            player.seekTo(index, position)
        }
        // After an error the player is idle until it is prepared; the play-when-ready flag it kept makes it play on.
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        return true
    }

    private suspend fun swapUpcoming() {
        var i = 0
        while (i < player.mediaItemCount) {
            val item = player.getMediaItemAt(i)
            if (i != player.currentMediaItemIndex && isStream(item)) {
                val local = localCopy(item.mediaId.toLongOrNull() ?: 0L)?.rebuilt(queued = item.isQueued)
                // The queue may have changed while the lookup ran.
                if (local != null && i < player.mediaItemCount && player.getMediaItemAt(i).mediaId == item.mediaId) {
                    muteUntilEventsAreDelivered { player.replaceMediaItem(i, local) }
                }
            }
            i++
        }
    }

    companion object {
        val DefaultRetryDelaysMs = listOf(1_500L, 3_000L, 6_000L, 12_000L, 20_000L)
        private const val RECONNECT_SETTLE_MS = 500L
        private const val POSITION_SLACK_MS = 3_000L

        /** A server song's stream (not live radio, not a file): the only items that can have a downloaded copy. */
        internal fun isStream(item: MediaItem): Boolean {
            val uri = item.localConfiguration?.uri ?: return false
            return uri.scheme?.startsWith("http") == true && uri.path?.endsWith("/rest/stream") == true
        }

        /** Errors worth waiting out and retrying: the connection failed or timed out, or the server had a hiccup. Not a missing file or a bad format. */
        fun isConnectionError(errorCode: Int, httpStatus: Int): Boolean = when (errorCode) {
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> httpStatus >= 500
            else -> false
        }
    }
}

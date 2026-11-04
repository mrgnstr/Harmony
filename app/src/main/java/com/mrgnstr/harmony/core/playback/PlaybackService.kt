package com.mrgnstr.harmony.core.playback

import android.app.PendingIntent
import android.content.Intent
import com.mrgnstr.harmony.app.MainActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import androidx.media3.session.MediaSessionService
import com.mrgnstr.harmony.core.common.ApplicationScope
import com.mrgnstr.harmony.core.database.HistoryDao
import com.mrgnstr.harmony.core.database.QueueDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Owns the player and the MediaSession. The notification, lock screen, headset and Bluetooth
 * controls all come from the session; the UI only ever talks to it through [PlayerConnection].
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var queueStore: QueueStore
    @Inject lateinit var queueDao: QueueDao
    @Inject lateinit var historyDao: HistoryDao
    @Inject lateinit var queueRestorer: QueueRestorer
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private var session: MediaSession? = null
    private var persister: QueuePersister? = null
    private var history: HistoryRecorder? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** The saved queue has been (or is being) loaded into the player, by the launch restore or by [onPlaybackResumption]. */
    private var restoreHandled = false

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // Tapping the notification (or lock-screen player) opens Now Playing.
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player).setCallback(ResumptionCallback()).setSessionActivity(open).build()
        history = HistoryRecorder(player, historyDao, appScope)
        val persister = QueuePersister(player, queueStore, queueDao, appScope).also { persister = it }
        // Back to where playback stopped, paused. Saving only starts afterwards, so the empty player cannot overwrite the queue.
        scope.launch {
            val saved = if (hasAudioPermission()) runCatching { queueRestorer.load() }.getOrNull() else null
            // The user may have started something before the read finished (or resumption got there first): that wins.
            if (saved != null && !restoreHandled && player.mediaItemCount == 0) {
                restoreHandled = true
                applySaved(saved)
                player.repeatMode = saved.repeatMode
                player.setMediaItems(saved.items, saved.index, saved.positionMs)
                player.prepare()
            }
            persister.arm()
        }
    }

    private fun applySaved(saved: SavedQueue) {
        queueStore.source = saved.source
        queueStore.unshuffled = saved.unshuffled
    }

    /** The system (media button after a reboot, Bluetooth play, resumption UI) asks what to play: the saved queue. */
    private inner class ResumptionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(SET_OUTPUT_ACTION, Bundle.EMPTY))
                        .build(),
                )
                .build()

        /** The output picker: pin playback to one device, or (id -1) back to whatever the system picks. */
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                SET_OUTPUT_ACTION -> Unit
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            val id = args.getInt(EXTRA_OUTPUT_ID, -1)
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val device = if (id < 0) null else audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
            (session.player as? ExoPlayer)?.setPreferredAudioDevice(device)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val player = mediaSession.player
            if (player.mediaItemCount > 0) {
                restoreHandled = true
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        List(player.mediaItemCount) { player.getMediaItemAt(it) }, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0),
                    ),
                )
            }
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                val saved = if (hasAudioPermission()) runCatching { queueRestorer.load() }.getOrNull() else null
                if (saved == null) {
                    result.setException(UnsupportedOperationException("no saved queue"))
                } else {
                    restoreHandled = true
                    applySaved(saved)
                    player.repeatMode = saved.repeatMode
                    result.set(MediaSession.MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs))
                }
            }
            return result
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Swiped away while paused: nothing left to keep alive.
        persister?.flush()
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        persister?.run { flush(); release() }
        persister = null
        history?.release()
        history = null
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

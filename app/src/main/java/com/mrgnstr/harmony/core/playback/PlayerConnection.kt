package com.mrgnstr.harmony.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mrgnstr.harmony.core.model.RadioStation
import com.mrgnstr.harmony.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The session command that pins the player to one output device (see [PlayerConnection.setPreferredOutput]). */
internal const val SET_OUTPUT_ACTION = "harmony.setOutput"
internal const val EXTRA_OUTPUT_ID = "id"
/** What the UI observes. Position is separate ([PlayerConnection.progress]) because it ticks. */
data class PlaybackSnapshot(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val albumId: Long = 0,
    val albumTitle: String = "",
    val isPlaying: Boolean = false,
    /** Wants to play but is waiting for data (a stream, a seek): the play button shows a loading indicator. */
    val isBuffering: Boolean = false,
    val hasMedia: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val codec: String? = null,
    val bitRateKbps: Int = 0,
    val isRemote: Boolean = false,
) {
    val songId: Long? get() = mediaId?.toLongOrNull()
}

/** [speed]: the playback rate (1 normally, [NightcoreSpeed] in nightcore); positions are in the song's own time, which passes [speed] times as fast. */
data class PlaybackProgress(val positionMs: Long = 0, val durationMs: Long = 0, val speed: Float = 1f)

/**
 * Nightcore's speed-up. The genre's template is a 25-30% speed-up and the usual figure is 35%: a 33 1/3 rpm record played at
 * 45 rpm (45 / 33.33 = 1.35), which lifts the pitch by 5.2 semitones. Speed and pitch move together, as on a turntable.
 */
const val NightcoreSpeed = 1.35f

/** [queued]: added with Play Next / Play Last, so it belongs to the "Queue" section rather than the source being played. */
data class QueueItem(val mediaId: String, val title: String, val artist: String, val albumId: Long, val queued: Boolean = false) {
    val songId: Long get() = mediaId.toLongOrNull() ?: 0L
}

enum class RepeatMode { Off, All, One }

/** The whole play order ([items], with [currentIndex] playing), what it was started from ([source]) and the play modes. */
data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val source: String? = null,
    val shuffled: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
) {
    /** What plays after the current song, in order. */
    val upcoming: List<QueueItem> get() = if (currentIndex + 1 < items.size) items.subList(currentIndex + 1, items.size) else emptyList()

    /** How many of [upcoming] are the rest of the source; the Queue section (Play Next / Play Last songs) follows them. */
    val sourceCount: Int get() = upcoming.takeWhile { !it.queued }.size
}

/**
 * App-wide handle on the playback session. Everything here must be called from the main thread
 * (MediaController requirement); callers are ViewModels, which are.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: QueueStore,
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()
    private val _queue = MutableStateFlow(QueueState())
    private val _preferredOutput = MutableStateFlow<Int?>(null)
    /** The output (an AudioDeviceInfo id) the user pinned playback to, or null while the system picks. */
    val preferredOutput: StateFlow<Int?> = _preferredOutput.asStateFlow()
    val queue: StateFlow<QueueState> = _queue.asStateFlow()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
            // Rebuilding the item list is the costly part: only when the list itself changed.
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) publishQueue(player, rebuildItems = true)
            else if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_REPEAT_MODE_CHANGED)) {
                publishQueue(player, rebuildItems = false)
            }
        }
    }

    /** Idempotent. Connecting binds the service but does not start playback. */
    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token)
            .buildAsync()
        future = f
        f.addListener({
            val controller = runCatching { f.get() }.getOrNull() ?: run { future = null; return@addListener }
            this.controller = controller
            controller.addListener(listener)
            // A restarted service has forgotten the pinned output.
            _preferredOutput.value?.let { sendOutput(controller, it) }
            publish(controller)
            publishQueue(controller, rebuildItems = true)
        }, mainExecutor)
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val extras = item?.mediaMetadata?.extras
        _state.value = PlaybackSnapshot(
            mediaId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            albumId = extras?.getLong(EXTRA_ALBUM_ID) ?: 0L,
            albumTitle = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            hasMedia = player.mediaItemCount > 0,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            codec = extras?.getString(EXTRA_CODEC),
            bitRateKbps = extras?.getInt(EXTRA_BITRATE, 0) ?: 0,
            isRemote = (extras?.getLong(EXTRA_SERVER_ID, 0L) ?: 0L) > 0L || (item?.mediaId?.toLongOrNull() ?: 0L) < 0L,
        )
    }

    private fun publishQueue(player: Player, rebuildItems: Boolean) {
        val items = if (rebuildItems) List(player.mediaItemCount) { i ->
            val item = player.getMediaItemAt(i)
            QueueItem(
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                albumId = item.mediaMetadata.extras?.getLong(EXTRA_ALBUM_ID) ?: 0L,
                queued = item.isQueued,
            )
        } else _queue.value.items
        _queue.value = QueueState(
            items = items,
            currentIndex = player.currentMediaItemIndex,
            source = store.source,
            shuffled = store.unshuffled != null,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                else -> RepeatMode.Off
            },
        )
    }

    private fun sendOutput(c: MediaController, id: Int?) {
        c.sendCustomCommand(SessionCommand(SET_OUTPUT_ACTION, Bundle.EMPTY), Bundle().apply { putInt(EXTRA_OUTPUT_ID, id ?: -1) })
    }

    /** Sends playback to the output with this AudioDeviceInfo [id]; null hands the choice back to the system. */
    fun setPreferredOutput(id: Int?) {
        _preferredOutput.value = id
        withController { sendOutput(it, id) }
    }

    private fun withController(block: (MediaController) -> Unit) {
        connect()
        val f = future ?: return
        f.addListener({ runCatching { f.get() }.getOrNull()?.let(block) }, mainExecutor)
    }

    /** Replaces the queue with [songs] and starts at [startIndex]; [source] names what they were played from. */
    fun playQueue(songs: List<Song>, startIndex: Int, source: String? = null) {
        if (songs.isEmpty()) return
        val items: List<MediaItem> = songs.map { it.toMediaItem() }
        store.unshuffled = null
        store.source = source
        withController {
            it.setMediaItems(items, startIndex.coerceIn(items.indices), 0L)
            it.prepare()
            it.play()
        }
    }

    /** Starts streaming a live Internet Radio station. */
    fun playRadio(station: RadioStation) {
        val item = station.toMediaItem()
        store.unshuffled = null
        store.source = station.name
        withController {
            it.setMediaItems(listOf(item), 0, 0L)
            it.prepare()
            it.play()
        }
    }

    /** Position ticks only while somebody collects: 4 Hz while playing, 1 Hz otherwise. Collect on the main thread. */
    fun progress(): Flow<PlaybackProgress> = flow {
        while (true) {
            controller?.let { emit(PlaybackProgress(it.currentPosition.coerceAtLeast(0), it.duration.coerceAtLeast(0), it.playbackParameters.speed)) }
            delay(if (_state.value.isPlaying) 250 else 1000)
        }
    }.distinctUntilChanged()

    /** The position right now, from the player itself when this process has it (the controller's is stepped by the session every 3 s); on the main thread. */
    fun currentPositionMs(): Long = (PlaybackService.livePlayer ?: controller)?.currentPosition?.coerceAtLeast(0) ?: 0L

    // Pressing while it buffers pauses (it is "playing" as far as the user is concerned).
    fun togglePlayPause() = withController { if (it.isPlaying || it.playWhenReady && it.playbackState == Player.STATE_BUFFERING) it.pause() else it.play() }
    fun skipToNext() = withController { it.seekToNextMediaItem() }
    fun skipToPrevious() = withController { it.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun skipToQueueIndex(index: Int) = withController { if (index in 0 until it.mediaItemCount) it.seekTo(index, 0L) }

    /** Moves a song within the play order ([from] and [to] are indices into the whole queue). */
    fun moveQueueItem(from: Int, to: Int) = withController {
        if (from != to && from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to)
    }

    /**
     * Where the rest of the source (album, playlist...) ends after the current song: the Queue section starts here. The
     * queue plays after what is already playing, so Play Next / Play Last never cut into an album.
     */
    private fun sourceEnd(c: Player): Int {
        var i = c.currentMediaItemIndex + 1
        while (i < c.mediaItemCount && !c.getMediaItemAt(i).isQueued) i++
        return i
    }

    /**
     * Plays a song from the history right now. It slots in after the current song (which becomes history itself), so the
     * rest of what was playing carries on after it.
     */
    fun playFromHistory(song: Song) = withController { c ->
        val item = song.toMediaItem()
        if (c.mediaItemCount == 0) {
            store.source = null
            c.setMediaItems(listOf(item)); c.prepare(); c.play()
        } else {
            val at = c.currentMediaItemIndex + 1
            c.addMediaItem(at, item)
            c.seekTo(at, 0L)
            c.play()
        }
    }

    /** Adds [songs] at the top of the "Queue" section, right after the rest of the source (Play Next). */
    fun playNext(songs: List<Song>) = addQueued(songs, ::sourceEnd)

    /** Adds [songs] at the bottom of the "Queue" section, after everything else (Play Last). */
    fun playLast(songs: List<Song>) = addQueued(songs) { it.mediaItemCount }

    private fun addQueued(songs: List<Song>, at: (Player) -> Int) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem().rebuilt(queued = true) }
        withController { c ->
            // Nothing loaded yet: the songs simply start playing.
            if (c.mediaItemCount == 0) {
                store.source = null
                c.setMediaItems(items); c.prepare(); c.play()
            } else c.addMediaItems(at(c), items)
        }
    }

    /** Removes one song from the play order ([index] into the whole queue). */
    fun removeQueueItem(index: Int) = withController { c ->
        if (index !in 0 until c.mediaItemCount || index == c.currentMediaItemIndex) return@withController
        val id = c.getMediaItemAt(index).mediaId
        c.removeMediaItem(index)
        // Turning shuffle off later must not bring it back: drop its first occurrence after the current song.
        store.unshuffled = store.unshuffled?.toMutableList()?.apply {
            val current = indexOfFirst { it.mediaId == c.currentMediaItem?.mediaId }
            val at = (current + 1 until size).firstOrNull { this[it].mediaId == id } ?: indexOfFirst { it.mediaId == id }
            if (at >= 0) removeAt(at)
        }
    }

    /** Stops playback and empties the whole queue. The service's persister also forgets the saved one. */
    fun clearAll() = withController { c ->
        c.pause()
        c.clearMediaItems()
        c.stop()
        store.source = null
        store.unshuffled = null
        publishQueue(c, rebuildItems = true)
    }

    /** Removes the Queue section (the Play Next / Play Last songs after the rest of the source). */
    fun clearQueued() = withController { c ->
        val start = sourceEnd(c)
        if (start < c.mediaItemCount) c.removeMediaItems(start, c.mediaItemCount)
    }

    fun toggleShuffle() = withController { c ->
        val current = c.currentMediaItemIndex
        val count = c.mediaItemCount
        if (count == 0) return@withController
        val original = store.unshuffled
        // Only the rest of the source is shuffled; the Queue section after it keeps its order (as in the Music app).
        val from = current + 1
        val end = sourceEnd(c)
        if (original == null) {
            store.unshuffled = List(count) { c.getMediaItemAt(it).rebuilt() }
            if (end - from > 1) c.replaceMediaItems(from, end, List(end - from) { c.getMediaItemAt(from + it).rebuilt() }.shuffled())
        } else {
            store.unshuffled = null
            // Back to the original order around the current song: what followed it originally follows it again.
            val at = original.indexOfFirst { it.mediaId == c.currentMediaItem?.mediaId }
            if (at >= 0) {
                c.replaceMediaItems(from, end, original.drop(at + 1).filterNot { it.isQueued })
                c.replaceMediaItems(0, current, original.take(at))
            }
        }
        publishQueue(c, rebuildItems = true)
        // Shuffling a list with nothing to reorder changes no timeline, but the saved shuffle flag still differs.
        store.onChanged?.invoke()
    }

    /** Off → all → one → off, like the Music app's repeat button. */
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

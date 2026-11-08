package com.mrgnstr.harmony.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.mrgnstr.harmony.core.database.QueueDao
import com.mrgnstr.harmony.core.database.QueueItemEntity
import com.mrgnstr.harmony.core.database.QueueStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Writes the player's queue and position to Room as they change, so the next launch (or a reboot) can pick up where it
 * stopped. Lives in the service because only the service sees every change (notification, Bluetooth and lock screen
 * skips included). Everything that touches the [Player] runs on the main thread; only the snapshots go to [writeScope].
 */
internal class QueuePersister(
    private val player: Player,
    private val store: QueueStore,
    private val dao: QueueDao,
    private val writeScope: CoroutineScope,
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Snapshots are taken in order on the main thread, and the lock keeps their writes in the same order. */
    private val writeLock = Mutex()
    private var itemsJob: Job? = null
    private var stateJob: Job? = null
    private var armed = false
    /** The player has held songs since saving started: an empty player after that means the queue was cleared. */
    private var hadItems = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!armed) return
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) scheduleItems()
            else if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) scheduleState()
        }
    }

    init {
        player.addListener(listener)
        store.onChanged = { if (armed) scheduleItems() }
        mainScope.launch {
            // A hard kill then loses at most this much of the position.
            while (true) {
                delay(PositionSaveMs)
                if (armed && player.isPlaying) scheduleState()
            }
        }
    }

    /** Saving starts here: until the saved queue has been restored, an empty player must not overwrite it. */
    fun arm() {
        armed = true
        hadItems = player.mediaItemCount > 0
    }

    private fun scheduleItems() {
        itemsJob?.cancel()
        itemsJob = mainScope.launch {
            delay(ItemsDebounceMs)
            saveNow(items = true)
        }
    }

    private fun scheduleState() {
        if (itemsJob?.isActive == true) return // the pending items save writes the state too
        stateJob?.cancel()
        stateJob = mainScope.launch {
            delay(StateDebounceMs)
            saveNow(items = false)
        }
    }

    /** Snapshots now and writes in the background. Used at the end of the service, where nothing may wait for a debounce. */
    fun flush() {
        itemsJob?.cancel(); stateJob?.cancel()
        if (armed) saveNow(items = true)
    }

    fun release() {
        player.removeListener(listener)
        store.onChanged = null
        mainScope.cancel()
    }

    private fun saveNow(items: Boolean) {
        if (player.mediaItemCount == 0) {
            // Cleared (the mini player was swiped away): the saved queue goes too. An empty player that never held songs just
            // means nothing was loaded (no permission, or a first run), and must leave what is saved alone.
            if (hadItems) {
                hadItems = false
                writeScope.launch { writeLock.withLock { dao.clearAll() } }
            }
            return
        }
        hadItems = true
        val state = QueueStateEntity(
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition.coerceAtLeast(0),
            repeatMode = player.repeatMode,
            shuffled = store.unshuffled != null,
            source = store.source,
            sourceKind = store.sourceKind.name,
            updatedAtMs = System.currentTimeMillis(),
        )
        val play = if (items) List(player.mediaItemCount) { player.getMediaItemAt(it) }.toRows(QueueItemEntity.LIST_PLAY) else null
        val original = if (items) store.unshuffled.orEmpty().toRows(QueueItemEntity.LIST_UNSHUFFLED) else null
        writeScope.launch {
            writeLock.withLock {
                if (play != null) dao.replaceItems(QueueItemEntity.LIST_PLAY, play)
                if (original != null) dao.replaceItems(QueueItemEntity.LIST_UNSHUFFLED, original)
                dao.saveState(state)
            }
        }
    }

    private fun List<MediaItem>.toRows(list: Int) =
        mapIndexed { i, item -> QueueItemEntity(list, i, item.mediaId.toLongOrNull() ?: 0L, item.isQueued) }

    private companion object {
        const val ItemsDebounceMs = 500L
        const val StateDebounceMs = 300L
        const val PositionSaveMs = 10_000L
    }
}

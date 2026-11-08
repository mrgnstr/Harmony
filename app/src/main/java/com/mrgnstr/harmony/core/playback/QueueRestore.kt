package com.mrgnstr.harmony.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.mrgnstr.harmony.core.database.QueueDao
import com.mrgnstr.harmony.core.database.QueueItemEntity
import com.mrgnstr.harmony.core.database.QueueRestoreRow
import com.mrgnstr.harmony.core.database.toModel
import javax.inject.Inject
import javax.inject.Singleton

/** A saved queue turned back into player items. [index] and [positionMs] say where to resume. */
class SavedQueue(
    val items: List<MediaItem>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val source: String?,
    val sourceKind: QueueSourceKind,
    /** The order shuffle goes back to, or null when the queue was not shuffled. */
    val unshuffled: List<MediaItem>?,
)

/** Where to resume in a queue that lost songs: [index] into the surviving items, and whether the saved position still applies. */
internal data class ResumePoint(val index: Int, val keepPosition: Boolean)

/**
 * [survivingPositions] are the saved positions of the songs still in the library, ascending. The song that was playing resumes
 * where it stopped; if it is gone, the next surviving song starts from the beginning (or the last one, when none follows).
 */
internal fun resumePoint(savedIndex: Int, survivingPositions: List<Int>): ResumePoint? {
    if (survivingPositions.isEmpty()) return null
    val exact = survivingPositions.indexOf(savedIndex)
    if (exact >= 0) return ResumePoint(exact, keepPosition = true)
    val next = survivingPositions.indexOfFirst { it > savedIndex }
    return ResumePoint(if (next >= 0) next else survivingPositions.lastIndex, keepPosition = false)
}

/** Reads the queue [QueuePersister] saved. Songs that left the library since are dropped. */
@Singleton
class QueueRestorer @Inject constructor(private val dao: QueueDao) {
    suspend fun load(): SavedQueue? {
        val state = dao.state() ?: return null
        val rows = dao.restoreRows(QueueItemEntity.LIST_PLAY)
        val point = resumePoint(state.currentIndex, rows.map { it.position }) ?: return null
        val unshuffled = if (state.shuffled) dao.restoreRows(QueueItemEntity.LIST_UNSHUFFLED).takeIf { it.isNotEmpty() } else null
        return SavedQueue(
            items = rows.map { it.toItem() },
            index = point.index,
            positionMs = if (point.keepPosition) state.positionMs else 0L,
            repeatMode = state.repeatMode.takeIf { it in RepeatModes } ?: Player.REPEAT_MODE_OFF,
            source = state.source,
            sourceKind = QueueSourceKind.from(state.sourceKind),
            unshuffled = unshuffled?.map { it.toItem() },
        )
    }

    private fun QueueRestoreRow.toItem(): MediaItem = song.toModel().toMediaItem().let { if (queued) it.rebuilt(queued = true) else it }

    private companion object {
        val RepeatModes = setOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL)
    }
}

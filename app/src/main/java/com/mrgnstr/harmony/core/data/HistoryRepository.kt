package com.mrgnstr.harmony.core.data

import com.mrgnstr.harmony.core.database.HistoryDao
import com.mrgnstr.harmony.core.database.PlayCount
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** One play in the history; [id] identifies the entry (the same song can appear many times). */
data class HistoryEntry(val id: Long, val song: Song)

@Singleton
class HistoryRepository @Inject constructor(private val dao: HistoryDao) {
    /** Oldest first, so the newest play sits right above what is playing now. */
    val entries: Flow<List<HistoryEntry>> = dao.observeLatest(Limit)
        .map { rows -> rows.asReversed().map { HistoryEntry(it.entryId, it.song.toModel()) } }
        .flowOn(Dispatchers.Default)

    /** Lifetime plays per song id. */
    val playCounts: Flow<Map<Long, Int>> = dao.observePlayCounts()
        .map { rows -> rows.associate { it.songId to it.count } }
        .flowOn(Dispatchers.Default)

    /** Lifetime plays and the time of the latest one, per song id. */
    val playStats: Flow<Map<Long, PlayCount>> = dao.observePlayCounts()
        .map { rows -> rows.associateBy { it.songId } }
        .flowOn(Dispatchers.Default)

    suspend fun remove(id: Long) = dao.delete(id)

    private companion object {
        /** Matches what the recorder keeps. */
        const val Limit = 200
    }
}

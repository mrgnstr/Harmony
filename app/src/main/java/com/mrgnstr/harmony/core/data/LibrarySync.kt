package com.mrgnstr.harmony.core.data

import android.util.Log
import androidx.room.withTransaction
import com.mrgnstr.harmony.core.common.ApplicationScope
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.SongDao
import com.mrgnstr.harmony.core.database.toEntity
import com.mrgnstr.harmony.core.mediastore.MediaStoreObserver
import com.mrgnstr.harmony.core.mediastore.MediaStoreScanner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [generation] increases after every successful sync, so observers can react to "library may have changed". */
data class SyncState(val syncing: Boolean = false, val completedOnce: Boolean = false, val generation: Int = 0, val failed: Boolean = false)

/** Keeps Room in step with MediaStore: one sync at launch, then one (debounced) per MediaStore change. */
@Singleton
class LibrarySync @Inject constructor(
    private val scanner: MediaStoreScanner,
    private val observer: MediaStoreObserver,
    private val db: HarmonyDatabase,
    private val dao: SongDao,
    private val settings: SettingsStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var job: Job? = null
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Idempotent. Call only once audio permission is granted. */
    @OptIn(FlowPreview::class)
    fun startObserving() {
        if (job?.isActive == true) return
        job = scope.launch {
            // Debounce first, then onStart, so the initial sync is not delayed.
            combine(
                observer.changes().debounce(DEBOUNCE_MS).onStart { emit(Unit) },
                settings.libraryFilters.distinctUntilChanged(),
            ) { _, _ -> Unit }.collect { syncNow() }
        }
    }

    suspend fun syncNow() = mutex.withLock {
        _state.update { it.copy(syncing = true, failed = false) }
        try {
            val filters = settings.libraryFilters.first()
            val scanned = scanner.scan(filters.minimumDurationMs, filters.excludedFolders)
            val diff = SyncDiff.compute(dao.stamps(), scanned)
            if (!diff.isEmpty) {
                db.withTransaction {
                    dao.upsert(diff.upserts.map { it.toEntity() })
                    // Old SQLite (API 25) caps bound variables at 999.
                    diff.deletedIds.chunked(DELETE_CHUNK).forEach { dao.deleteByIds(it) }
                }
            }
            Log.i(TAG, "synced: ${scanned.size} scanned, +${diff.upserts.size} -${diff.deletedIds.size}")
            _state.update { SyncState(syncing = false, completedOnce = true, generation = it.generation + 1) }
        } catch (e: CancellationException) {
            _state.update { it.copy(syncing = false) }
            throw e
        } catch (e: Exception) {
            // Keep whatever Room has; the next MediaStore change retries.
            Log.w(TAG, "sync failed", e)
            _state.update { it.copy(syncing = false, failed = true) }
        }
    }

    private companion object {
        const val TAG = "LibrarySync"
        const val DEBOUNCE_MS = 2_000L
        const val DELETE_CHUNK = 500
    }
}

package com.mrgnstr.harmony.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.data.LibrarySync
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts library syncing, the playback connection and palette precompute once the permission-gated UI is shown. */
@HiltViewModel
class AppViewModel @Inject constructor(
    sync: LibrarySync,
    player: PlayerConnection,
    colors: ArtworkColorStore,
) : ViewModel() {
    init {
        sync.startObserving()
        player.connect()
        // After every successful sync, give any new album its colours (low priority, off the main thread).
        viewModelScope.launch {
            sync.state.filter { it.completedOnce && !it.syncing }.map { it.generation }.distinctUntilChanged()
                .collect { colors.precomputeMissing() }
        }
    }
}

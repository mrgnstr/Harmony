package com.mrgnstr.harmony.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.database.AlbumKey
import com.mrgnstr.harmony.core.debug.DebugOptions
import com.mrgnstr.harmony.core.designsystem.components.LocalOfflineDim
import com.mrgnstr.harmony.core.designsystem.components.OfflineDim
import com.mrgnstr.harmony.core.device.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class OfflineViewModel @Inject constructor(network: NetworkMonitor, library: LibraryRepository) : ViewModel() {
    val online: StateFlow<Boolean> = network.online
    val playable: StateFlow<Set<AlbumKey>> = library.playableOffline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}

/** Tells every tile and row what can't be played without a connection (see [OfflineDim]); online it provides the no-op default. */
@Composable
fun ProvideOfflineDim(content: @Composable () -> Unit) {
    val vm: OfflineViewModel = hiltViewModel()
    val online = vm.online.collectAsStateWithLifecycle().value && !DebugOptions.offline.on
    // The set is only read while offline, so it isn't observed (and nothing recomposes for it) while the network is fine.
    val dim = if (online) OfflineDim.None else {
        val playable by vm.playable.collectAsStateWithLifecycle()
        remember(playable) { OfflineDim(true, playable) }
    }
    CompositionLocalProvider(LocalOfflineDim provides dim, content = content)
}

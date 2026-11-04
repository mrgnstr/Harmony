package com.mrgnstr.harmony.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryRepository,
    private val player: PlayerConnection,
) : ViewModel() {
    val songs: StateFlow<List<Song>> = library.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(songs: List<Song>, index: Int) = player.playQueue(songs, index, source = "Library")
}

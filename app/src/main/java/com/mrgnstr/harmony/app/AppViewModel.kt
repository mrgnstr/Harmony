package com.mrgnstr.harmony.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.data.LibrarySync
import com.mrgnstr.harmony.core.data.LyricsRepository
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts library syncing, Navidrome hooks, the playback connection and palette precompute once the UI is shown. */
@HiltViewModel
class AppViewModel @Inject constructor(
    sync: LibrarySync,
    navidrome: NavidromeRepository,
    favourites: FavouritesRepository,
    lyrics: LyricsRepository,
    player: PlayerConnection,
    colors: ArtworkColorStore,
) : ViewModel() {
    init {
        favourites.onSongStarChanged = navidrome::syncStarSong
        favourites.onAlbumStarChanged = navidrome::syncStarAlbum
        favourites.onArtistStarChanged = navidrome::syncStarArtist
        lyrics.remoteLyricsFetcher = navidrome::fetchRemoteLyrics

        sync.startObserving()
        player.connect()
        // After every successful sync, give any new album its colours (low priority, off the main thread).
        viewModelScope.launch {
            sync.state.filter { it.completedOnce && !it.syncing }.map { it.generation }.distinctUntilChanged()
                .collect { colors.precomputeMissing() }
        }
    }
}

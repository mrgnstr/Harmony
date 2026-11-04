package com.mrgnstr.harmony.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.artwork.CoverStripCache
import com.mrgnstr.harmony.core.artwork.CoverStripSpec
import com.mrgnstr.harmony.core.artwork.CoverStrips
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.playback.PlaybackProgress
import com.mrgnstr.harmony.core.playback.PlaybackSnapshot
import com.mrgnstr.harmony.core.playback.PlayerConnection
import com.mrgnstr.harmony.core.playback.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val player: PlayerConnection,
    private val stripCache: CoverStripCache,
    private val favourites: FavouritesRepository,
    private val colorStore: ArtworkColorStore,
) : ViewModel() {
    val snapshot: StateFlow<PlaybackSnapshot> = player.state
    val queue: StateFlow<QueueState> = player.queue

    /** Ticks only while the full player is on screen. */
    val progress: StateFlow<PlaybackProgress> = player.progress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), PlaybackProgress())

    val isFavourite: StateFlow<Boolean> =
        combine(favourites.songIds, player.state.map { it.songId }.distinctUntilChanged()) { ids, id -> id != null && id in ids }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The controls' colours, from the cover's palette; re-resolved when the album changes, not on every song within one album. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val coverColors: StateFlow<CoverColors> = player.state
        .map { (it.songId ?: 0L) to it.albumId }
        .distinctUntilChanged { a, b -> a.second == b.second && a.second > 0 }
        .mapLatest { (songId, albumId) ->
            if (songId == 0L) DefaultCoverColors
            else colorStore.coverSample(songId, albumId)?.let { coverColorsOf(it.dominant, it.bottomLuminance) } ?: DefaultCoverColors
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultCoverColors)

    fun toggleFavourite() { player.state.value.songId?.let { id -> viewModelScope.launch { favourites.toggleSong(id) } } }

    /** The pre-blurred strips around the cover (where the live shader is off); computed off the main thread and cached. */
    suspend fun coverStrips(spec: CoverStripSpec): CoverStrips? = stripCache.get(spec)

    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.skipToNext()
    fun previous() = player.skipToPrevious()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
}

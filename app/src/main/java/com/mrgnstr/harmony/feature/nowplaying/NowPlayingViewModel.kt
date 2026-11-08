package com.mrgnstr.harmony.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.artwork.CoverStripCache
import com.mrgnstr.harmony.core.artwork.CoverStripSpec
import com.mrgnstr.harmony.core.artwork.CoverStrips
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.data.LyricsRepository
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.mediastore.AudioFormatProbe
import com.mrgnstr.harmony.core.model.AudioBookmark
import com.mrgnstr.harmony.core.model.AudioFormat
import com.mrgnstr.harmony.core.model.FormatTag
import com.mrgnstr.harmony.core.model.JukeboxState
import com.mrgnstr.harmony.core.model.Lyrics
import com.mrgnstr.harmony.core.playback.NightcoreSpeed
import com.mrgnstr.harmony.core.playback.PlaybackProgress
import com.mrgnstr.harmony.core.playback.PlaybackSnapshot
import com.mrgnstr.harmony.core.playback.PlayerConnection
import com.mrgnstr.harmony.core.playback.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val lyricsRepository: LyricsRepository,
    private val diffusedArt: com.mrgnstr.harmony.core.artwork.DiffusedArtCache,
    private val libraryRepository: LibraryRepository,
    private val formatProbe: AudioFormatProbe,
    private val settings: com.mrgnstr.harmony.core.datastore.SettingsStore,
    val navidrome: NavidromeRepository,
) : ViewModel() {
    val snapshot: StateFlow<PlaybackSnapshot> = player.state
    val queue: StateFlow<QueueState> = player.queue
    val jukeboxState: StateFlow<JukeboxState> = navidrome.jukeboxState
    val hasNavidrome: StateFlow<Boolean> = navidrome.activeAccount.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val hasJukeboxRole: StateFlow<Boolean> = navidrome.activeAccount.map { it?.jukeboxRole == true || it?.isAdmin == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val nightcore: StateFlow<Boolean> = settings.nightcore.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val nightcoreSpeed: StateFlow<Float> = settings.nightcoreSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightcoreSpeed)
    /** Moving the slider switches nightcore on. */
    fun setNightcoreSpeed(speed: Float) {
        viewModelScope.launch {
            settings.setNightcoreSpeed(speed)
            if (!nightcore.value) settings.setNightcore(true)
        }
    }
    fun toggleNightcore() { viewModelScope.launch { settings.setNightcore(!nightcore.value) } }

    /** Ticks only while the full player is on screen. */
    val progress: StateFlow<PlaybackProgress> = player.progress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), PlaybackProgress())

    val isFavourite: StateFlow<Boolean> =
        combine(favourites.songIds, player.state.map { it.songId }.distinctUntilChanged()) { ids, id -> id != null && id in ids }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The playing song's format pill: probed from the file for local songs, from the server's metadata for remote ones. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val format: StateFlow<FormatTag?> = player.state
        .map { it.songId }
        .distinctUntilChanged()
        .mapLatest { id ->
            when {
                id == null -> null
                id > 0 -> formatProbe.probe(id)
                else -> libraryRepository.songs.first().firstOrNull { it.id == id }?.let { s ->
                    s.codec?.let { AudioFormat(it, s.lossless, s.sampleRateHz, s.bitDepth, s.channels, s.bitRateKbps) }
                }
            }?.pill
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Bumped when the lyrics should be looked for again (file access may have been granted). */
    private val lyricsRefresh = MutableStateFlow(0)
    private val _fileAccess = MutableStateFlow(lyricsRepository.hasFileAccess())
    /** Whether the lyric files beside local songs can be read. */
    val lyricsFileAccess: StateFlow<Boolean> = _fileAccess

    fun recheckFileAccess() {
        val now = lyricsRepository.hasFileAccess()
        if (now != _fileAccess.value) { _fileAccess.value = now; lyricsRefresh.value++ }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<Lyrics?> = combine(player.state.map { it.songId }.distinctUntilChanged(), lyricsRefresh) { songId, _ -> songId }
        .mapLatest { songId -> if (songId != null) lyricsRepository.lyricsFor(songId) else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The cover heavily blurred, small: the lyrics' background where the live blur is off. */
    suspend fun diffusedCover(songId: Long, albumId: Long) = diffusedArt.get(songId, albumId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentBookmark: StateFlow<AudioBookmark?> = player.state
        .map { it.songId }
        .distinctUntilChanged()
        .flatMapLatest { songId -> if (songId != null) navidrome.observeBookmarkForSong(songId) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The controls' colours, from the cover's palette; re-resolved when the album changes, not on every song within one album. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val coverColors: StateFlow<CoverColors> = player.state
        .map { (it.songId ?: 0L) to it.albumId }
        .distinctUntilChanged { a, b -> a.second == b.second && a.second > 0 }
        .mapLatest { (songId, albumId) ->
            if (songId == 0L) DefaultCoverColors
            else colorStore.coverSample(songId, albumId)?.let { coverColorsOf(it.dominant, it.bottomLuminance, it.topLuminance) } ?: DefaultCoverColors
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultCoverColors)

    /** The playing song's artist credit split into artists (a collaboration names several); the artist link asks which when there are. */
    val artists: StateFlow<List<String>> = combine(player.state.map { it.artist }.distinctUntilChanged(), libraryRepository.artistIndex) { credit, index ->
        if (credit.isBlank()) emptyList() else index.artistsOf(credit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The playing position at this instant (for per-frame use on the main thread: the lyrics). */
    fun positionNow(): Long = player.currentPositionMs()

    fun toggleFavourite() { player.state.value.songId?.let { id -> viewModelScope.launch { favourites.toggleSong(id) } } }

    /** The pre-blurred strips around the cover (where the live shader is off); computed off the main thread and cached. */
    suspend fun coverStrips(spec: CoverStripSpec): CoverStrips? = stripCache.get(spec)

    /** The mini player was swiped away: stops and empties the queue. */
    fun clearAll() = player.clearAll()
    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.skipToNext()
    fun previous() = player.skipToPrevious()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun skipToQueueIndex(index: Int) = player.skipToQueueIndex(index)
    fun removeQueueItem(index: Int) = player.removeQueueItem(index)

    fun saveCloudQueue() {
        val q = queue.value
        val pos = progress.value.positionMs
        viewModelScope.launch {
            navidrome.saveCloudQueue(q.items.map { it.songId }, q.currentIndex, pos)
        }
    }

    fun loadCloudQueue() {
        viewModelScope.launch {
            val remote = navidrome.loadCloudQueue() ?: return@launch
            if (remote.songs.isNotEmpty()) {
                player.playQueue(remote.songs, remote.currentIndex, source = "Navidrome Queue")
                if (remote.positionMs > 0L) player.seekTo(remote.positionMs)
            }
        }
    }

    fun insertSonicJourneyTo(targetMediaId: String) {
        val currentId = snapshot.value.songId ?: return
        val targetId = targetMediaId.toLongOrNull() ?: return
        viewModelScope.launch {
            val allSongs = libraryRepository.songs.first()
            val startSong = allSongs.firstOrNull { it.id == currentId } ?: return@launch
            val endSong = allSongs.firstOrNull { it.id == targetId } ?: return@launch
            val path = navidrome.findSonicJourney(startSong, endSong)
            if (path.isNotEmpty()) {
                player.playNext(path.map { it.song })
            }
        }
    }

    fun saveBookmark(note: String) {
        val songId = snapshot.value.songId ?: return
        val pos = progress.value.positionMs
        viewModelScope.launch {
            val song = libraryRepository.songs.first().firstOrNull { it.id == songId } ?: return@launch
            navidrome.saveBookmark(song, pos, note)
        }
    }

    fun deleteBookmark() {
        val songId = snapshot.value.songId ?: return
        viewModelScope.launch { navidrome.deleteBookmark(songId) }
    }

    fun toggleJukebox(active: Boolean) {
        viewModelScope.launch {
            navidrome.setJukeboxActive(active)
            if (active) {
                val ids = queue.value.items.map { it.songId }
                val allSongs = libraryRepository.songs.first().associateBy { it.id }
                val songs = ids.mapNotNull { allSongs[it] }
                if (songs.isNotEmpty()) navidrome.controlJukebox("set", songs = songs)
            }
        }
    }

    fun sendJukeboxCommand(action: String, gain: Float? = null) {
        viewModelScope.launch { navidrome.controlJukebox(action, gain = gain) }
    }
}

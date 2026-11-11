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
import com.mrgnstr.harmony.core.model.AudioFormat
import com.mrgnstr.harmony.core.model.FormatTag
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
import kotlinx.coroutines.flow.Flow
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
    private val transcodePolicy: com.mrgnstr.harmony.core.data.TranscodePolicy,
    private val settings: com.mrgnstr.harmony.core.datastore.SettingsStore,
    val navidrome: NavidromeRepository,
) : ViewModel() {
    val snapshot: StateFlow<PlaybackSnapshot> = player.state
    val queue: StateFlow<QueueState> = player.queue

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
                    // Navidrome mis-reads Atmos (E-AC-3 JOC) .m4a as stereo AAC: the stream's own header decides for the MP4 family.
                    val stream = s.streamUri?.takeIf { s.codec == "AAC" || s.codec == "ALAC" }
                    val probed = stream?.let { formatProbe.probeRemote(s.id, it) }?.let { f -> if (f.bitRateKbps > 0) f else f.copy(bitRateKbps = s.bitRateKbps) }
                    // A stream converted by the server is what plays, not the file's own format (a downloaded copy is already what it is).
                    val converted = if (s.downloadedPath == null) transcodePolicy.streamQuality(s) else null
                    if (converted != null) AudioFormat(AudioFormat.codecName("audio/${converted.preferredFormat}"), false, 0, 0, 2, converted.maxBitRateKbps)
                    else probed ?: s.codec?.let { AudioFormat(it, s.lossless, s.sampleRateHz, s.bitDepth, s.channels, s.bitRateKbps) }
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
    val lyrics: StateFlow<Lyrics?> = combine(player.state.map { it.songId }.distinctUntilChanged(), lyricsRefresh, settings.lyricsPriority) { songId, _, priority -> songId to priority }
        .mapLatest { (songId, priority) -> if (songId != null) lyricsRepository.lyricsFor(songId, priority) else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The cover heavily blurred, small: the lyrics' background where the live blur is off. */
    suspend fun diffusedCover(songId: Long, albumId: Long) = diffusedArt.get(songId, albumId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coverColorPair: Flow<Pair<CoverColors, CoverColors>> = player.state
        .map { (it.songId ?: 0L) to it.albumId }
        .distinctUntilChanged { a, b -> a.second == b.second && a.second > 0 }
        .mapLatest { (songId, albumId) ->
            val sample = if (songId == 0L) null else colorStore.coverSample(songId, albumId)
            if (sample == null) DefaultCoverColors to DefaultCoverColors
            else coverColorsOf(sample.dominant, sample.bottomLuminance, sample.topLuminance) to
                coverColorsOf(sample.dominant, sample.bottomLuminance, sample.topLuminance, alwaysDark = true)
        }

    /** The controls' colours, from the cover's palette; re-resolved when the album changes, not on every song within one album. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val coverColors: StateFlow<CoverColors> = coverColorPair.map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultCoverColors)

    /** The same, for the "always dark" mode (used when the setting is on). */
    val darkCoverColors: StateFlow<CoverColors> = coverColorPair.map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultCoverColors)

    val collapseBarsOnScroll: StateFlow<Boolean> = settings.collapseBarsOnScroll
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showAudioInfo: StateFlow<Boolean> = settings.showAudioInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val alwaysDark: StateFlow<Boolean> = settings.nowPlayingAlwaysDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)


    /** The playing song's artist credit split into artists (a collaboration names several); the artist link asks which when there are. */
    val artists: StateFlow<List<String>> = combine(player.state.map { it.artist }.distinctUntilChanged(), libraryRepository.artistIndex) { credit, index ->
        if (credit.isBlank()) emptyList() else index.artistsOf(credit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The playing position at this instant (for per-frame use on the main thread: the lyrics). */
    fun positionNow(): Long = player.currentPositionMs()

    fun toggleFavourite() { player.state.value.songId?.let { id -> viewModelScope.launch { favourites.toggleSong(id) } } }

    /** The pre-blurred strips around the cover (where the live shader is off); computed off the main thread and cached. */
    suspend fun coverStrips(spec: CoverStripSpec): CoverStrips? = stripCache.get(spec)

    /** What is already built (no work, main thread): lets the screen start with the blur in place. */
    fun coverStripsIfReady(spec: CoverStripSpec): CoverStrips? = stripCache.peek(spec)
    fun diffusedCoverIfReady(songId: Long, albumId: Long) = diffusedArt.peek(songId, albumId)

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
    fun moveQueueItem(from: Int, to: Int) = player.moveQueueItem(from, to)
}

package com.mrgnstr.harmony.feature.album

import com.mrgnstr.harmony.core.playback.QueueSourceKind
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.data.PlaylistRepository
import com.mrgnstr.harmony.core.download.OfflineDownloadManager
import com.mrgnstr.harmony.core.mediastore.AlbumCredits
import com.mrgnstr.harmony.core.database.AlbumKey
import com.mrgnstr.harmony.core.mediastore.AlbumCreditsReader
import com.mrgnstr.harmony.core.mediastore.AudioFormatProbe
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.AlbumExtraInfo
import com.mrgnstr.harmony.core.model.AudioFormat
import com.mrgnstr.harmony.core.model.FormatTag
import com.mrgnstr.harmony.core.model.NewestFirst
import com.mrgnstr.harmony.core.model.Playlist
import com.mrgnstr.harmony.core.model.ShareLink
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.mainEditions
import com.mrgnstr.harmony.core.model.otherVersionsOf
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AlbumUiState(
    val loaded: Boolean = false,
    val title: String = "",
    /** The album artist as credited, and the artists it names (several for a collaboration). */
    val artist: String = "",
    val artists: List<String> = emptyList(),
    val genre: String? = null,
    val year: Int = 0,
    /** Format tags; empty until the files have been probed. */
    val formats: List<FormatTag> = emptyList(),
    val songs: List<Song> = emptyList(),
    val hasMultipleDiscs: Boolean = false,
    val playingId: Long? = null,
    /** Cover colour (ARGB) the whole app is themed with, null until the palette is known. */
    val accent: Int? = null,
    val durationMs: Long = 0,
    val addedSec: Long = 0,
    val credits: AlbumCredits? = null,
    val favouriteSongs: Set<Long> = emptySet(),
    val favouriteAlbum: Boolean = false,
    val otherVersions: List<Album> = emptyList(),
    val moreByArtist: List<Album> = emptyList(),
    val remoteExtra: AlbumExtraInfo? = null,
)

@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
class AlbumViewModel @AssistedInject constructor(
    @Assisted private val albumId: Long,
    @Assisted private val title: String,
    repository: LibraryRepository,
    colorStore: ArtworkColorStore,
    private val probe: AudioFormatProbe,
    private val creditsReader: AlbumCreditsReader,
    private val favourites: FavouritesRepository,
    private val playlistRepository: PlaylistRepository,
    private val navidrome: NavidromeRepository,
    private val downloads: OfflineDownloadManager,
    private val player: PlayerConnection,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(albumId: Long, title: String): AlbumViewModel
    }

    // Known at once when the palette was seen before, so the page is themed in its first frame (see EntryTheme).
    private val accent = MutableStateFlow(colorStore.peek(albumId)?.takeIf { it.hasArt }?.primary)
    private val formats = MutableStateFlow<List<FormatTag>>(emptyList())
    private val credits = MutableStateFlow<AlbumCredits?>(null)
    private val songsFlow = repository.albumSongs(albumId, title)

    init {
        viewModelScope.launch {
            val songs = songsFlow.first { it.isNotEmpty() }
            launch { accent.value = colorStore.paletteFor(songs.first().id, albumId)?.takeIf { it.hasArt }?.primary }
            if (songs.first().isRemote) {
                launch {
                    val remoteFormats = songs.take(40).mapNotNull { s ->
                        val c = s.codec ?: return@mapNotNull null
                        AudioFormat(c, s.lossless, s.sampleRateHz, s.bitDepth, s.channels)
                    }
                    formats.value = AudioFormat.tags(remoteFormats)
                }
                launch { navidrome.enrichAlbumInfo(albumId) }
            } else {
                // Probing is capped on long albums: the main formats are clear from the first few dozen files.
                launch { formats.value = AudioFormat.tags(songs.take(40).mapNotNull { probe.probe(it.id) }) }
                // The first song that has a label or copyright tag speaks for the album.
                launch { credits.value = songs.take(4).firstNotNullOfOrNull { creditsReader.read(it.id) } }
            }
        }
    }

    private val library = combine(songsFlow, repository.albums, repository.artistIndex, ::Triple)

    private class Extras(
        val accent: Int?,
        val formats: List<FormatTag>,
        val credits: AlbumCredits?,
        val favouriteSongs: Set<Long>,
        val favouriteAlbums: Set<AlbumKey>,
        val remoteExtra: AlbumExtraInfo?,
    )

    private val extras = combine(
        combine(accent, formats, credits, ::Triple),
        combine(favourites.songIds, favourites.albums, navidrome.observeAlbumExtra(albumId), ::Triple),
    ) { (acc, fmt, cred), (favSongs, favAlbums, remote) ->
        Extras(acc, fmt, cred, favSongs, favAlbums, remote)
    }

    /** The user's playlists, for the add-to-playlist sheet. */
    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<AlbumUiState> = combine(library, player.state, extras) { (songs, albums, index), playback, extras ->
        if (songs.isEmpty()) return@combine AlbumUiState(title = title, accent = extras.accent)
        val artist = songs.first().let { it.albumArtist.ifEmpty { it.artist } }
        val artists = index.artistsOf(artist)
        fun Album.sharesArtist() = index.artistsOf(this.artist).any { a -> artists.any { it.equals(a, ignoreCase = true) } }
        val self = albums.firstOrNull { it.id == albumId && it.title == title }
        val versions = if (self == null) emptyList() else otherVersionsOf(self, albums) { it.sharesArtist() }
        val versionKeys = versions.mapTo(HashSet()) { it.id to it.title }
        val mergedCredits = extras.credits ?: extras.remoteExtra?.recordLabels?.takeIf { it.isNotEmpty() }?.let {
            AlbumCredits(label = it.joinToString(", "), copyright = null)
        }
        AlbumUiState(
            loaded = true,
            title = title,
            artist = artist,
            artists = artists,
            genre = songs.firstNotNullOfOrNull { it.genre?.takeIf(String::isNotBlank) },
            year = songs.maxOf { it.year },
            formats = extras.formats,
            songs = songs,
            hasMultipleDiscs = songs.map { it.discNumber }.distinct().size > 1,
            playingId = playback.mediaId?.toLongOrNull(),
            accent = extras.accent,
            durationMs = songs.sumOf { it.durationMs },
            addedSec = songs.maxOf { it.dateAddedSec },
            credits = mergedCredits,
            favouriteSongs = extras.favouriteSongs,
            favouriteAlbum = AlbumKey(albumId, title) in extras.favouriteAlbums,
            otherVersions = versions,
            // Everything else by the artist: this album and its other versions are not repeated here.
            moreByArtist = albums.filter { it.sharesArtist() && !(it.id == albumId && it.title == title) && (it.id to it.title) !in versionKeys }
                .sortedWith(NewestFirst).mainEditions(),
            remoteExtra = extras.remoteExtra,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumUiState(title = title, accent = accent.value))

    fun play(songs: List<Song>, index: Int) = player.playQueue(songs, index, source = title, kind = QueueSourceKind.Album)

    fun shuffle(songs: List<Song>) {
        if (songs.isNotEmpty()) player.playQueue(songs.shuffled(), 0, source = title, kind = QueueSourceKind.Album)
    }

    fun playNext(songs: List<Song>) = player.playNext(songs)

    fun addToQueue(songs: List<Song>) = player.playLast(songs)

    fun toggleFavourite(song: Song) { viewModelScope.launch { favourites.toggleSong(song.id) } }

    fun toggleFavouriteAlbum() { viewModelScope.launch { favourites.toggleAlbum(albumId, title) } }

    fun addToPlaylist(playlistId: Long, songs: List<Song>) { viewModelScope.launch { playlistRepository.addSongs(playlistId, songs.map { it.id }) } }

    fun addToNewPlaylist(name: String, songs: List<Song>) { viewModelScope.launch { playlistRepository.create(name, songIds = songs.map { it.id }) } }

    fun downloadAlbum() { downloads.downloadSongs(state.value.songs) }

    suspend fun createShare(description: String, downloadable: Boolean): ShareLink? =
        navidrome.createShareForAlbum(albumId, description, downloadable = downloadable)

    fun refreshServerMetadata() {
        val remoteId = state.value.remoteExtra?.remoteId ?: return
        viewModelScope.launch {
            navidrome.refreshEntityMetadata("al", remoteId)
            navidrome.enrichAlbumInfo(albumId)
        }
    }
}

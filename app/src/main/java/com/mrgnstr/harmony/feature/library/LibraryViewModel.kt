package com.mrgnstr.harmony.feature.library

import com.mrgnstr.harmony.core.playback.QueueSourceKind
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.data.HistoryRepository
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.data.PlaylistRepository
import com.mrgnstr.harmony.core.database.PlayCount
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.ArtRef
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.GenreCard
import com.mrgnstr.harmony.core.model.LibrarySourceMode
import com.mrgnstr.harmony.core.model.Playlist
import com.mrgnstr.harmony.core.model.LibrarySort
import com.mrgnstr.harmony.core.model.SmartPlaylistCriteria
import com.mrgnstr.harmony.core.model.SortKey
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.heroOf
import com.mrgnstr.harmony.core.model.owner
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The tabs of the Library page, in order. [keys] are what the tab can be sorted by (none: no sort menu). */
enum class LibraryTab(val label: String, val keys: List<SortKey>, val default: LibrarySort) {
    RecentlyAdded("Recently Added", emptyList(), LibrarySort(SortKey.DateAdded)),
    Albums(
        "Albums",
        listOf(SortKey.Title, SortKey.Artist, SortKey.Year, SortKey.DateAdded, SortKey.RecentlyPlayed, SortKey.PlayCount, SortKey.Rating, SortKey.Duration, SortKey.TrackCount),
        LibrarySort(SortKey.Title),
    ),
    Songs(
        "Songs",
        listOf(SortKey.Title, SortKey.Artist, SortKey.AlbumArtist, SortKey.Album, SortKey.Year, SortKey.DateAdded, SortKey.RecentlyPlayed, SortKey.PlayCount, SortKey.Rating, SortKey.Duration),
        LibrarySort(SortKey.Title),
    ),
    Artists(
        "Artists",
        listOf(SortKey.Title, SortKey.RecentlyPlayed, SortKey.PlayCount, SortKey.TrackCount),
        LibrarySort(SortKey.Title),
    ),
    Playlists(
        "Playlists",
        listOf(SortKey.Title, SortKey.Updated, SortKey.TrackCount, SortKey.Duration),
        LibrarySort(SortKey.Title),
    ),
    Genres("Genres", listOf(SortKey.Title, SortKey.TrackCount), LibrarySort(SortKey.Title)),
}

/** How many albums the Recently Added tab shows. */
private const val RecentlyAddedCount = 60

private val ci = String.CASE_INSENSITIVE_ORDER

private fun <T> Comparator<T>.direction(ascending: Boolean) = if (ascending) this else reversed()

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    history: HistoryRepository,
    private val playlistRepository: PlaylistRepository,
    val navidrome: NavidromeRepository,
    private val settings: SettingsStore,
    private val player: PlayerConnection,
) : ViewModel() {
    val sourceMode: StateFlow<LibrarySourceMode> = settings.librarySourceMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySourceMode.All)

    val hasNavidrome: StateFlow<Boolean> = navidrome.activeAccount
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setSourceMode(mode: LibrarySourceMode) {
        viewModelScope.launch { settings.setLibrarySourceMode(mode) }
    }
    val sorts: StateFlow<Map<LibraryTab, LibrarySort>> = settings.librarySorts
        .map { saved -> LibraryTab.entries.associateWith { tab -> saved[tab.name]?.takeIf { it.key in tab.keys } ?: tab.default } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryTab.entries.associateWith { it.default })

    /** The first scan of this launch has not finished: an empty page is still filling in, not empty. */
    val loading: StateFlow<Boolean> = library.syncState.map { !it.completedOnce && !it.failed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val playingId: StateFlow<Long?> = player.state.map { it.mediaId?.toLongOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(tab: LibraryTab, sort: LibrarySort) { viewModelScope.launch { settings.setLibrarySort(tab.name, sort) } }

    private fun sortOf(tab: LibraryTab): Flow<LibrarySort> = sorts.map { it.getValue(tab) }

    /** Plays per song and when the latest one was, summed over each album / artist for their orderings. */
    private class Stats(val songs: Map<Long, PlayCount>, val albums: Map<String, PlayCount>, val artists: Map<String, PlayCount>) {
        fun of(song: Song) = songs[song.id]
    }

    private val stats: Flow<Stats> = combine(library.songs, library.artistIndex, history.playStats) { songs, index, plays ->
        val albums = HashMap<String, PlayCount>()
        val artists = HashMap<String, PlayCount>()
        fun add(into: MutableMap<String, PlayCount>, key: String, p: PlayCount) {
            val old = into[key]
            into[key] = if (old == null) p else PlayCount(0, old.count + p.count, maxOf(old.lastPlayedMs, p.lastPlayedMs))
        }
        for (song in songs) {
            val p = plays[song.id] ?: continue
            add(albums, albumKey(song.albumId, song.album), p)
            for (name in index.artistsOf(song.owner)) add(artists, name.lowercase(), p)
        }
        Stats(plays, albums, artists)
    }.flowOn(Dispatchers.Default)

    val recentlyAdded: StateFlow<List<Album>> = library.albums
        .map { albums -> albums.sortedByDescending { it.addedSec }.take(RecentlyAddedCount) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = combine(library.albums, stats, sortOf(LibraryTab.Albums)) { albums, stats, sort ->
        fun plays(a: Album) = stats.albums[albumKey(a.id, a.title)]
        val order: Comparator<Album> = when (sort.key) {
            SortKey.Artist -> compareBy<Album, String>(ci) { it.artist }.thenBy(ci) { it.title }
            SortKey.Year -> compareBy<Album> { it.year }.thenBy(ci) { it.title }
            SortKey.DateAdded -> compareBy<Album> { it.addedSec }.thenBy(ci) { it.title }
            SortKey.RecentlyPlayed -> compareBy<Album> { plays(it)?.lastPlayedMs ?: 0L }.thenBy(ci) { it.title }
            SortKey.PlayCount -> compareBy<Album> { plays(it)?.count ?: 0 }.thenBy(ci) { it.title }
            SortKey.Duration -> compareBy<Album> { it.durationMs }.thenBy(ci) { it.title }
            SortKey.TrackCount -> compareBy<Album> { it.songCount }.thenBy(ci) { it.title }
            else -> compareBy(ci) { it.title }
        }
        albums.sortedWith(order.direction(sort.ascending))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs: StateFlow<List<Song>> = combine(library.songs, stats, sortOf(LibraryTab.Songs)) { songs, stats, sort ->
        val order: Comparator<Song> = when (sort.key) {
            SortKey.Artist -> compareBy<Song, String>(ci) { it.artist }.thenBy(ci) { it.title }
            SortKey.AlbumArtist -> compareBy<Song, String>(ci) { it.albumArtist }.thenBy(ci) { it.album }.thenBy { it.discNumber }.thenBy { it.trackNumber }
            SortKey.Album -> compareBy<Song, String>(ci) { it.album }.thenBy { it.discNumber }.thenBy { it.trackNumber }
            SortKey.Year -> compareBy<Song> { it.year }.thenBy(ci) { it.title }
            SortKey.DateAdded -> compareBy<Song> { it.dateAddedSec }.thenBy(ci) { it.title }
            SortKey.RecentlyPlayed -> compareBy<Song> { stats.of(it)?.lastPlayedMs ?: 0L }.thenBy(ci) { it.title }
            SortKey.PlayCount -> compareBy<Song> { stats.of(it)?.count ?: 0 }.thenBy(ci) { it.title }
            SortKey.Rating -> compareBy<Song> { it.userRating }.thenBy(ci) { it.title }
            SortKey.Duration -> compareBy<Song> { it.durationMs }.thenBy(ci) { it.title }
            else -> compareBy(ci) { it.title }
        }
        songs.sortedWith(order.direction(sort.ascending))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The artists come pictured (own photo, server picture or the page's hero release) from the repository.
    val artists: StateFlow<List<Artist>> = combine(
        library.artists, combine(stats, sortOf(LibraryTab.Artists)) { s, o -> s to o },
    ) { shown, (stats, sort) ->
        fun plays(a: Artist) = stats.artists[a.name.lowercase()]
        val order: Comparator<Artist> = when (sort.key) {
            SortKey.RecentlyPlayed -> compareBy<Artist> { plays(it)?.lastPlayedMs ?: 0L }.thenBy(ci) { it.name }
            SortKey.PlayCount -> compareBy<Artist> { plays(it)?.count ?: 0 }.thenBy(ci) { it.name }
            SortKey.TrackCount -> compareBy<Artist> { it.songCount }.thenBy(ci) { it.name }
            else -> compareBy(ci) { it.name }
        }
        shown.sortedWith(order.direction(sort.ascending))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Favourites stays first; the user's own playlists follow in the chosen order. */
    val playlists: StateFlow<List<Playlist>> = combine(playlistRepository.playlistsWithFavourites, sortOf(LibraryTab.Playlists)) { all, sort ->
        val order: Comparator<Playlist> = when (sort.key) {
            SortKey.Updated -> compareBy<Playlist> { it.updatedAtMs }.thenBy(ci) { it.name }
            SortKey.TrackCount -> compareBy<Playlist> { it.songCount }.thenBy(ci) { it.name }
            SortKey.Duration -> compareBy<Playlist> { it.durationMs }.thenBy(ci) { it.name }
            else -> compareBy(ci) { it.name }
        }
        all.filter { it.isFavourites } + all.filterNot { it.isFavourites }.sortedWith(order.direction(sort.ascending))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val genres: StateFlow<List<GenreCard>> = combine(library.genres, library.songs, sortOf(LibraryTab.Genres)) { genres, songs, sort ->
        val byGenre = songs.groupBy { it.genre }
        val cards = genres.map { genre ->
            val art = byGenre[genre.name].orEmpty().distinctBy { it.albumId }.take(3).map { ArtRef(it.id, it.albumId) }
            GenreCard(genre.name, genre.songCount, art)
        }
        val order: Comparator<GenreCard> = when (sort.key) {
            SortKey.TrackCount -> compareBy<GenreCard> { it.songCount }.thenBy(ci) { it.name }
            else -> compareBy(ci) { it.name }
        }
        cards.sortedWith(order.direction(sort.ascending))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createPlaylist(name: String, coverUri: String?, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch { onCreated(playlistRepository.create(name.ifBlank { "New Playlist" }, coverUri)) }
    }

    fun createSmartPlaylist(name: String, comment: String, isPublic: Boolean, criteria: SmartPlaylistCriteria) {
        viewModelScope.launch { navidrome.createSmartPlaylist(name, comment, isPublic, criteria) }
    }

    fun editPlaylist(playlist: Playlist, name: String, coverUri: String?, clearCover: Boolean) {
        viewModelScope.launch { playlistRepository.edit(playlist.id, name.ifBlank { playlist.name }, coverUri, clearCover) }
    }

    fun deletePlaylist(playlist: Playlist) { viewModelScope.launch { playlistRepository.delete(playlist.id) } }

    fun playPlaylist(playlist: Playlist, shuffled: Boolean) {
        viewModelScope.launch {
            val songs = playlistRepository.entries(playlist.id).first().map { it.song }
            if (songs.isNotEmpty()) player.playQueue(if (shuffled) songs.shuffled() else songs, 0, source = playlist.name, kind = if (playlist.isFavourites) QueueSourceKind.Favourites else QueueSourceKind.Playlist)
        }
    }

    fun play(songs: List<Song>, index: Int) = player.playQueue(songs, index, source = "Library")

    fun shuffle(songs: List<Song>) { if (songs.isNotEmpty()) player.playQueue(songs.shuffled(), 0, source = "Library") }
}

private fun albumKey(id: Long, title: String) = "$id:$title"

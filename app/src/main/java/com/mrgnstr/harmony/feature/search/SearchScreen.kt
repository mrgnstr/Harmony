@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.mrgnstr.harmony.feature.search

import com.mrgnstr.harmony.core.playback.QueueSourceKind
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.data.PlaylistRepository
import com.mrgnstr.harmony.core.data.SearchRepository
import com.mrgnstr.harmony.core.data.SearchResults
import com.mrgnstr.harmony.core.database.AlbumKey
import com.mrgnstr.harmony.core.database.RecentSearchEntity
import com.mrgnstr.harmony.core.designsystem.ArtistPicture
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalOpenAlbum
import com.mrgnstr.harmony.core.designsystem.LocalOpenArtist
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.LocalTopBarInset
import com.mrgnstr.harmony.core.designsystem.bleedSides
import com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
import com.mrgnstr.harmony.core.designsystem.components.AlbumContextMenu
import com.mrgnstr.harmony.core.designsystem.components.LocalOfflineDim
import com.mrgnstr.harmony.core.designsystem.components.ArtistContextMenu
import com.mrgnstr.harmony.core.designsystem.components.CenteredDropdownMenu
import com.mrgnstr.harmony.core.designsystem.components.EmptyState
import com.mrgnstr.harmony.core.designsystem.components.GenreCardTile
import com.mrgnstr.harmony.core.designsystem.components.LocalCoverShare
import com.mrgnstr.harmony.core.designsystem.components.MediaRow
import com.mrgnstr.harmony.core.designsystem.components.PlaylistCover
import com.mrgnstr.harmony.core.designsystem.components.RowCoverShape
import com.mrgnstr.harmony.core.designsystem.components.SectionHeading
import com.mrgnstr.harmony.core.designsystem.components.SongContextMenu
import com.mrgnstr.harmony.core.designsystem.components.SongRow
import com.mrgnstr.harmony.core.designsystem.components.TopBarScaffold
import com.mrgnstr.harmony.core.designsystem.components.albumCoverKey
import com.mrgnstr.harmony.core.designsystem.components.gridColumns
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.designsystem.components.sharedCover
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import com.mrgnstr.harmony.core.designsystem.motion.pressResponse
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.ArtRef
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.GenreCard
import com.mrgnstr.harmony.core.model.Playlist
import com.mrgnstr.harmony.core.model.SearchCategory
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val SearchFieldRowHeight = 68.dp
private val SearchPillHeight = 52.dp
private const val PreviewLimit = 5
private const val SearchDebounceMs = 200L

private enum class SearchScope(val label: String, val category: SearchCategory?) {
    All("All", null),
    Songs("Songs", SearchCategory.Songs),
    Albums("Albums", SearchCategory.Albums),
    Artists("Artists", SearchCategory.Artists),
    Playlists("Playlists", SearchCategory.Playlists),
}

@Immutable
data class SearchUiState(
    val results: SearchResults = SearchResults(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: SearchRepository,
    private val library: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val favourites: FavouritesRepository,
    private val player: PlayerConnection,
) : ViewModel() {
    private val immediateNext = AtomicBoolean(false)

    val query: StateFlow<String> = savedState.getStateFlow("query", "")

    val recents: StateFlow<List<RecentSearchEntity>> = repository.recents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playingId: StateFlow<Long?> = player.state.map { it.mediaId?.toLongOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favouriteSongs: StateFlow<Set<Long>> = favourites.songIds
    val favouriteAlbums: StateFlow<Set<AlbumKey>> = favourites.albums
    val favouriteArtists: StateFlow<Set<String>> = favourites.artists

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = library.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryLoading: StateFlow<Boolean> = library.syncState
        .map { !it.completedOnce && !it.failed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val genres: StateFlow<List<GenreCard>> = combine(library.genres, library.songs) { genres, songs ->
        val byGenre = songs.groupBy { it.genre }
        genres.map { genre ->
            val art = byGenre[genre.name].orEmpty().distinctBy { it.albumId }.take(3).map { ArtRef(it.id, it.albumId) }
            GenreCard(genre.name, genre.songCount, art)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<SearchUiState> = query.flatMapLatest { input ->
        val skipDelay = immediateNext.getAndSet(false)
        flow {
            if (input.isBlank()) {
                emit(SearchUiState(SearchResults("")))
                return@flow
            }
            emit(SearchUiState(SearchResults(input), loading = true))
            if (!skipDelay) delay(SearchDebounceMs)
            repository.search(input).collect { emit(SearchUiState(it, loading = false)) }
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(SearchUiState(SearchResults(input), failed = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String, immediate: Boolean = false) {
        if (immediate) immediateNext.set(true)
        savedState["query"] = value.take(200)
    }

    fun selectRecent(value: String) {
        setQuery(value, immediate = true)
        viewModelScope.launch { repository.remember(value) }
    }

    fun rememberQuery() {
        val input = query.value
        viewModelScope.launch { repository.remember(input) }
    }

    fun removeRecent(key: String) {
        viewModelScope.launch { repository.removeRecent(key) }
    }

    fun clearRecents() {
        viewModelScope.launch { repository.clearRecents() }
    }

    fun playSong(index: Int) {
        val songs = state.value.results.songs
        if (index !in songs.indices) return
        rememberQuery()
        player.playQueue(songs, index, source = "Search")
    }

    fun playAlbum(album: Album, shuffled: Boolean = false) {
        rememberQuery()
        viewModelScope.launch {
            val songs = library.albumSongs(album.id, album.title).first()
            if (songs.isNotEmpty()) {
                player.playQueue(if (shuffled) songs.shuffled() else songs, 0, source = album.title, kind = QueueSourceKind.Album)
            }
        }
    }

    fun playArtist(artist: Artist) {
        rememberQuery()
        viewModelScope.launch {
            val songs = library.artistSongs(artist.name).first()
            if (songs.isNotEmpty()) {
                player.playQueue(songs, 0, source = artist.name, kind = QueueSourceKind.Artist)
            }
        }
    }

    fun playPlaylist(playlist: Playlist, shuffled: Boolean = false) {
        rememberQuery()
        viewModelScope.launch {
            val songs = playlistRepository.entries(playlist.id).first().map { it.song }
            if (songs.isNotEmpty()) {
                player.playQueue(if (shuffled) songs.shuffled() else songs, 0, source = playlist.name, kind = if (playlist.isFavourites) QueueSourceKind.Favourites else QueueSourceKind.Playlist)
            }
        }
    }

    fun playNextSong(song: Song) = player.playNext(listOf(song))
    fun addToQueueSong(song: Song) = player.playLast(listOf(song))

    fun playNextAlbum(album: Album) {
        viewModelScope.launch {
            val songs = library.albumSongs(album.id, album.title).first()
            if (songs.isNotEmpty()) player.playNext(songs)
        }
    }

    fun addToQueueAlbum(album: Album) {
        viewModelScope.launch {
            val songs = library.albumSongs(album.id, album.title).first()
            if (songs.isNotEmpty()) player.playLast(songs)
        }
    }

    fun addAlbumToPlaylist(album: Album, onSongsLoaded: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = library.albumSongs(album.id, album.title).first()
            if (songs.isNotEmpty()) onSongsLoaded(songs)
        }
    }

    fun toggleFavouriteSong(song: Song) { viewModelScope.launch { favourites.toggleSong(song.id) } }
    fun toggleFavouriteAlbum(album: Album) { viewModelScope.launch { favourites.toggleAlbum(album.id, album.title) } }
    fun toggleFavouriteArtist(name: String) { viewModelScope.launch { favourites.toggleArtist(name) } }

    fun addToPlaylist(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch { playlistRepository.addSongs(playlistId, songs.map { it.id }) }
    }

    fun addToNewPlaylist(name: String, songs: List<Song>) {
        viewModelScope.launch { playlistRepository.create(name, songIds = songs.map { it.id }) }
    }
}

/**
 * Search: expressive M3 search field pinned in the blurred top bar, recent searches with swipe-to-remove,
 * browse-by-genre cards when empty, and grouped results (Top Result card, Songs, Albums, Artists, Playlists).
 */
@Composable
fun SearchRoute(
    onOpenSettings: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenGenre: (String) -> Unit,
    active: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val playingId by viewModel.playingId.collectAsStateWithLifecycle()
    val libraryLoading by viewModel.libraryLoading.collectAsStateWithLifecycle()

    var scope by rememberSaveable { mutableStateOf(SearchScope.All) }
    val focus = LocalFocusManager.current
    val results = state.results
    val searching = state.loading || results.query != query

    val openResult = {
        viewModel.rememberQuery()
        focus.clearFocus()
    }

    CompositionLocalProvider(
        LocalOpenAlbum provides { album -> openResult(); onOpenAlbum(album) },
        LocalOpenArtist provides { artist -> openResult(); onOpenArtist(artist) },
    ) {
    TopBarScaffold(
        title = "Search",
        modifier = modifier.imePadding(),
        actions = {
            IconButton(onClick = onOpenSettings) { SymbolIcon(Symbols.Settings, "Settings") }
        },
        floatingBarHeight = SearchFieldRowHeight,
        floatingBar = { blur ->
            SearchFieldBar(
                query = query,
                active = active,
                blur = blur,
                onQueryChange = { viewModel.setQuery(it) },
                onClear = { viewModel.setQuery("", immediate = true) },
                onSearch = { viewModel.rememberQuery(); focus.clearFocus() },
            )
        },
    ) {
        if (query.isBlank()) {
            if (recents.isEmpty() && genres.isEmpty()) {
                EmptyState(
                    loading = libraryLoading,
                    symbol = Symbols.Search,
                    title = "Search your library",
                    message = "Find songs, albums, artists and playlists.",
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sides = LocalSideInsets.current
                    val columns = gridColumns(maxWidth - sides.left - sides.right)
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = sides.padding(
                            top = LocalTopBarInset.current + 8.dp,
                            bottom = LocalBottomBarInset.current + 24.dp,
                        ),
                    ) {
                        if (recents.isNotEmpty()) {
                            item(key = "recent-heading", contentType = "heading") {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Recent Searches",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleLargeEmphasized,
                                    )
                                    TextButton(onClick = viewModel::clearRecents) {
                                        Text("Clear")
                                    }
                                }
                            }
                            items(recents, key = { "recent:${it.key}" }, contentType = { "recent" }) { recent ->
                                RecentSearchRow(
                                    recent = recent,
                                    onSelect = {
                                        focus.clearFocus()
                                        viewModel.selectRecent(recent.query)
                                    },
                                    onRemove = { viewModel.removeRecent(recent.key) },
                                    modifier = itemMotion(),
                                )
                            }
                        }
                        if (genres.isNotEmpty()) {
                            item(key = "genres-heading", contentType = "heading") {
                                SectionHeading("Browse Genres")
                            }
                            val rows = genres.chunked(columns)
                            itemsIndexed(
                                rows,
                                key = { _, row -> "genre-row:$columns:${row.first().name}" },
                                contentType = { _, _ -> "genre-row" },
                            ) { _, row ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    row.forEach { genre ->
                                        GenreCardTile(
                                            genre = genre,
                                            onClick = { focus.clearFocus(); onOpenGenre(genre.name) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val showSongs = scope == SearchScope.All || scope == SearchScope.Songs
            val showAlbums = scope == SearchScope.All || scope == SearchScope.Albums
            val showArtists = scope == SearchScope.All || scope == SearchScope.Artists
            val showPlaylists = scope == SearchScope.All || scope == SearchScope.Playlists
            val visibleCount = (if (showSongs) results.songs.size else 0) +
                (if (showAlbums) results.albums.size else 0) +
                (if (showArtists) results.artists.size else 0) +
                (if (showPlaylists) results.playlists.size else 0)

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = LocalSideInsets.current.padding(
                    top = LocalTopBarInset.current + 8.dp,
                    bottom = LocalBottomBarInset.current + 24.dp,
                ),
            ) {
                item(key = "scopes", contentType = "scopes") {
                    val sides = LocalSideInsets.current
                    Row(
                        Modifier
                            .bleedSides(sides)
                            .horizontalScroll(rememberScrollState())
                            .absolutePadding(left = sides.left, right = sides.right)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchScope.entries.forEach { option ->
                            ToggleButton(
                                checked = option == scope,
                                onCheckedChange = { scope = option },
                            ) {
                                Text(option.label)
                            }
                        }
                    }
                }

                when {
                    state.failed && !searching -> {
                        item(key = "failed") {
                            EmptyState(
                                loading = false,
                                symbol = Symbols.Search,
                                title = "Search unavailable",
                                message = "Couldn't search your library. Try again.",
                            )
                        }
                    }
                    searching -> {
                        item(key = "searching") {
                            EmptyState(loading = true, symbol = Symbols.Search, title = "", message = "")
                        }
                    }
                    visibleCount == 0 -> {
                        item(key = "empty") {
                            EmptyState(
                                loading = false,
                                symbol = Symbols.Search,
                                title = "No results",
                                message = "Nothing matched \"$query\".",
                            )
                        }
                    }
                    else -> {
                        if (scope == SearchScope.All && results.categoryOrder.isNotEmpty()) {
                            item(key = "top-result:heading", contentType = "heading") {
                                SectionHeading("Top Result")
                            }
                            item(key = "top-result:card", contentType = "top-result") {
                                TopResultCard(
                                    results = results,
                                    playingId = playingId,
                                    onOpenAlbum = { album -> openResult(); onOpenAlbum(album) },
                                    onOpenArtist = { artist -> openResult(); onOpenArtist(artist.name) },
                                    onOpenPlaylist = { playlist -> openResult(); onOpenPlaylist(playlist.id) },
                                    onPlaySong = { openResult(); viewModel.playSong(0) },
                                    onPlayAlbum = { album -> openResult(); viewModel.playAlbum(album) },
                                    onPlayArtist = { artist -> openResult(); viewModel.playArtist(artist) },
                                    onPlayPlaylist = { playlist -> openResult(); viewModel.playPlaylist(playlist) },
                                )
                            }
                        }

                        val categories = if (scope == SearchScope.All) results.categoryOrder else listOfNotNull(scope.category)
                        categories.forEach { category ->
                            when (category) {
                                SearchCategory.Songs -> if (results.songs.isNotEmpty()) {
                                    item(key = "songs-heading", contentType = "heading") {
                                        SectionHeading(
                                            "Songs",
                                            onMore = if (scope == SearchScope.All && results.songs.size > PreviewLimit) ({ scope = SearchScope.Songs }) else null,
                                        )
                                    }
                                    val songs = if (scope == SearchScope.All) results.songs.take(PreviewLimit) else results.songs
                                    itemsIndexed(songs, key = { _, s -> "song:${s.id}" }, contentType = { _, _ -> "song" }) { index, song ->
                                        val subtitle = remember(song.artist, song.album) {
                                            listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album.takeIf { it.isNotBlank() }).joinToString(" · ")
                                        }
                                        SongRow(
                                            song = song,
                                            onClick = { focus.clearFocus(); viewModel.playSong(index) },
                                            subtitle = subtitle,
                                            playing = song.id == playingId,
                                            modifier = itemMotion(),
                                        )
                                    }
                                }
                                SearchCategory.Albums -> if (results.albums.isNotEmpty()) {
                                    item(key = "albums-heading", contentType = "heading") {
                                        SectionHeading(
                                            "Albums",
                                            onMore = if (scope == SearchScope.All && results.albums.size > PreviewLimit) ({ scope = SearchScope.Albums }) else null,
                                        )
                                    }
                                    val albums = if (scope == SearchScope.All) results.albums.take(PreviewLimit) else results.albums
                                    items(albums, key = { "album:${it.id}:${it.title}" }, contentType = { "album" }) { album ->
                                        SearchAlbumRow(
                                            album = album,
                                            onClick = { openResult(); onOpenAlbum(album) },
                                            modifier = itemMotion(),
                                        )
                                    }
                                }
                                SearchCategory.Artists -> if (results.artists.isNotEmpty()) {
                                    item(key = "artists-heading", contentType = "heading") {
                                        SectionHeading(
                                            "Artists",
                                            onMore = if (scope == SearchScope.All && results.artists.size > PreviewLimit) ({ scope = SearchScope.Artists }) else null,
                                        )
                                    }
                                    val artists = if (scope == SearchScope.All) results.artists.take(PreviewLimit) else results.artists
                                    items(artists, key = { "artist:${it.name}" }, contentType = { "artist" }) { artist ->
                                        SearchArtistRow(
                                            artist = artist,
                                            onClick = { openResult(); onOpenArtist(artist.name) },
                                            modifier = itemMotion(),
                                        )
                                    }
                                }
                                SearchCategory.Playlists -> if (results.playlists.isNotEmpty()) {
                                    item(key = "playlists-heading", contentType = "heading") {
                                        SectionHeading(
                                            "Playlists",
                                            onMore = if (scope == SearchScope.All && results.playlists.size > PreviewLimit) ({ scope = SearchScope.Playlists }) else null,
                                        )
                                    }
                                    val playlists = if (scope == SearchScope.All) results.playlists.take(PreviewLimit) else results.playlists
                                    items(playlists, key = { "playlist:${it.id}" }, contentType = { "playlist" }) { playlist ->
                                        SearchPlaylistRow(
                                            playlist = playlist,
                                            onClick = { openResult(); onOpenPlaylist(playlist.id) },
                                            onPlay = { viewModel.playPlaylist(playlist, shuffled = false) },
                                            onShuffle = { viewModel.playPlaylist(playlist, shuffled = true) },
                                            modifier = itemMotion(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * A separate floating blurred pill below the top bar. When switching to the Search tab, it springs open from a circle on the right
 * (matching the floating Search button's side) into the full-width search bar.
 */
@Composable
private fun SearchFieldBar(
    query: String,
    active: Boolean,
    blur: ChromeBlurState?,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    val expansion by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = Springs.land(visibilityThreshold = 0.005f),
        label = "searchFieldExpansion",
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(SearchFieldRowHeight)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val pillWidth = lerp(SearchPillHeight, maxWidth, expansion.coerceIn(0f, 1f))
        ChromeSurface(
            state = blur,
            shape = CircleShape,
            modifier = Modifier.width(pillWidth).height(SearchPillHeight),
            container = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                Modifier.fillMaxSize().padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymbolIcon(
                    Symbols.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .graphicsLayer { alpha = ((expansion - 0.25f) / 0.75f).coerceIn(0f, 1f) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            "Search songs, albums, artists…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    )
                }
                AnimatedVisibility(
                    visible = query.isNotEmpty() && expansion > 0.6f,
                    enter = scaleIn(Springs.press()) + fadeIn(),
                    exit = scaleOut(Springs.release()) + fadeOut(),
                ) {
                    IconButton(onClick = onClear) {
                        SymbolIcon(
                            Symbols.Close,
                            contentDescription = "Clear search",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    recent: RecentSearchEntity,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onRemove()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                SymbolIcon(Symbols.Delete, "Remove ${recent.query}", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onSelect)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(Symbols.History, null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                recent.query,
                Modifier.weight(1f).padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRemove) {
                SymbolIcon(Symbols.Close, "Remove ${recent.query}", size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Highlights the best match across all categories at the top of the All view, with direct open and play actions.
 */
@Composable
private fun TopResultCard(
    results: SearchResults,
    playingId: Long?,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onPlaySong: () -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onPlayArtist: (Artist) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
) {
    val topCategory = results.categoryOrder.firstOrNull() ?: return
    val share = LocalCoverShare.current
    val owner = remember { Any() }
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var menuInitialized by remember { mutableStateOf(false) }

    val label: String
    val title: String
    val subtitle: String
    val highlightTitle: Boolean
    val onCardClick: () -> Unit
    val onPlayClick: () -> Unit
    val artwork: @Composable () -> Unit
    val menuContent: @Composable () -> Unit

    when (topCategory) {
        SearchCategory.Artists -> {
            val artist = results.artists.firstOrNull() ?: return
            label = "Artist"
            title = artist.name
            subtitle = "${artist.albumCount} ${if (artist.albumCount == 1) "album" else "albums"} · ${artist.songCount} ${if (artist.songCount == 1) "song" else "songs"}"
            highlightTitle = false
            onCardClick = { onOpenArtist(artist) }
            onPlayClick = { onPlayArtist(artist) }
            artwork = {
                ArtistPicture(artist, Modifier.size(80.dp).clip(CircleShape))
            }
            menuContent = {
                if (menuInitialized) {
                    ArtistContextMenu(artistName = artist.name, expanded = menuOpen, onDismiss = { menuOpen = false })
                }
            }
        }
        SearchCategory.Albums -> {
            val album = results.albums.firstOrNull() ?: return
            val coverKey = albumCoverKey(album.id, album.title)
            label = "Album"
            title = album.title
            subtitle = listOfNotNull(album.artist, album.year.takeIf { it > 0 }?.toString()).joinToString(" · ")
            highlightTitle = false
            onCardClick = { share?.claim(coverKey, owner); onOpenAlbum(album) }
            onPlayClick = { onPlayAlbum(album) }
            artwork = {
                ArtworkImage(
                    album.artSongId,
                    album.id,
                    Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).sharedCover(coverKey, owner),
                )
            }
            menuContent = {
                if (menuInitialized) {
                    AlbumContextMenu(album = album, expanded = menuOpen, onDismiss = { menuOpen = false })
                }
            }
        }
        SearchCategory.Songs -> {
            val song = results.songs.firstOrNull() ?: return
            label = "Song"
            title = song.title
            subtitle = listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album.takeIf { it.isNotBlank() }).joinToString(" · ")
            highlightTitle = song.id == playingId
            onCardClick = onPlaySong
            onPlayClick = onPlaySong
            artwork = {
                ArtworkImage(song.id, song.albumId, Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)))
            }
            menuContent = {
                if (menuInitialized) {
                    SongContextMenu(song = song, expanded = menuOpen, onDismiss = { menuOpen = false })
                }
            }
        }
        SearchCategory.Playlists -> {
            val playlist = results.playlists.firstOrNull() ?: return
            label = "Playlist"
            title = playlist.name
            subtitle = "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}"
            highlightTitle = false
            onCardClick = { onOpenPlaylist(playlist) }
            onPlayClick = { onPlayPlaylist(playlist) }
            artwork = {
                PlaylistCover(playlist, Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)))
            }
            menuContent = {
                if (menuInitialized) {
                    CenteredDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Play") },
                            leadingIcon = { SymbolIcon(Symbols.Play, null) },
                            onClick = { menuOpen = false; onPlayPlaylist(playlist) },
                        )
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pressResponse(interaction, pressedScale = 0.97f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                Modifier
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuInitialized = true
                            menuOpen = true
                        },
                        onClick = onCardClick,
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork()
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = if (highlightTitle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledIconButton(
                    onClick = onPlayClick,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(48.dp),
                ) {
                    SymbolIcon(Symbols.Play, "Play $title", fill = 1f)
                }
            }
        }
        menuContent()
    }
}

@Composable
private fun SearchAlbumRow(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuInitialized by remember { mutableStateOf(false) }
    val openMenu = { menuInitialized = true; menuOpen = true }
    val share = LocalCoverShare.current
    val owner = remember { Any() }
    val coverKey = albumCoverKey(album.id, album.title)
    val subtitle = remember(album.artist, album.year) {
        listOfNotNull(album.artist.takeIf { it.isNotBlank() }, album.year.takeIf { it > 0 }?.toString()).joinToString(" · ")
    }
    MediaRow(
        title = album.title,
        subtitle = subtitle,
        onClick = { share?.claim(coverKey, owner); onClick() },
        modifier = modifier,
        onLongClick = openMenu,
        dimmed = LocalOfflineDim.current.album(album),
        leading = {
            ArtworkImage(
                album.artSongId,
                album.id,
                Modifier.size(56.dp).clip(RowCoverShape).sharedCover(coverKey, owner),
            )
        },
        trailing = {
            IconButton(onClick = openMenu) {
                SymbolIcon(Symbols.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        menu = {
            if (menuInitialized) {
                AlbumContextMenu(
                    album = album,
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                )
            }
        },
    )
}

@Composable
private fun SearchArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuInitialized by remember { mutableStateOf(false) }
    val openMenu = { menuInitialized = true; menuOpen = true }
    MediaRow(
        title = artist.name,
        subtitle = "${artist.songCount} ${if (artist.songCount == 1) "song" else "songs"}",
        onClick = onClick,
        modifier = modifier,
        onLongClick = openMenu,
        leading = {
            ArtistPicture(artist, Modifier.size(56.dp).clip(CircleShape))
        },
        trailing = {
            IconButton(onClick = openMenu) {
                SymbolIcon(Symbols.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        menu = {
            if (menuInitialized) {
                ArtistContextMenu(
                    artistName = artist.name,
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                )
            }
        },
    )
}

@Composable
private fun SearchPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuInitialized by remember { mutableStateOf(false) }
    val openMenu = { menuInitialized = true; menuOpen = true }
    MediaRow(
        title = playlist.name,
        subtitle = "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}",
        onClick = onClick,
        modifier = modifier,
        onLongClick = openMenu,
        leading = {
            PlaylistCover(playlist, Modifier.size(56.dp).clip(RowCoverShape))
        },
        trailing = {
            IconButton(onClick = openMenu) {
                SymbolIcon(Symbols.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        menu = {
            if (menuInitialized) {
                CenteredDropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { SymbolIcon(Symbols.Play, null) },
                        onClick = { menuOpen = false; onPlay() },
                    )
                    DropdownMenuItem(
                        text = { Text("Shuffle") },
                        leadingIcon = { SymbolIcon(Symbols.Shuffle, null) },
                        onClick = { menuOpen = false; onShuffle() },
                    )
                }
            }
        },
    )
}

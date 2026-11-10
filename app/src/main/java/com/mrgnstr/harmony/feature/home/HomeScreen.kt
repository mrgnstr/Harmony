package com.mrgnstr.harmony.feature.home

import com.mrgnstr.harmony.core.playback.QueueSourceKind
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.components.ArtistGridPage
import com.mrgnstr.harmony.core.designsystem.components.ArtistShelfItem
import com.mrgnstr.harmony.core.designsystem.components.ArtistShelfTileWidth
import com.mrgnstr.harmony.core.designsystem.components.ArtistShelfTile
import com.mrgnstr.harmony.core.model.Artist
import java.io.File
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.data.FAVOURITES_NAME
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.data.HistoryRepository
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.database.AlbumKey
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.LocalTopBarInset
import com.mrgnstr.harmony.core.designsystem.bleedSides
import com.mrgnstr.harmony.core.designsystem.components.AlbumGridPage
import com.mrgnstr.harmony.core.designsystem.components.AlbumTile
import com.mrgnstr.harmony.core.designsystem.components.ArtworkTile
import com.mrgnstr.harmony.core.designsystem.components.ColumnsShelf
import com.mrgnstr.harmony.core.designsystem.components.EmptyState
import com.mrgnstr.harmony.core.designsystem.components.SectionHeading
import com.mrgnstr.harmony.core.designsystem.components.ShelfTileWidth
import com.mrgnstr.harmony.core.designsystem.components.SongRow
import com.mrgnstr.harmony.core.designsystem.components.TopBarScaffold
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.FAVOURITES_PLAYLIST_ID
import com.mrgnstr.harmony.core.model.HomeShelf
import com.mrgnstr.harmony.core.model.RadioStation
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.playback.PlayerConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

private const val RecentAlbumsLimit = 20
private const val FavouriteSongsLimit = 5

@Immutable
data class HomeUiState(
    val recentAlbums: List<Album> = emptyList(),
    val favouriteSongs: List<Song> = emptyList(),
    val allFavouriteSongs: List<Song> = emptyList(),
    val favouriteAlbums: List<Album> = emptyList(),
    val favouriteArtists: List<Artist> = emptyList(),
    val radioStations: List<RadioStation> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playingId: Long? = null,
    val loading: Boolean = true,
    val hasLibrary: Boolean = false,
) {
    val isEmpty: Boolean get() = recentAlbums.isEmpty() && favouriteSongs.isEmpty() && favouriteAlbums.isEmpty() && favouriteArtists.isEmpty() && radioStations.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    history: HistoryRepository,
    favourites: FavouritesRepository,
    navidrome: NavidromeRepository,
    private val player: PlayerConnection,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        combine(library.albums, library.songs, library.syncState) { a, s, sync -> Triple(a, s, sync) },
        combine(history.playStats, navidrome.recentAlbums) { stats, server -> stats to server },
        combine(favourites.entries, favourites.albums, favourites.artists, library.artists) { fe, fa, names, artists ->
            val byName = artists.associateBy { it.name }
            Triple(fe, fa, names.mapNotNull { byName[it] })
        },
        navidrome.radios,
        player.state,
    ) { (albums, songs, sync), (playStats, serverRecent), (favEntries, favAlbumKeys, favArtists), radios, playback ->
        val byKey = albums.associateBy { AlbumKey(it.id, it.title) }
        // Each album by the time of its latest play: the local play times, and for albums on a server the server's own Recently
        // Played, which also holds plays made on other devices.
        val songById = songs.associateBy { it.id }
        val playedAt = HashMap<AlbumKey, Long>()
        for (stat in playStats.values) {
            val song = songById[stat.songId] ?: continue
            playedAt.merge(AlbumKey(song.albumId, song.album), stat.lastPlayedMs, ::maxOf)
        }
        val remoteByKey = albums.filter { it.id < 0L }.associateBy { it.id }
        for ((albumId, ms) in serverRecent) {
            val album = remoteByKey[albumId] ?: continue
            playedAt.merge(AlbumKey(album.id, album.title), ms, ::maxOf)
        }
        val recent = playedAt.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.take(RecentAlbumsLimit)
        val allFavSongs = favEntries.map { it.song }
        val favAlbums = favAlbumKeys.mapNotNull { byKey[it] }
        HomeUiState(
            recentAlbums = recent.toList(),
            favouriteSongs = allFavSongs.take(FavouriteSongsLimit),
            allFavouriteSongs = allFavSongs,
            favouriteAlbums = favAlbums,
            favouriteArtists = favArtists,
            radioStations = radios,
            albums = albums,
            playingId = playback.mediaId?.toLongOrNull(),
            loading = !sync.completedOnce && !sync.failed && albums.isEmpty() && songs.isEmpty(),
            hasLibrary = albums.isNotEmpty() || songs.isNotEmpty() || radios.isNotEmpty(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** The server's Recently Played also holds plays from other devices; read it whenever Home is opened. */
    init { viewModelScope.launch { navidrome.refreshRecentAlbums() } }

    fun playFavourite(index: Int) {
        val songs = state.value.allFavouriteSongs
        if (index in songs.indices) player.playQueue(songs, index, source = FAVOURITES_NAME, kind = QueueSourceKind.Favourites)
    }

    fun playRadio(station: RadioStation) {
        player.playRadio(station)
    }
}

/**
 * Home: Recently Played albums shelf, 5 most recent Favourite Songs, and Favourite Albums shelf.
 * Shelves use M3 tiles with press response, shared cover transitions, and snapping fling.
 */
@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenShelf: (HomeShelf) -> Unit,
    onOpenFavouriteArtists: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TopBarScaffold(
        title = "Home",
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenSettings) { SymbolIcon(Symbols.Settings, "Settings") }
        },
    ) {
        if (state.isEmpty) {
            EmptyState(
                loading = state.loading,
                symbol = Symbols.Home,
                title = if (state.hasLibrary) "Your Home is ready" else "Nothing here yet",
                message = if (state.hasLibrary) {
                    "Albums you play and music you favourite show up here."
                } else {
                    "Music you add to this phone shows up here."
                },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = LocalSideInsets.current.padding(
                    top = LocalTopBarInset.current,
                    bottom = LocalBottomBarInset.current + 24.dp,
                ),
            ) {
                albumShelf(
                    key = "recent",
                    heading = HomeShelf.RecentlyPlayed.title,
                    albums = state.recentAlbums,
                    onMore = { onOpenShelf(HomeShelf.RecentlyPlayed) },
                    onOpenAlbum = onOpenAlbum,
                )
                if (state.favouriteSongs.isNotEmpty()) {
                    item(key = "fav-songs:heading", contentType = "heading") {
                        SectionHeading("Favourite Songs", onMore = { onOpenPlaylist(FAVOURITES_PLAYLIST_ID) })
                    }
                    // Every favourite, in columns of five that scroll sideways (as many columns beside each other as the width allows).
                    item(key = "fav-songs:shelf", contentType = "shelf") {
                        ColumnsShelf(state.allFavouriteSongs, key = { "fav-song:${it.id}" }) { song, index ->
                            val subtitle = remember(song.artist, song.album) {
                                listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album.takeIf { it.isNotBlank() }).joinToString(" · ")
                            }
                            SongRow(
                                song = song,
                                onClick = { viewModel.playFavourite(index) },
                                subtitle = subtitle,
                                playing = song.id == state.playingId,
                            )
                        }
                    }
                }
                albumShelf(
                    key = "fav-albums",
                    heading = HomeShelf.FavouriteAlbums.title,
                    albums = state.favouriteAlbums,
                    onMore = { onOpenShelf(HomeShelf.FavouriteAlbums) },
                    onOpenAlbum = onOpenAlbum,
                )
                if (state.favouriteArtists.isNotEmpty()) {
                    item(key = "fav-artists:heading", contentType = "heading") {
                        SectionHeading("Favourite Artists", onMore = onOpenFavouriteArtists)
                    }
                    item(key = "fav-artists:row", contentType = "shelf") {
                        val sides = LocalSideInsets.current
                        val rowState = rememberLazyListState()
                        LazyRow(
                            modifier = Modifier.bleedSides(sides),
                            state = rowState,
                            flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
                            contentPadding = sides.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.favouriteArtists, key = { "fav-artist:${it.name}" }, contentType = { "artist" }) { artist ->
                                ArtistShelfTile(ArtistShelfItem(artist.name, artist = artist), onClick = { onOpenArtist(artist.name) }, modifier = itemMotion().width(ArtistShelfTileWidth))
                            }
                        }
                    }
                }
                if (state.radioStations.isNotEmpty()) {
                    item(key = "radio:heading", contentType = "heading") {
                        SectionHeading("Internet Radio")
                    }
                    item(key = "radio:row", contentType = "shelf") {
                        val sides = LocalSideInsets.current
                        val rowState = rememberLazyListState()
                        LazyRow(
                            modifier = Modifier.bleedSides(sides),
                            state = rowState,
                            flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
                            contentPadding = sides.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.radioStations, key = { "radio:${it.id}" }, contentType = { "radio" }) { station ->
                                ArtworkTile(
                                    title = station.name,
                                    subtitle = station.homePageUrl ?: "Live Stream",
                                    onClick = { viewModel.playRadio(station) },
                                    modifier = itemMotion().width(ShelfTileWidth),
                                ) { artMod ->
                                    Box(artMod.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                                        SymbolIcon(Symbols.Radio, null, size = 48.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
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

private fun LazyListScope.albumShelf(
    key: String,
    heading: String,
    albums: List<Album>,
    onMore: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    if (albums.isEmpty()) return
    item(key = "$key:heading", contentType = "heading") {
        SectionHeading(heading, onMore = onMore)
    }
    item(key = "$key:row", contentType = "shelf") {
        val sides = LocalSideInsets.current
        val rowState = rememberLazyListState()
        LazyRow(
            modifier = Modifier.bleedSides(sides),
            state = rowState,
            flingBehavior = rememberSnapFlingBehavior(rowState, SnapPosition.Start),
            contentPadding = sides.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { "$key:${it.id}:${it.title}" }, contentType = { "album" }) { album ->
                AlbumTile(album, onClick = { onOpenAlbum(album) }, modifier = itemMotion().width(ShelfTileWidth))
            }
        }
    }
}

@Composable
fun HomeShelfRoute(
    shelf: HomeShelf,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val albums = when (shelf) {
        HomeShelf.RecentlyPlayed -> state.recentAlbums
        HomeShelf.FavouriteAlbums -> state.favouriteAlbums
    }
    AlbumGridPage(
        title = shelf.title,
        subtitle = "Home",
        albums = albums,
        loaded = !state.loading,
        onBack = onBack,
        onOpenAlbum = onOpenAlbum,
    )
}

@Composable
fun FavouriteArtistsRoute(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArtistGridPage(
        title = "Favourite Artists",
        subtitle = "Home",
        artists = remember(state.favouriteArtists) { state.favouriteArtists.map { ArtistShelfItem(it.name, artist = it) } },
        loaded = !state.loading,
        onBack = onBack,
        onOpenArtist = onOpenArtist,
    )
}

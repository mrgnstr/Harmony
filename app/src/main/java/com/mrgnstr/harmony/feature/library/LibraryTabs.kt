package com.mrgnstr.harmony.feature.library

import com.mrgnstr.harmony.core.designsystem.components.TileGrid
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.designsystem.ArtistPicture
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalTopBarInset
import com.mrgnstr.harmony.core.designsystem.components.AlbumTile
import com.mrgnstr.harmony.core.designsystem.components.ArtistContextMenu
import com.mrgnstr.harmony.core.designsystem.components.EmptyState
import com.mrgnstr.harmony.core.designsystem.icons.Symbol
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.components.ArtworkTile
import com.mrgnstr.harmony.core.designsystem.components.LazyGridFastScroller
import com.mrgnstr.harmony.core.designsystem.components.LazyListFastScroller
import com.mrgnstr.harmony.core.designsystem.components.PlayShuffleButtons
import com.mrgnstr.harmony.core.designsystem.components.SongRow
import com.mrgnstr.harmony.core.designsystem.components.gridCellPadding
import com.mrgnstr.harmony.core.designsystem.components.gridColumns
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.SortKey
import java.io.File

/** Room under the list for the floating tab bar and the mini player. */
@Composable
internal fun listPadding() = PaddingValues(top = LocalTopBarInset.current + 16.dp, bottom = LocalBottomBarInset.current + 16.dp)


/** An empty tab: a spinner while the first scan is still running, otherwise [title] and [message]. */
@Composable
internal fun LibraryEmpty(viewModel: LibraryViewModel, symbol: Symbol, title: String, message: String) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    EmptyState(loading, symbol, title, message)
}

/** The letter a name files under; "#" for a name that does not start with one. */
internal fun letterOf(name: String): String {
    val first = name.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

@Composable
fun RecentlyAddedTab(viewModel: LibraryViewModel, onOpenAlbum: (Album) -> Unit) {
    val albums by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    if (albums.isEmpty()) return LibraryEmpty(viewModel, Symbols.Library, "Nothing added yet", "Music you add to this phone shows up here.")
    TileGrid(albums.size) { columns -> albumTiles(albums, columns, onOpenAlbum) }
}

@Composable
fun AlbumsTab(viewModel: LibraryViewModel, onOpenAlbum: (Album) -> Unit) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val key = viewModel.sorts.collectAsStateWithLifecycle().value.getValue(LibraryTab.Albums).key
    if (albums.isEmpty()) return LibraryEmpty(viewModel, Symbols.Album, "No albums", "Albums show up here once your music is found.")
    TileGrid(albums.size, { i ->
        albums.getOrNull(i)?.let { when (key) { SortKey.Title -> letterOf(it.title); SortKey.Artist -> letterOf(it.artist); else -> null } }
    }) { columns -> albumTiles(albums, columns, onOpenAlbum) }
}

private fun LazyGridScope.albumTiles(albums: List<Album>, columns: Int, onOpenAlbum: (Album) -> Unit) {
    gridItemsIndexed(albums, key = { _, a -> "album:${a.id}:${a.title}" }, contentType = { _, _ -> "album" }) { i, album ->
        AlbumTile(album, { onOpenAlbum(album) }, itemMotion().padding(gridCellPadding(i % columns, columns)))
    }
}

@Composable
fun SongsTab(viewModel: LibraryViewModel) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playingId by viewModel.playingId.collectAsStateWithLifecycle()
    val sorts by viewModel.sorts.collectAsStateWithLifecycle()
    val key = sorts.getValue(LibraryTab.Songs).key
    val list = rememberLazyListState()
    if (songs.isEmpty()) return LibraryEmpty(viewModel, Symbols.MusicNote, "No songs", "Songs show up here once your music is found.")
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = listPadding()) {
            item(key = "buttons", contentType = "buttons") {
                PlayShuffleButtons(onPlay = { viewModel.play(songs, 0) }, onShuffle = { viewModel.shuffle(songs) }, modifier = Modifier.padding(bottom = 8.dp))
            }
            itemsIndexed(songs, key = { _, song -> song.id }, contentType = { _, _ -> "song" }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { viewModel.play(songs, index) },
                    playing = song.id == playingId,
                    modifier = itemMotion(),
                )
            }
        }
        // Item 0 is the Play/Shuffle row; the songs follow. Only the orderings by name have letters to show.
        LazyListFastScroller(
            list, itemCount = songs.size + 1,
            labelOf = { i -> songs.getOrNull((i - 1).coerceAtLeast(0))?.let { songLetter(it, key) } },
            // The page box starts above the collapsed bar (it is slid up with it), so the track starts below the expanded height.
            topInset = LocalTopBarInset.current + 8.dp,
            bottomInset = LocalBottomBarInset.current + 8.dp,
        )
    }
}

/** The letter a song files under in an ordering by name; null for the orderings that are not alphabetical. */
private fun songLetter(song: Song, key: SortKey): String? = when (key) {
    SortKey.Title -> letterOf(song.title)
    SortKey.Artist -> letterOf(song.artist)
    SortKey.AlbumArtist -> letterOf(song.albumArtist)
    SortKey.Album -> letterOf(song.album)
    else -> null
}

@Composable
fun ArtistsTab(viewModel: LibraryViewModel, onOpenArtist: (Artist) -> Unit) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val key = viewModel.sorts.collectAsStateWithLifecycle().value.getValue(LibraryTab.Artists).key
    if (artists.isEmpty()) return LibraryEmpty(viewModel, Symbols.Artist, "No artists", "Artists show up here once your music is found.")
    TileGrid(artists.size, { i -> artists.getOrNull(i)?.takeIf { key == SortKey.Title }?.let { letterOf(it.name) } }) { columns ->
        gridItemsIndexed(artists, key = { _, a -> "artist:${a.name}" }, contentType = { _, _ -> "artist" }) { i, artist ->
            var menuOpen by remember { mutableStateOf(false) }
            var menuInitialized by remember { mutableStateOf(false) }
            ArtworkTile(
                artist.name, null, { onOpenArtist(artist) },
                itemMotion().padding(gridCellPadding(i % columns, columns)),
                circular = true,
                onLongClick = { menuInitialized = true; menuOpen = true },
                menu = {
                    if (menuInitialized) {
                        ArtistContextMenu(
                            artistName = artist.name,
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                        )
                    }
                },
            ) { cover ->
                // Their own photo, else the server's picture of them, else the cover of one of their albums.
                ArtistPicture(artist, cover)
            }
        }
    }
}

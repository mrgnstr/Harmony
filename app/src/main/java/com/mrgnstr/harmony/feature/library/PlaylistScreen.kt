@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.bleedSides
import com.mrgnstr.harmony.core.designsystem.blur.chromeSource
import com.mrgnstr.harmony.core.designsystem.blur.rememberChromeBlurState
import com.mrgnstr.harmony.core.designsystem.components.AddSongsSheet
import com.mrgnstr.harmony.core.designsystem.components.ArtistGridPage
import com.mrgnstr.harmony.core.designsystem.components.ArtistShelfItem
import com.mrgnstr.harmony.core.designsystem.components.ArtistShelfTileWidth
import com.mrgnstr.harmony.core.designsystem.components.ArtistShelfTile
import com.mrgnstr.harmony.core.designsystem.components.ArtworkTile
import com.mrgnstr.harmony.core.designsystem.components.CoverFrame
import com.mrgnstr.harmony.core.designsystem.components.DetailTopBar
import com.mrgnstr.harmony.core.designsystem.components.HeaderGap
import com.mrgnstr.harmony.core.designsystem.components.MediaRow
import com.mrgnstr.harmony.core.designsystem.components.PlayShuffleButtons
import com.mrgnstr.harmony.core.designsystem.components.PlaylistCover
import com.mrgnstr.harmony.core.designsystem.components.SectionHeading
import com.mrgnstr.harmony.core.designsystem.components.ShelfTileWidth
import com.mrgnstr.harmony.core.designsystem.components.SongRow
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.designsystem.components.rememberTransitionSettled
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.theme.ColorSource
import com.mrgnstr.harmony.core.designsystem.theme.PageColorSource
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.FAVOURITES_PLAYLIST_ID
import java.io.File
import kotlin.math.roundToInt

private val BarRow = 64.dp

/**
 * A playlist, laid out like an album: the cover (heart for Favourites, the custom image or a mosaic otherwise) parallaxes away under the
 * top bar, then the name, how many songs, Play / Shuffle, the songs with their covers (an "Add songs" row on top of them) and a shelf of
 * the artists on it, in the order their first song appears. The page wears the first song's cover colours.
 */
@Composable
fun PlaylistRoute(
    playlistId: Long,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenArtists: (Int?) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
        key = "playlist:$playlistId",
        creationCallback = { it.create(playlistId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PageColorSource(state.accent?.let { ColorSource.Seed(it) })
    val playlist = state.playlist
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded, playlist) {
        if (state.loaded && playlist != null) seen = true
        else if (seen && playlist == null) onBack()
    }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val canEdit = playlist != null && !playlist.isFavourites && playlist.editable

    val listState = rememberLazyListState()
    val settled = rememberTransitionSettled()
    val blur = rememberChromeBlurState()
    val density = LocalDensity.current
    val sides = LocalSideInsets.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val barRowPx = with(density) { BarRow.toPx() }
    val titleGapPx = with(density) { HeaderGap.toPx() }
    val name = playlist?.name ?: if (playlistId == FAVOURITES_PLAYLIST_ID) "Favourite Songs" else "Playlist"

    // Everything the page draws, the top bar and its menu included, takes the theme's text colour (see AlbumRoute).
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            LazyColumn(
                Modifier.fillMaxSize().chromeSource(blur),
                state = listState,
                contentPadding = sides.padding(bottom = LocalBottomBarInset.current + 16.dp),
            ) {
                item(key = "cover", contentType = "cover") {
                    CoverFrame(listState) { if (playlist != null) PlaylistCover(playlist, Modifier.fillMaxSize()) }
                }
                if (state.loaded && playlist != null) {
                    item(key = "header", contentType = "header") { Header(name, state.entries.size, state.entries.sumOf { it.song.durationMs }) }
                    if (state.entries.isNotEmpty()) item(key = "actions", contentType = "actions") {
                        PlayShuffleButtons({ viewModel.play(0) }, viewModel::shuffle, Modifier.padding(top = 20.dp, bottom = 12.dp))
                    }
                    // Composing the rest while the page is still flying in costs frames; it follows once the transition has settled.
                    if (settled) {
                        if (canEdit) item(key = "add", contentType = "add") { AddSongsRow(Modifier.padding(top = if (state.entries.isEmpty()) 20.dp else 0.dp)) { adding = true } }
                        if (state.entries.isEmpty() && !canEdit) item(key = "empty", contentType = "empty") { EmptyHint() }
                        itemsIndexed(state.entries, key = { _, entry -> entry.entryId }, contentType = { _, _ -> "song" }) { index, entry ->
                            val song = entry.song
                            SongRow(
                                song = song,
                                onClick = { viewModel.play(index) },
                                subtitle = listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album.takeIf { it.isNotBlank() }).joinToString(" · "),
                                playing = song.id == state.playingId,
                                onRemoveFromPlaylist = if (playlistId != FAVOURITES_PLAYLIST_ID && playlist.editable) ({ viewModel.removeEntry(entry.entryId) }) else null,
                                modifier = itemMotion(),
                            )
                        }
                        if (state.featuredArtists.isNotEmpty()) artistShelf(state.featuredArtists, onOpenArtist) { onOpenArtists(state.accent) }
                    }
                }
            }
            DetailTopBar(
                title = name,
                fade = {
                    val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "header" }
                    if (header != null) ((statusTop + barRowPx - header.offset - titleGapPx) / barRowPx).coerceIn(0f, 1f)
                    else if (listState.firstVisibleItemIndex > 1) 1f else 0f
                },
                blur = blur,
                statusTop = with(density) { statusTop.toDp() },
                onBack = onBack,
                menu = { close ->
                    if (state.entries.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Play next") }, leadingIcon = { SymbolIcon(Symbols.PlayNext, null) },
                            onClick = { close(); viewModel.playNextAll() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") }, leadingIcon = { SymbolIcon(Symbols.QueueMusic, null) },
                            onClick = { close(); viewModel.addAllToQueue() },
                        )
                    }
                    if (canEdit) {
                        DropdownMenuItem(
                            text = { Text("Edit") }, leadingIcon = { SymbolIcon(Symbols.Edit, null) },
                            onClick = { close(); editing = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") }, leadingIcon = { SymbolIcon(Symbols.Delete, null) },
                            onClick = { close(); deleting = true },
                        )
                    }
                },
            )
        }
    }

    if (adding) {
        val library by viewModel.librarySongs.collectAsStateWithLifecycle()
        AddSongsSheet(
            songs = library,
            exclude = remember(state.entries) { state.entries.mapTo(HashSet()) { it.song.id } },
            onAdd = { songs -> adding = false; viewModel.addToPlaylist(playlistId, songs) },
            onDismiss = { adding = false },
        )
    }
    if (editing && playlist != null) {
        PlaylistEditor(playlist, { editing = false }) { newName, cover, clear ->
            editing = false
            viewModel.edit(newName, cover, clear)
        }
    }
    if (deleting && playlist != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("The songs stay in your library.") },
            confirmButton = { TextButton(onClick = { deleting = false; viewModel.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(name: String, songs: Int, durationMs: Long) {
    val minutes = (durationMs / 60_000f).roundToInt()
    val length = when {
        songs == 0 -> null
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min"
        else -> "${minutes.coerceAtLeast(1)} min"
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = HeaderGap), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        Text(
            listOfNotNull("$songs ${if (songs == 1) "song" else "songs"}", length).joinToString(" · "),
            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The first row of the song list: opens the picker. Shaped like a song row, with a tonal tile where the cover would be. */
@Composable
private fun AddSongsRow(modifier: Modifier = Modifier, onClick: () -> Unit) {
    MediaRow(
        title = "Add Songs",
        subtitle = null,
        onClick = onClick,
        modifier = modifier,
        titleColor = MaterialTheme.colorScheme.primary,
        leading = {
            Surface(Modifier.size(48.dp), shape = com.mrgnstr.harmony.core.designsystem.components.RowCoverShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    SymbolIcon(Symbols.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
    )
}

@Composable
private fun EmptyHint() {
    Text(
        "Songs you mark as favourite show up here.",
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
}

private fun LazyListScope.artistShelf(artists: List<Artist>, onOpenArtist: (String) -> Unit, onMore: () -> Unit) {
    item(key = "artists:heading", contentType = "heading") { SectionHeading("Featured Artists", onMore = onMore) }
    item(key = "artists:row", contentType = "shelf") {
        // Scrolls out to the display edges; rests 16 dp in from the content's side.
        val sides = LocalSideInsets.current
        LazyRow(Modifier.bleedSides(sides), contentPadding = sides.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(artists, key = { "artist:${it.name}" }, contentType = { "artist" }) { artist ->
                ArtistShelfTile(ArtistShelfItem(artist.name, artist = artist), { onOpenArtist(artist.name) }, Modifier.width(ArtistShelfTileWidth))
            }
        }
    }
}

@Composable
fun PlaylistArtistsRoute(
    playlistId: Long,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
        key = "playlist:$playlistId:artists",
        creationCallback = { it.create(playlistId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PageColorSource(state.accent?.let { ColorSource.Seed(it) })
    ArtistGridPage(
        title = "Featured Artists",
        subtitle = state.playlist?.name ?: if (playlistId == FAVOURITES_PLAYLIST_ID) "Favourite Songs" else "Playlist",
        artists = remember(state.featuredArtists) { state.featuredArtists.map { ArtistShelfItem(it.name, artist = it) } },
        loaded = state.loaded,
        onBack = onBack,
        onOpenArtist = onOpenArtist,
    )
}

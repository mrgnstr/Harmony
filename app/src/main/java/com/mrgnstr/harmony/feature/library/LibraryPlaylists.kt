package com.mrgnstr.harmony.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.data.PlaylistRepository
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.LocalTopBarInset
import com.mrgnstr.harmony.core.designsystem.components.ArtworkTile
import com.mrgnstr.harmony.core.designsystem.components.CenteredDropdownMenu
import com.mrgnstr.harmony.core.designsystem.components.EmptyState
import com.mrgnstr.harmony.core.designsystem.components.PlayShuffleButtons
import com.mrgnstr.harmony.core.designsystem.components.PlaylistCover
import com.mrgnstr.harmony.core.designsystem.components.SongRow
import com.mrgnstr.harmony.core.designsystem.components.TileCoverShape
import com.mrgnstr.harmony.core.designsystem.components.TileGrid
import com.mrgnstr.harmony.core.designsystem.components.TopBarScaffold
import com.mrgnstr.harmony.core.designsystem.components.gridCellPadding
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.FAVOURITES_PLAYLIST_ID
import com.mrgnstr.harmony.core.model.Playlist
import com.mrgnstr.harmony.core.model.PlaylistEntry
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.SortKey
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private sealed interface Editing {
    data object New : Editing
    data class Edit(val playlist: Playlist) : Editing
}

@Composable
fun PlaylistsTab(viewModel: LibraryViewModel, onOpenPlaylist: (Long) -> Unit) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val hasNavidrome by viewModel.hasNavidrome.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var buildingSmart by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    val key = viewModel.sorts.collectAsStateWithLifecycle().value.getValue(LibraryTab.Playlists).key
    val extraTiles = if (hasNavidrome) 2 else 1
    // Item 0 is the New Playlist tile (+ optional Smart Playlist tile) and Favourites is pinned first; none have a letter.
    TileGrid(playlists.size + extraTiles, { i ->
        playlists.getOrNull(i - extraTiles)?.takeIf { key == SortKey.Title && !it.isFavourites }?.let { letterOf(it.name) }
    }) { columns ->
        // The first cell: a tile that makes a new playlist.
        item(key = "new", contentType = "new") {
            ArtworkTile("New Playlist", null, { editing = Editing.New }, Modifier.padding(gridCellPadding(0, columns))) { cover ->
                Box(cover.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    SymbolIcon(Symbols.Add, null, size = 48.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        if (hasNavidrome) {
            item(key = "new-smart", contentType = "new") {
                ArtworkTile("Smart Playlist", "Navidrome rules", { buildingSmart = true }, Modifier.padding(gridCellPadding(1 % columns, columns))) { cover ->
                    Box(cover.background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                        SymbolIcon(Symbols.AutoAwesome, null, size = 44.dp, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        itemsIndexed(playlists, key = { _, p -> p.id }, contentType = { _, _ -> "playlist" }) { i, playlist ->
            PlaylistTile(
                playlist,
                onClick = { onOpenPlaylist(playlist.id) },
                onPlay = { viewModel.playPlaylist(playlist, shuffled = false) },
                onShuffle = { viewModel.playPlaylist(playlist, shuffled = true) },
                onEdit = { editing = Editing.Edit(playlist) },
                onDelete = { deleting = playlist },
                modifier = itemMotion().padding(gridCellPadding((i + extraTiles) % columns, columns)),
            )
        }
    }

    if (buildingSmart) {
        SmartPlaylistBuilderSheet(
            onDismiss = { buildingSmart = false },
            onCreate = { name, comment, isPublic, criteria ->
                buildingSmart = false
                viewModel.createSmartPlaylist(name, comment, isPublic, criteria)
            },
        )
    }

    when (val e = editing) {
        null -> Unit
        Editing.New -> PlaylistEditor(null, { editing = null }) { name, cover, _ ->
            editing = null
            viewModel.createPlaylist(name, cover)
        }
        is Editing.Edit -> PlaylistEditor(e.playlist, { editing = null }) { name, cover, clear ->
            editing = null
            viewModel.editPlaylist(e.playlist, name, cover, clear)
        }
    }
    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("The songs stay in your library.") },
            confirmButton = { TextButton(onClick = { deleting = null; viewModel.deletePlaylist(playlist) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** A playlist tile: tap opens it, long press offers play, shuffle, edit and delete (Favourites can only be played). */
@Composable
private fun PlaylistTile(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuInitialized by remember { mutableStateOf(false) }
    val countText = "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}"
    val subtitle = when {
        playlist.isSmart -> "Smart · $countText"
        playlist.isRemote -> "Navidrome · $countText"
        else -> countText
    }
    ArtworkTile(
        title = playlist.name,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        onLongClick = { menuInitialized = true; menuOpen = true },
        menu = {
            if (menuInitialized) {
                CenteredDropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Play") }, leadingIcon = { SymbolIcon(Symbols.Play, null) }, onClick = { menuOpen = false; onPlay() })
                    DropdownMenuItem(text = { Text("Shuffle") }, leadingIcon = { SymbolIcon(Symbols.Shuffle, null) }, onClick = { menuOpen = false; onShuffle() })
                    if (!playlist.isFavourites && playlist.editable) {
                        DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { SymbolIcon(Symbols.Edit, null) }, onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { SymbolIcon(Symbols.Delete, null) }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        },
    ) { cover -> PlaylistCover(playlist, cover) }
}

/** New playlist / edit playlist: a cover to pick (tap it) and a name. [onSave] gets the picked image, if any, and whether to drop the custom cover. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistEditor(initial: Playlist?, onDismiss: () -> Unit, onSave: (name: String, coverUri: String?, clearCover: Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) { picked = uri.toString(); reset = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (initial == null) "New Playlist" else "Edit Playlist", style = MaterialTheme.typography.titleLargeEmphasized)
            Box(
                Modifier.padding(top = 20.dp).size(168.dp).clip(TileCoverShape).clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    picked != null -> AsyncImage(picked, null, Modifier.size(168.dp), contentScale = ContentScale.Crop)
                    initial != null && !reset -> PlaylistCover(initial, Modifier.size(168.dp))
                    else -> Box(Modifier.size(168.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    SymbolIcon(Symbols.Image, "Choose cover", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
            if (picked != null || (initial?.coverPath != null && !reset)) {
                TextButton(onClick = { picked = null; reset = true }) { Text("Use default cover") }
            }
            OutlinedTextField(
                name, { name = it.take(100) },
                Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(name, picked, reset && picked == null) }, enabled = name.isNotBlank()) { Text("Save") }
            }
        }
    }
}

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val entries: List<PlaylistEntry> = emptyList(),
    val playingId: Long? = null,
    val favouriteSongIds: Set<Long> = emptySet(),
    val loaded: Boolean = false,
)

@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted private val playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val favourites: FavouritesRepository,
    library: LibraryRepository,
    private val player: PlayerConnection,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playlistId: Long): PlaylistDetailViewModel
    }

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<Album>> = library.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<PlaylistDetailUiState> = combine(
        playlistRepository.playlist(playlistId),
        playlistRepository.entries(playlistId),
        player.state,
        favourites.songIds,
    ) { playlist, entries, playback, favIds ->
        PlaylistDetailUiState(
            playlist = playlist,
            entries = entries,
            playingId = playback.mediaId?.toLongOrNull(),
            favouriteSongIds = favIds,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState())

    fun play(index: Int) {
        val songs = state.value.entries.map { it.song }
        if (index in songs.indices) {
            player.playQueue(songs, index, source = state.value.playlist?.name ?: "Playlist")
        }
    }

    fun shuffle() {
        val songs = state.value.entries.map { it.song }
        if (songs.isNotEmpty()) {
            player.playQueue(songs.shuffled(), 0, source = state.value.playlist?.name ?: "Playlist")
        }
    }

    fun playNext(song: Song) = player.playNext(listOf(song))
    fun addToQueue(song: Song) = player.playLast(listOf(song))
    fun toggleFavourite(song: Song) { viewModelScope.launch { favourites.toggleSong(song.id) } }
    fun removeEntry(entryId: Long) {
        viewModelScope.launch {
            if (playlistId == FAVOURITES_PLAYLIST_ID) favourites.toggleSong(entryId)
            else playlistRepository.removeEntry(playlistId, entryId)
        }
    }

    fun addToPlaylist(targetPlaylistId: Long, songs: List<Song>) {
        viewModelScope.launch { playlistRepository.addSongs(targetPlaylistId, songs.map { it.id }) }
    }

    fun addToNewPlaylist(name: String, songs: List<Song>) {
        viewModelScope.launch { playlistRepository.create(name, songIds = songs.map { it.id }) }
    }

    fun edit(name: String, coverUri: String?, clearCover: Boolean) {
        val p = state.value.playlist ?: return
        viewModelScope.launch { playlistRepository.edit(p.id, name.ifBlank { p.name }, coverUri, clearCover) }
    }

    fun delete() {
        if (playlistId != FAVOURITES_PLAYLIST_ID) {
            viewModelScope.launch { playlistRepository.delete(playlistId) }
        }
    }
}

@Composable
fun PlaylistRoute(
    playlistId: Long,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory>(
        key = "playlist:$playlistId",
        creationCallback = { it.create(playlistId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlist = state.playlist
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded, playlist) {
        if (state.loaded && playlist != null) seen = true
        else if (seen && playlist == null) onBack()
    }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    TopBarScaffold(
        title = playlist?.name ?: if (playlistId == FAVOURITES_PLAYLIST_ID) "Favourite Songs" else "Playlist",
        subtitle = playlist?.let { "${it.songCount} ${if (it.songCount == 1) "song" else "songs"}" },
        onBack = onBack,
        actions = {
            if (playlist != null && !playlist.isFavourites && playlist.editable) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { SymbolIcon(Symbols.MoreVert, "More") }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { SymbolIcon(Symbols.Edit, null) },
                            onClick = { menuOpen = false; editing = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { SymbolIcon(Symbols.Delete, null) },
                            onClick = { menuOpen = false; deleting = true },
                        )
                    }
                }
            }
        },
    ) {
        if (state.entries.isEmpty()) {
            val isFav = playlistId == FAVOURITES_PLAYLIST_ID
            EmptyState(
                loading = !state.loaded,
                symbol = if (isFav) Symbols.Favorite else Symbols.QueueMusic,
                title = if (isFav) "No favourite songs" else "Playlist is empty",
                message = if (isFav) "Songs you mark as favourite show up here." else "Add songs from any album, artist or search.",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = LocalSideInsets.current.padding(top = LocalTopBarInset.current + 16.dp, bottom = LocalBottomBarInset.current + 16.dp),
            ) {
                item(key = "buttons", contentType = "buttons") {
                    PlayShuffleButtons(onPlay = { viewModel.play(0) }, onShuffle = viewModel::shuffle, modifier = Modifier.padding(bottom = 8.dp))
                }
                listItemsIndexed(state.entries, key = { _, entry -> entry.entryId }, contentType = { _, _ -> "song" }) { index, entry ->
                    val song = entry.song
                    SongRow(
                        song = song,
                        onClick = { viewModel.play(index) },
                        subtitle = listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album.takeIf { it.isNotBlank() }).joinToString(" · "),
                        playing = song.id == state.playingId,
                        onRemoveFromPlaylist = if (playlistId != FAVOURITES_PLAYLIST_ID && playlist?.editable != false) ({ viewModel.removeEntry(entry.entryId) }) else null,
                        modifier = itemMotion(),
                    )
                }
            }
        }
    }

    if (editing && playlist != null) {
        PlaylistEditor(playlist, { editing = false }) { name, cover, clear ->
            editing = false
            viewModel.edit(name, cover, clear)
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

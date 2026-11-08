package com.mrgnstr.harmony.core.designsystem.components

import com.mrgnstr.harmony.core.playback.QueueSourceKind
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.data.PlaylistRepository
import com.mrgnstr.harmony.core.database.AlbumKey
import com.mrgnstr.harmony.core.designsystem.LocalOpenAlbum
import com.mrgnstr.harmony.core.designsystem.LocalOpenArtist
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.download.OfflineDownloadManager
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Playlist
import com.mrgnstr.harmony.core.model.ShareLink
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * An M3 Expressive dropdown menu centered on its parent layout node (the song row or album/playlist tile).
 * Scales out from the item's center and stays within the window margins.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenteredDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val horizontalMarginPx = with(density) { 16.dp.roundToPx() }
    val verticalMarginPx = with(density) { 48.dp.roundToPx() }
    val positionProvider = remember(horizontalMarginPx, verticalMarginPx) {
        CenteredMenuPositionProvider(horizontalMarginPx, verticalMarginPx)
    }
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
    ) {
        Surface(
            modifier = modifier,
            shape = MenuDefaults.shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(IntrinsicSize.Max)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class CenteredMenuPositionProvider(
    private val horizontalMarginPx: Int,
    private val verticalMarginPx: Int,
) : DropdownMenuPopupPositionProvider {
    private var _transformOrigin by mutableStateOf(TransformOrigin.Center)
    override val transformOrigin: TransformOrigin get() = _transformOrigin

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
        val maxY = (windowSize.height - popupContentSize.height - verticalMarginPx).coerceAtLeast(verticalMarginPx)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(horizontalMarginPx, maxX)
        val y = (anchorBounds.center.y - popupContentSize.height / 2).coerceIn(verticalMarginPx, maxY)
        val pivotX = if (popupContentSize.width > 0) {
            ((anchorBounds.center.x - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f)
        } else 0.5f
        val pivotY = if (popupContentSize.height > 0) {
            ((anchorBounds.center.y - y).toFloat() / popupContentSize.height).coerceIn(0f, 1f)
        } else 0.5f
        _transformOrigin = TransformOrigin(pivotX, pivotY)
        return IntOffset(x, y)
    }
}

/** Shared playback, playlist, favourite, rating, download, mix and share actions for context menus across all screens. */
@HiltViewModel
class MediaMenuViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val favourites: FavouritesRepository,
    val navidrome: NavidromeRepository,
    val downloads: OfflineDownloadManager,
    private val player: PlayerConnection,
) : ViewModel() {
    val favouriteSongs: StateFlow<Set<Long>> = favourites.songIds
    val favouriteAlbums: StateFlow<Set<AlbumKey>> = favourites.albums
    val favouriteArtists: StateFlow<Set<String>> = favourites.artists

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val albums: StateFlow<List<Album>> = library.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playNextSong(song: Song) = player.playNext(listOf(song))
    fun addToQueueSong(song: Song) = player.playLast(listOf(song))
    fun toggleFavouriteSong(song: Song) { viewModelScope.launch { favourites.toggleSong(song.id) } }

    fun setSongRating(song: Song, rating: Int) {
        viewModelScope.launch { navidrome.setRating(songId = song.id, rating = rating) }
    }

    fun setAlbumRating(album: Album, rating: Int) {
        viewModelScope.launch { navidrome.setRating(albumId = album.id, rating = rating) }
    }

    fun setArtistRating(artistName: String, rating: Int) {
        viewModelScope.launch { navidrome.setRating(artistName = artistName, rating = rating) }
    }

    fun startInstantMix(song: Song) {
        viewModelScope.launch {
            val mix = navidrome.instantMixForSong(song)
            if (mix.isNotEmpty()) player.playQueue(mix, 0, source = "${song.title} Mix")
        }
    }

    fun downloadSong(song: Song) = downloads.downloadSong(song)

    fun removeSongDownload(song: Song) {
        viewModelScope.launch { downloads.removeDownload(song) }
    }

    fun downloadAlbum(album: Album) {
        viewModelScope.launch {
            val songs = library.albumSongs(album.id, album.title).first()
            downloads.downloadSongs(songs)
        }
    }

    fun resolveAlbum(song: Song, onResolved: (Album) -> Unit) {
        val cached = albums.value.firstOrNull { it.id == song.albumId && it.title == song.album }
        if (cached != null) {
            onResolved(cached)
        } else {
            viewModelScope.launch {
                val found = library.albums.first().firstOrNull { it.id == song.albumId && it.title == song.album }
                    ?: Album(song.albumId, song.album, song.albumArtist, song.id, 1, song.dateAddedSec, song.durationMs, song.year)
                onResolved(found)
            }
        }
    }

    fun playAlbum(album: Album, shuffled: Boolean = false) {
        viewModelScope.launch {
            val songs = library.albumSongs(album.id, album.title).first()
            if (songs.isNotEmpty()) {
                player.playQueue(if (shuffled) songs.shuffled() else songs, 0, source = album.title, kind = QueueSourceKind.Album)
            }
        }
    }

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

    fun loadAlbumSongs(album: Album, onLoaded: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = library.albumSongs(album.id, album.title).first()
            if (songs.isNotEmpty()) onLoaded(songs)
        }
    }

    fun toggleFavouriteAlbum(album: Album) {
        viewModelScope.launch { favourites.toggleAlbum(album.id, album.title) }
    }

    fun playArtist(artistName: String, shuffled: Boolean = false) {
        viewModelScope.launch {
            val songs = library.artistSongs(artistName).first()
            if (songs.isNotEmpty()) {
                player.playQueue(if (shuffled) songs.shuffled() else songs, 0, source = artistName, kind = QueueSourceKind.Artist)
            }
        }
    }

    fun toggleFavouriteArtist(artistName: String) {
        viewModelScope.launch { favourites.toggleArtist(artistName) }
    }

    fun addToPlaylist(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch { playlistRepository.addSongs(playlistId, songs.map { it.id }) }
    }

    fun addToNewPlaylist(name: String, songs: List<Song>) {
        viewModelScope.launch { playlistRepository.create(name, songIds = songs.map { it.id }) }
    }
}

/** Interactive 1–5 star rating row for Song, Album, Artist and Playlist menus. */
@Composable
fun StarRatingRow(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (star in 1..5) {
            val filled = star <= rating
            IconButton(
                onClick = { onRatingChange(if (rating == star) 0 else star) },
            ) {
                SymbolIcon(
                    Symbols.Star,
                    contentDescription = "Rate $star stars",
                    tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fill = if (filled) 1f else 0f,
                )
            }
        }
    }
}

/** Modal bottom sheet to create and share a Navidrome public link (`/share/<id>`). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavidromeShareSheet(
    title: String,
    onDismiss: () -> Unit,
    onCreateShare: suspend (description: String, downloadable: Boolean) -> ShareLink?,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var description by remember { mutableStateOf(title) }
    var downloadable by remember { mutableStateOf(true) }
    var createdShare by remember { mutableStateOf<ShareLink?>(null) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbols.Share, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Share via Navidrome", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            val share = createdShare
            if (share == null) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = downloadable, onCheckedChange = { downloadable = it })
                    Text("Allow visitors to download audio files")
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            creating = true
                            error = null
                            scope.launch {
                                val res = onCreateShare(description, downloadable)
                                creating = false
                                if (res != null) createdShare = res else error = "Sharing is disabled or failed on the server."
                            }
                        },
                        enabled = !creating,
                    ) {
                        Text(if (creating) "Creating…" else "Create Link")
                    }
                }
            } else {
                Text("Public Share URL:", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = share.url,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                    Button(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, share.description)
                                putExtra(Intent.EXTRA_TEXT, share.url)
                            }
                            context.startActivity(Intent.createChooser(send, "Share link"))
                        },
                    ) {
                        SymbolIcon(Symbols.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share URL")
                    }
                }
            }
        }
    }
}

/** Centered context menu for a [Song], plus add-to-playlist and Navidrome share sheets. */
@Composable
fun SongContextMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    showGoToAlbum: Boolean = true,
    showGoToArtist: Boolean = true,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    viewModel: MediaMenuViewModel = hiltViewModel(),
) {
    val favSongs by viewModel.favouriteSongs.collectAsStateWithLifecycle()
    val favourite = song.id in favSongs
    val openAlbum = LocalOpenAlbum.current
    val openArtist = LocalOpenArtist.current
    var playlistFor by remember { mutableStateOf<List<Song>?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var localRating by remember(song.id, song.userRating) { mutableIntStateOf(song.userRating) }

    CenteredDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Play next") },
            leadingIcon = { SymbolIcon(Symbols.PlayNext, null) },
            onClick = { onDismiss(); viewModel.playNextSong(song) },
        )
        DropdownMenuItem(
            text = { Text("Add to queue") },
            leadingIcon = { SymbolIcon(Symbols.QueueMusic, null) },
            onClick = { onDismiss(); viewModel.addToQueueSong(song) },
        )
        if (song.isRemote) {
            DropdownMenuItem(
                text = { Text("Start instant mix") },
                leadingIcon = { SymbolIcon(Symbols.AutoAwesome, null) },
                onClick = { onDismiss(); viewModel.startInstantMix(song) },
            )
        }
        DropdownMenuItem(
            text = { Text("Add to playlist") },
            leadingIcon = { SymbolIcon(Symbols.PlaylistAdd, null) },
            onClick = { onDismiss(); playlistFor = listOf(song) },
        )
        if (onRemoveFromPlaylist != null) {
            DropdownMenuItem(
                text = { Text("Remove from playlist") },
                leadingIcon = { SymbolIcon(Symbols.Delete, null) },
                onClick = { onDismiss(); onRemoveFromPlaylist() },
            )
        }
        DropdownMenuItem(
            text = { Text(if (favourite) "Remove from favourites" else "Favourite") },
            leadingIcon = { SymbolIcon(Symbols.Favorite, null, fill = if (favourite) 1f else 0f) },
            onClick = { onDismiss(); viewModel.toggleFavouriteSong(song) },
        )
        if (song.isRemote) {
            DropdownMenuItem(
                text = { Text(if (song.isDownloaded) "Remove offline download" else "Download for offline") },
                leadingIcon = { SymbolIcon(if (song.isDownloaded) Symbols.OfflinePin else Symbols.Download, null, fill = if (song.isDownloaded) 1f else 0f) },
                onClick = {
                    onDismiss()
                    if (song.isDownloaded) viewModel.removeSongDownload(song) else viewModel.downloadSong(song)
                },
            )
            DropdownMenuItem(
                text = { Text("Share via Navidrome") },
                leadingIcon = { SymbolIcon(Symbols.Share, null) },
                onClick = { onDismiss(); showShareSheet = true },
            )
        }
        if (showGoToAlbum && openAlbum != null && song.album.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("Go to album") },
                leadingIcon = { SymbolIcon(Symbols.Album, null) },
                onClick = {
                    onDismiss()
                    viewModel.resolveAlbum(song, openAlbum)
                },
            )
        }
        if (showGoToArtist && openArtist != null && song.artist.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("Go to artist") },
                leadingIcon = { SymbolIcon(Symbols.Artist, null) },
                onClick = { onDismiss(); openArtist(song.artist) },
            )
        }
        // Ratings are a Navidrome feature: local songs have none.
        if (song.isRemote) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            StarRatingRow(
                rating = localRating,
                onRatingChange = { r ->
                    localRating = r
                    viewModel.setSongRating(song, r)
                },
            )
        }
    }

    playlistFor?.let { songs ->
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        AddToPlaylistSheet(
            playlists = playlists,
            onPick = { viewModel.addToPlaylist(it.id, songs); playlistFor = null },
            onCreate = { viewModel.addToNewPlaylist(it, songs); playlistFor = null },
            onDismiss = { playlistFor = null },
        )
    }

    if (showShareSheet) {
        NavidromeShareSheet(
            title = "${song.artist} - ${song.title}",
            onDismiss = { showShareSheet = false },
            onCreateShare = { desc, dl -> viewModel.navidrome.createShareForSong(song, desc, downloadable = dl) },
        )
    }
}

/** Centered context menu for an [Album], plus add-to-playlist and Navidrome share sheets. */
@Composable
fun AlbumContextMenu(
    album: Album,
    expanded: Boolean,
    onDismiss: () -> Unit,
    showGoToArtist: Boolean = true,
    viewModel: MediaMenuViewModel = hiltViewModel(),
) {
    val favAlbums by viewModel.favouriteAlbums.collectAsStateWithLifecycle()
    val favourite = AlbumKey(album.id, album.title) in favAlbums
    val openArtist = LocalOpenArtist.current
    var playlistFor by remember { mutableStateOf<List<Song>?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var localRating by remember(album.id) { mutableIntStateOf(0) }

    CenteredDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Play") },
            leadingIcon = { SymbolIcon(Symbols.Play, null) },
            onClick = { onDismiss(); viewModel.playAlbum(album, shuffled = false) },
        )
        DropdownMenuItem(
            text = { Text("Shuffle") },
            leadingIcon = { SymbolIcon(Symbols.Shuffle, null) },
            onClick = { onDismiss(); viewModel.playAlbum(album, shuffled = true) },
        )
        DropdownMenuItem(
            text = { Text("Play next") },
            leadingIcon = { SymbolIcon(Symbols.PlayNext, null) },
            onClick = { onDismiss(); viewModel.playNextAlbum(album) },
        )
        DropdownMenuItem(
            text = { Text("Add to queue") },
            leadingIcon = { SymbolIcon(Symbols.QueueMusic, null) },
            onClick = { onDismiss(); viewModel.addToQueueAlbum(album) },
        )
        DropdownMenuItem(
            text = { Text("Add to playlist") },
            leadingIcon = { SymbolIcon(Symbols.PlaylistAdd, null) },
            onClick = {
                onDismiss()
                viewModel.loadAlbumSongs(album) { playlistFor = it }
            },
        )
        DropdownMenuItem(
            text = { Text(if (favourite) "Remove from favourites" else "Favourite") },
            leadingIcon = { SymbolIcon(Symbols.Favorite, null, fill = if (favourite) 1f else 0f) },
            onClick = { onDismiss(); viewModel.toggleFavouriteAlbum(album) },
        )
        if (album.id < 0L) {
            DropdownMenuItem(
                text = { Text("Download album") },
                leadingIcon = { SymbolIcon(Symbols.Download, null) },
                onClick = { onDismiss(); viewModel.downloadAlbum(album) },
            )
            DropdownMenuItem(
                text = { Text("Share via Navidrome") },
                leadingIcon = { SymbolIcon(Symbols.Share, null) },
                onClick = { onDismiss(); showShareSheet = true },
            )
        }
        if (showGoToArtist && openArtist != null && album.artist.isNotBlank()) {
            DropdownMenuItem(
                text = { Text("Go to artist") },
                leadingIcon = { SymbolIcon(Symbols.Artist, null) },
                onClick = { onDismiss(); openArtist(album.artist) },
            )
        }
        if (album.id < 0L) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            StarRatingRow(
                rating = localRating,
                onRatingChange = { r ->
                    localRating = r
                    viewModel.setAlbumRating(album, r)
                },
            )
        }
    }

    playlistFor?.let { songs ->
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        AddToPlaylistSheet(
            playlists = playlists,
            onPick = { viewModel.addToPlaylist(it.id, songs); playlistFor = null },
            onCreate = { viewModel.addToNewPlaylist(it, songs); playlistFor = null },
            onDismiss = { playlistFor = null },
        )
    }

    if (showShareSheet) {
        NavidromeShareSheet(
            title = "${album.artist} - ${album.title}",
            onDismiss = { showShareSheet = false },
            onCreateShare = { desc, dl -> viewModel.navidrome.createShareForAlbum(album.id, desc, downloadable = dl) },
        )
    }
}

/** Centered context menu for an artist tile/row. */
@Composable
fun ArtistContextMenu(
    artistName: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: MediaMenuViewModel = hiltViewModel(),
) {
    val favArtists by viewModel.favouriteArtists.collectAsStateWithLifecycle()
    val favourite = artistName in favArtists
    // Null until known, and for an artist that is not on a server (ratings are a Navidrome feature).
    val remoteRating by produceState<Int?>(null, artistName) { value = viewModel.navidrome.remoteArtistRating(artistName) }
    var localRating by remember(artistName) { mutableStateOf<Int?>(null) }

    CenteredDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Play") },
            leadingIcon = { SymbolIcon(Symbols.Play, null) },
            onClick = { onDismiss(); viewModel.playArtist(artistName, shuffled = false) },
        )
        DropdownMenuItem(
            text = { Text("Shuffle") },
            leadingIcon = { SymbolIcon(Symbols.Shuffle, null) },
            onClick = { onDismiss(); viewModel.playArtist(artistName, shuffled = true) },
        )
        DropdownMenuItem(
            text = { Text(if (favourite) "Remove from favourites" else "Favourite") },
            leadingIcon = { SymbolIcon(Symbols.Favorite, null, fill = if (favourite) 1f else 0f) },
            onClick = { onDismiss(); viewModel.toggleFavouriteArtist(artistName) },
        )
        if (remoteRating != null) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            StarRatingRow(
                rating = localRating ?: remoteRating ?: 0,
                onRatingChange = { r ->
                    localRating = r
                    viewModel.setArtistRating(artistName, r)
                },
            )
        }
    }
}

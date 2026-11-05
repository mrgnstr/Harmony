package com.mrgnstr.harmony.feature.album

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
import com.mrgnstr.harmony.core.designsystem.blur.chromeSource
import com.mrgnstr.harmony.core.designsystem.blur.rememberChromeBlurState
import com.mrgnstr.harmony.core.designsystem.bleedSides
import com.mrgnstr.harmony.core.designsystem.components.AddToPlaylistSheet
import com.mrgnstr.harmony.core.designsystem.components.AlbumTile
import com.mrgnstr.harmony.core.designsystem.components.albumCoverKey
import com.mrgnstr.harmony.core.designsystem.components.sharedCover
import com.mrgnstr.harmony.core.designsystem.components.CoverFrame
import com.mrgnstr.harmony.core.designsystem.components.HeaderGap
import com.mrgnstr.harmony.core.designsystem.components.PlayShuffleButtons
import com.mrgnstr.harmony.core.designsystem.components.SectionHeading
import com.mrgnstr.harmony.core.designsystem.components.ShelfTileWidth
import com.mrgnstr.harmony.core.designsystem.components.coverPast
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.theme.ColorSource
import com.mrgnstr.harmony.core.designsystem.theme.PageColorSource
import com.mrgnstr.harmony.core.designsystem.icons.DolbyLogo
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.FormatIcon
import com.mrgnstr.harmony.core.model.Song
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val BarRow = 64.dp

/**
 * An album: the whole app wears the cover's colours while it is open. The cover parallaxes, blurs and fades away as the page scrolls
 * (the top bar then takes over with a blurred surface and the album's title), under it the title, artist, quick tags, Play / Shuffle,
 * the track list (with disc headers), an info card, and shelves with other versions and more by the artist.
 */
@Composable
fun AlbumRoute(
    albumId: Long,
    title: String,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel<AlbumViewModel, AlbumViewModel.Factory>(
        key = "album:$albumId:$title",
        creationCallback = { it.create(albumId, title) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PageColorSource(state.accent?.let { ColorSource.Seed(it) })

    val listState = rememberLazyListState()
    val settled = rememberTransitionSettled()
    val blur = rememberChromeBlurState()
    val density = LocalDensity.current
    val sides = LocalSideInsets.current
    val statusTop = WindowInsets.statusBars.getTop(density)

    // Songs waiting for a playlist to be picked (a track, or the whole album).
    var playlistFor by remember { mutableStateOf<List<Song>?>(null) }

    // Everything the page draws, the top bar and its menu included, takes the theme's text colour: without it text and icons fall back to
    // the light theme's black in dark mode.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            LazyColumn(
                Modifier.fillMaxSize().chromeSource(blur),
                state = listState,
                contentPadding = sides.padding(bottom = LocalBottomBarInset.current + 16.dp),
            ) {
                item(key = "cover", contentType = "cover") {
                    CoverFrame(listState) {
                        state.songs.firstOrNull()?.let { ArtworkImage(it.id, it.albumId, Modifier.fillMaxSize().sharedCover(albumCoverKey(albumId, title))) }
                    }
                }
                if (state.loaded) {
                    item(key = "header", contentType = "header") { Header(state, onOpenArtist) }
                    item(key = "tags", contentType = "tags") { Tags(state) }
                    item(key = "actions", contentType = "actions") {
                        PlayShuffleButtons({ viewModel.play(state.songs, 0) }, { viewModel.shuffle(state.songs) }, Modifier.padding(top = 20.dp, bottom = 12.dp))
                    }
                    // Composing the rest while the page is still flying in costs frames; it follows once the transition has settled.
                    if (settled) {
                        trackList(state, viewModel, onOpenArtist) { playlistFor = it }
                        item(key = "info", contentType = "info") { InfoCard(state) }
                        if (state.otherVersions.isNotEmpty()) shelf("versions", "Other Versions", state.otherVersions, null, onOpenAlbum)
                        if (state.moreByArtist.isNotEmpty()) {
                            shelf("more", "More by ${state.artist}", state.moreByArtist, { onOpenArtist(state.artists.first()) }, onOpenAlbum)
                        }
                    }
                }
            }
            AlbumTopBar(
                title = state.title,
                fade = { ((listState.coverPast(density.density, listState.layoutInfo.viewportSize.width) - TitleFadeStart) / TitleFadeLength).coerceIn(0f, 1f) },
                blur = blur,
                statusTop = with(density) { statusTop.toDp() },
                onBack = onBack,
                menu = { close ->
                    DropdownMenuItem(
                        text = { Text("Play next") }, leadingIcon = { SymbolIcon(Symbols.PlayNext, null) },
                        onClick = { close(); viewModel.playNext(state.songs) },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") }, leadingIcon = { SymbolIcon(Symbols.QueueMusic, null) },
                        onClick = { close(); viewModel.addToQueue(state.songs) },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") }, leadingIcon = { SymbolIcon(Symbols.PlaylistAdd, null) },
                        onClick = { close(); playlistFor = state.songs },
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.favouriteAlbum) "Remove from favourites" else "Favourite") },
                        leadingIcon = { SymbolIcon(Symbols.Favorite, null, fill = if (state.favouriteAlbum) 1f else 0f) },
                        onClick = { close(); viewModel.toggleFavouriteAlbum() },
                    )
                },
            )
        }
        playlistFor?.let { songs ->
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            AddToPlaylistSheet(
                playlists,
                onPick = { viewModel.addToPlaylist(it.id, songs); playlistFor = null },
                onCreate = { viewModel.addToNewPlaylist(it, songs); playlistFor = null },
                onDismiss = { playlistFor = null },
            )
        }
    }
}

/** False while this page's own enter transition is running. */
@Composable
private fun rememberTransitionSettled(): Boolean {
    val transition = LocalNavAnimatedContentScope.current.transition
    return transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
}

// The bar's surface and title come in over the last stretch of the cover's fade.
private const val TitleFadeStart = 0.55f
private const val TitleFadeLength = 0.4f

/** Transparent over the cover (plain icon buttons); as the cover fades the blurred surface and the album's title come in. */
@Composable
private fun AlbumTopBar(
    title: String,
    fade: () -> Float,
    blur: com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState,
    statusTop: Dp,
    onBack: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(statusTop + BarRow)) {
        ChromeSurface(blur, RectangleShape, Modifier.matchParentSize().graphicsLayer { alpha = fade() }, container = MaterialTheme.colorScheme.surface)
        Box(Modifier.padding(top = statusTop).height(BarRow).fillMaxWidth().padding(horizontal = 64.dp).graphicsLayer { alpha = fade() }, contentAlignment = Alignment.CenterStart) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = statusTop).height(BarRow).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { SymbolIcon(Symbols.ArrowBack, "Back") }
            Box(Modifier.weight(1f))
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton({ open = true }) { SymbolIcon(Symbols.MoreVert, "More") }
                DropdownMenu(open, { open = false }) { menu { open = false } }
            }
        }
    }
}

@Composable
private fun Header(state: AlbumUiState, onOpenArtist: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = HeaderGap), horizontalAlignment = Alignment.CenterHorizontally) {
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        Text(state.title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        ArtistLink(state.artist, state.artists, onOpenArtist, Modifier.padding(top = 4.dp))
    }
}

/** The artist's name, in the page's accent; opens the artist, or a small menu to pick one when the credit names several. */
@Composable
private fun ArtistLink(text: String, artists: List<String>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            text,
            Modifier.clip(CircleShape).clickable { if (artists.size > 1) open = true else onOpen(artists.firstOrNull() ?: text) }.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        DropdownMenu(open, { open = false }) {
            artists.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onOpen(name) }) }
        }
    }
}

/** Genre, year and the audio formats as small tonal tags; the formats wear an icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(state: AlbumUiState) {
    val plain = listOfNotNull(state.genre, state.year.takeIf { it > 0 }?.toString())
    if (plain.isEmpty() && state.formats.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        plain.forEach { Tag(it, null) }
        state.formats.forEach { Tag(it.text, it.icon) }
    }
}

@Composable
private fun Tag(text: String, icon: FormatIcon?) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.padding(start = if (icon != null) 10.dp else 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            when (icon) {
                null -> Unit
                FormatIcon.Dolby -> DolbyLogo(Modifier.padding(end = 6.dp), size = 12.dp)
                FormatIcon.Surround -> SymbolIcon(Symbols.SurroundSound, null, Modifier.padding(end = 6.dp), size = 18.dp)
                FormatIcon.Lossless -> SymbolIcon(Symbols.Hd, null, Modifier.padding(end = 6.dp), size = 18.dp)
                FormatIcon.Lossy -> SymbolIcon(Symbols.MusicNote, null, Modifier.padding(end = 6.dp), size = 18.dp)
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

private fun LazyListScope.trackList(state: AlbumUiState, viewModel: AlbumViewModel, onOpenArtist: (String) -> Unit, onAddToPlaylist: (List<Song>) -> Unit) {
    val songs = state.songs
    songs.forEachIndexed { index, song ->
        if (state.hasMultipleDiscs && (index == 0 || songs[index - 1].discNumber != song.discNumber)) {
            item(key = "disc:${song.discNumber}", contentType = "disc") { DiscHeader(song.discNumber.coerceAtLeast(1)) }
        }
        item(key = "song:${song.id}", contentType = "track") {
            TrackRow(
                song, position = index + 1, playing = song.id == state.playingId, albumArtist = state.artist, favourite = song.id in state.favouriteSongs,
                onClick = { viewModel.play(songs, index) },
                onPlayNext = { viewModel.playNext(listOf(song)) },
                onAddToQueue = { viewModel.addToQueue(listOf(song)) },
                onAddToPlaylist = { onAddToPlaylist(listOf(song)) },
                onToggleFavourite = { viewModel.toggleFavourite(song) },
                onOpenArtist = { onOpenArtist(if (song.artist.equals(state.artist, true)) state.artists.first() else song.artist) },
            )
        }
    }
}

@Composable
private fun DiscHeader(number: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SymbolIcon(Symbols.Album, null, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Disc $number", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    song: Song,
    position: Int,
    playing: Boolean,
    albumArtist: String,
    favourite: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavourite: () -> Unit,
    onOpenArtist: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).combinedClickable(onLongClick = { menu = true }, onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            if (playing) SymbolIcon(Symbols.GraphicEq, "Playing", size = 20.dp, tint = accent)
            else Text((song.trackNumber.takeIf { it > 0 } ?: position).toString(), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title, style = MaterialTheme.typography.bodyLarge, color = if (playing) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // The artist line only appears when it differs from the album's (features, compilations).
            if (!song.artist.equals(albumArtist, ignoreCase = true)) {
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (favourite) SymbolIcon(Symbols.Favorite, "Favourite", Modifier.padding(start = 8.dp), size = 16.dp, tint = accent, fill = 1f)
        Text(clock(song.durationMs), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = muted)
        Box {
            IconButton({ menu = true }) { SymbolIcon(Symbols.MoreVert, "More", tint = muted) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { SymbolIcon(Symbols.PlayNext, null) }, onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { SymbolIcon(Symbols.QueueMusic, null) }, onClick = { menu = false; onAddToQueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, leadingIcon = { SymbolIcon(Symbols.PlaylistAdd, null) }, onClick = { menu = false; onAddToPlaylist() })
                DropdownMenuItem(
                    text = { Text(if (favourite) "Remove from favourites" else "Favourite") },
                    leadingIcon = { SymbolIcon(Symbols.Favorite, null, fill = if (favourite) 1f else 0f) },
                    onClick = { menu = false; onToggleFavourite() },
                )
                DropdownMenuItem(text = { Text("Go to artist") }, leadingIcon = { SymbolIcon(Symbols.Artist, null) }, onClick = { menu = false; onOpenArtist() })
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Album length, date added, and label / copyright when the tags have them. */
@Composable
private fun InfoCard(state: AlbumUiState) {
    val added = remember(state.addedSec) { DateFormat.getDateInstance(DateFormat.LONG).format(Date(state.addedSec * 1000)) }
    val count = state.songs.size
    val minutes = (state.durationMs / 60_000f).roundToInt().coerceAtLeast(1)
    val length = if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes ${if (minutes == 1) "minute" else "minutes"}"
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow("Length", "$length · $count ${if (count == 1) "song" else "songs"}")
            InfoRow("Added", added)
            state.credits?.label?.let { InfoRow("Label", it) }
            state.credits?.copyright?.let { InfoRow("Copyright", it) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun LazyListScope.shelf(key: String, heading: String, albums: List<Album>, onMore: (() -> Unit)?, onOpenAlbum: (Album) -> Unit) {
    item(key = "$key:heading", contentType = "heading") { SectionHeading(heading, onMore = onMore) }
    item(key = "$key:row", contentType = "shelf") {
        // Scrolls out to the display edges; rests 16 dp in from the content's side.
        val sides = LocalSideInsets.current
        LazyRow(Modifier.bleedSides(sides), contentPadding = sides.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { "$key:${it.id}:${it.title}" }, contentType = { "album" }) { album ->
                AlbumTile(album, { onOpenAlbum(album) }, Modifier.width(ShelfTileWidth), subtitle = album.year.takeIf { it > 0 }?.toString() ?: album.artist)
            }
        }
    }
}

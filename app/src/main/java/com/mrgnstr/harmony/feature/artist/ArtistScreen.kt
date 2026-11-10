@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.mrgnstr.harmony.feature.artist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.core.designsystem.*
import com.mrgnstr.harmony.core.designsystem.blur.*
import com.mrgnstr.harmony.core.designsystem.components.*
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.theme.ColorSource
import com.mrgnstr.harmony.core.designsystem.theme.PageColorSource
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.ArtistReleaseShelf
import com.mrgnstr.harmony.core.model.SimilarArtistRef
import com.mrgnstr.harmony.core.model.Song

@Composable
fun ArtistRoute(
    name: String,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onAllSongs: (Int?) -> Unit,
    onInfo: (Int?) -> Unit,
    onOpenShelf: (ArtistReleaseShelf, Int?) -> Unit,
    onSimilar: (Int?) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(key = "artist:$name", creationCallback = { it.create(name) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openInfo = { onInfo(state.accent) }
    val openAllSongs = { onAllSongs(state.accent) }
    val error by viewModel.imageError.collectAsStateWithLifecycle()
    PageColorSource(state.accent?.let { ColorSource.Seed(it) })
    val list = rememberLazyListState()
    val blur = rememberChromeBlurState()
    val density = LocalDensity.current
    val status = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.setImage(it.toString()) } }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.setImage(it.toString(), logo = true) } }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            LazyColumn(
                Modifier.fillMaxSize().chromeSource(blur), state = list,
                contentPadding = LocalSideInsets.current.padding(bottom = LocalBottomBarInset.current + 24.dp),
            ) {
                item("hero", contentType = "hero") { ArtistHero(state, list) }
                item("actions", contentType = "actions") {
                    ArtistActions(openInfo, { viewModel.play(state.songs) }, viewModel::shuffle, state.songs.isNotEmpty())
                }
                if (state.loaded) {
                    state.latest?.let { album -> item("latest", contentType = "latest") {
                        LatestRelease(album, { onOpenAlbum(album) }, { viewModel.playRelease(album) })
                    } }
                    if (state.rankedSongs.isNotEmpty()) {
                        item("top:title", contentType = "heading") {
                            SectionHeading("Top Songs", onMore = openAllSongs)
                        }
                        // All of them, ranked, in columns of five that scroll sideways.
                        item("top:shelf", contentType = "shelf") {
                            ColumnsShelf(state.rankedSongs, key = { "top:${it.id}" }) { song, i ->
                                ArtistSongRow(song, song.id == state.playingId, { viewModel.play(state.rankedSongs, i) }, viewModel)
                            }
                        }
                    }
                    ArtistReleaseShelf.entries.forEach { section ->
                        releaseShelf(section.title, state.releases(section), onOpenAlbum) { onOpenShelf(section, state.accent) }
                    }
                    val similar = state.remoteExtra?.similarArtists.orEmpty()
                    if (similar.isNotEmpty()) {
                        item("similar:heading", contentType = "heading") { SectionHeading("Similar Artists", onMore = { onSimilar(state.accent) }) }
                        item("similar:row", contentType = "shelf") {
                            val openArtist = LocalOpenArtist.current
                            val sides = LocalSideInsets.current
                            LazyRow(Modifier.bleedSides(sides), contentPadding = sides.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(similar, key = { "sim:${it.id}:${it.name}" }) { sim ->
                                    ArtistShelfTile(shelfItem(sim, state.libraryArtists), { openArtist?.invoke(sim.name) }, Modifier.width(ArtistShelfTileWidth))
                                }
                            }
                        }
                    }
                    if (state.songs.isEmpty()) item("empty") {
                        Text("No releases in your library", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else item("loading") { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            }
            // A light scrim keeps the plain navigation icons legible over a bright hero.
            Box(Modifier.fillMaxWidth().height(status + 80.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent))))
            DetailTopBar(
                state.name,
                fade = {
                    if (list.firstVisibleItemIndex > 0) 1f else {
                        val width = list.layoutInfo.viewportSize.width.coerceAtLeast(1)
                        ((list.firstVisibleItemScrollOffset / width.toFloat() - 0.55f) / 0.35f).coerceIn(0f, 1f)
                    }
                },
                blur = blur, statusTop = status, onBack = onBack, overArtwork = true,
                menu = { close ->
                    DropdownMenuItem(text = { Text("Artist info") }, leadingIcon = { SymbolIcon(Symbols.Info, null) }, onClick = { close(); openInfo() })
                    DropdownMenuItem(text = { Text(if (state.favourite) "Remove from favourites" else "Favourite") }, leadingIcon = { SymbolIcon(Symbols.Favorite, null, fill = if (state.favourite) 1f else 0f) }, onClick = { close(); viewModel.toggleFavourite() })
                    DropdownMenuItem(text = { Text("Choose artist image") }, leadingIcon = { SymbolIcon(Symbols.Image, null) }, onClick = { close(); photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    if (state.customImage) DropdownMenuItem(text = { Text("Use default image") }, onClick = { close(); viewModel.clearImage() })
                    DropdownMenuItem(text = { Text("Choose title logo") }, leadingIcon = { SymbolIcon(Symbols.Edit, null) }, onClick = { close(); logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    if (state.logoPath != null) DropdownMenuItem(text = { Text("Use default title") }, onClick = { close(); viewModel.clearImage(logo = true) })
                    if (state.remoteExtra != null) {
                        DropdownMenuItem(text = { Text("Refresh server metadata") }, leadingIcon = { SymbolIcon(Symbols.Refresh, null) }, onClick = { close(); viewModel.refreshServerMetadata() })
                    }
                },
            )
        }
        error?.let { message -> AlertDialog(onDismissRequest = { viewModel.imageError.value = null }, title = { Text("Image unavailable") }, text = { Text(message) }, confirmButton = { TextButton({ viewModel.imageError.value = null }) { Text("OK") } }) }
    }
}

/** Same expansion ratio and spring as M3 ButtonGroup, with the artist row's asymmetric space sharing. */
@Composable
private fun ArtistActions(onInfo: () -> Unit, onPlay: () -> Unit, onShuffle: () -> Unit, enabled: Boolean) {
    val infoInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val shuffleInteraction = remember { MutableInteractionSource() }
    val infoPressed by infoInteraction.collectIsPressedAsState()
    val playPressed by playInteraction.collectIsPressedAsState()
    val shufflePressed by shuffleInteraction.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val infoExpansion = animateFloatAsState(if (infoPressed) 1f else 0f, motion, label = "artist-info-width")
    val playExpansion = animateFloatAsState(if (playPressed) 1f else 0f, motion, label = "artist-play-width")
    val shuffleExpansion = animateFloatAsState(if (shufflePressed) 1f else 0f, motion, label = "artist-shuffle-width")
    Layout(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp),
        content = {
            FilledTonalIconButton(onInfo, interactionSource = infoInteraction, shapes = IconButtonDefaults.shapes()) {
                SymbolIcon(Symbols.Info, "Artist info")
            }
            Button(onPlay, enabled = enabled, interactionSource = playInteraction, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.MediumContentPadding) {
                SymbolIcon(Symbols.Play, null, fill = 1f)
                Spacer(Modifier.width(8.dp))
                Text("Play", style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalIconButton(onShuffle, enabled = enabled, interactionSource = shuffleInteraction, shapes = IconButtonDefaults.shapes()) {
                SymbolIcon(Symbols.Shuffle, "Shuffle artist")
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = 56.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        // An extra 16 dp inside the outer 16 dp gives the resting 32 dp inset. Play can borrow this extra space,
        // but its spring (including overshoot) never carries either outer button closer than 16 dp to the edge.
        val inset = 16.dp.toPx()
        val side = 56.dp.toPx()
        val restingPlay = (width - 2 * inset - 2 * side - 2 * gap).coerceAtLeast(0f)
        val ratio = ButtonGroupDefaults.ExpandedRatio
        val playExtra = (restingPlay * ratio * playExpansion.value).coerceIn(-2 * inset, 2 * inset)
        val infoWidth = (side * (1f + ratio * infoExpansion.value)).roundToInt().coerceAtLeast(1)
        val shuffleWidth = (side * (1f + ratio * shuffleExpansion.value)).roundToInt().coerceAtLeast(1)
        val infoX = (inset - playExtra / 2f).roundToInt()
        val shuffleX = width - infoX - shuffleWidth
        val playX = infoX + infoWidth + gap
        val playWidth = (shuffleX - gap - playX).coerceAtLeast(1)
        val info = measurables[0].measure(Constraints.fixed(infoWidth, height))
        val play = measurables[1].measure(Constraints.fixed(playWidth, height))
        val shuffle = measurables[2].measure(Constraints.fixed(shuffleWidth, height))
        layout(width, height) {
            info.placeRelative(infoX, 0)
            play.placeRelative(playX, 0)
            shuffle.placeRelative(shuffleX, 0)
        }
    }
}

@Composable
private fun LatestRelease(album: Album, onOpen: () -> Unit, onPlay: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuInitialized by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val share = LocalCoverShare.current
    val owner = remember { Any() }
    val coverKey = albumCoverKey(album.id, album.title)
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier
                    .combinedClickable(
                        onClick = { share?.claim(coverKey, owner); onOpen() },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuInitialized = true
                            menuOpen = true
                        },
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(album.artSongId, album.id, Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).sharedCover(coverKey, owner))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Latest Release", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(album.year.takeIf { it > 0 }?.toString(), "${album.songCount} ${if (album.songCount == 1) "song" else "songs"}").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledIconButton(onPlay, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) { SymbolIcon(Symbols.Play, "Play ${album.title}", fill = 1f) }
            }
        }
        if (menuInitialized) {
            AlbumContextMenu(
                album = album,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                showGoToArtist = false,
            )
        }
    }
}

@Composable
private fun ArtistSongRow(song: Song, playing: Boolean, onPlay: () -> Unit, @Suppress("UNUSED_PARAMETER") viewModel: ArtistViewModel) {
    SongRow(
        song = song,
        onClick = onPlay,
        subtitle = listOfNotNull(song.album, song.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
        playing = playing,
        showGoToArtist = false,
    )
}

private fun LazyListScope.releaseShelf(title: String, albums: List<Album>, onOpen: (Album) -> Unit, onMore: () -> Unit) {
    if (albums.isEmpty()) return
    item("$title:heading", contentType = "heading") { SectionHeading(title, onMore = onMore) }
    item("$title:row", contentType = "shelf") {
        val sides = LocalSideInsets.current
        LazyRow(Modifier.bleedSides(sides), contentPadding = sides.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { "${it.id}:${it.title}" }) { album -> AlbumTile(album, { onOpen(album) }, Modifier.width(ShelfTileWidth), subtitle = album.year.takeIf { it > 0 }?.toString(), showGoToArtist = false) }
        }
    }
}

@Composable
fun ArtistSongsRoute(
    name: String, onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(key = "artist:$name:songs", creationCallback = { it.create(name) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PageColorSource(state.accent?.let { ColorSource.Seed(it) })
    TopBarScaffold("Top Songs", subtitle = name, onBack = onBack) {
        LazyColumn(contentPadding = LocalSideInsets.current.padding(top = LocalTopBarInset.current, bottom = LocalBottomBarInset.current + 24.dp)) {
            itemsIndexed(state.rankedSongs, key = { _, song -> song.id }) { i, song -> ArtistSongRow(song, song.id == state.playingId, { viewModel.play(state.rankedSongs, i) }, viewModel) }
        }
    }
}

@Composable
fun ArtistInfoRoute(
    name: String, onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(key = "artist:$name:info", creationCallback = { it.create(name) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastFm by viewModel.lastFm.collectAsStateWithLifecycle()
    PageColorSource(state.accent?.let { ColorSource.Seed(it) })
    TopBarScaffold(name, onBack = onBack) {
        val uriHandler = LocalUriHandler.current
        LazyColumn(contentPadding = LocalSideInsets.current.padding(horizontal = 20.dp, top = LocalTopBarInset.current + 16.dp, bottom = LocalBottomBarInset.current + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val years = state.songs.map { it.year }.filter { it > 0 }
            val minutes = state.songs.sumOf { it.durationMs } / 60_000
            // Short facts sit two to a row; the long ones get a full row under them.
            val counts = listOfNotNull(
                "Songs" to state.songs.size.toString(),
                "Albums" to state.albums.size.toString(),
                "Singles & EPs" to state.singles.size.toString(),
                years.takeIf { it.isNotEmpty() }?.let { "Release years" to (if (it.min() == it.max()) "${it.min()}" else "${it.min()}–${it.max()}") },
                "Music length" to (if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"),
            )
            val details = listOfNotNull(
                state.songs.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct().takeIf { it.isNotEmpty() }?.let { "Genres" to it.joinToString() },
                state.remoteExtra?.roles?.takeIf { it.isNotEmpty() }?.let { "Roles" to it.joinToString { r -> r.replaceFirstChar(Char::uppercase) } },
                state.remoteExtra?.mbzArtistId?.takeIf { it.isNotBlank() }?.let { "MusicBrainz ID" to it },
            )
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("In Your Library", style = MaterialTheme.typography.titleMedium)
                        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            counts.forEach { (label, value) -> InfoFact(label, value, Modifier.weight(1f)) }
                        }
                        details.forEach { (label, value) -> InfoFact(label, value, Modifier.fillMaxWidth()) }
                    }
                }
            }
            // The server's biography for its own artists; Last.fm's (when asked for in Settings) for local ones.
            val bio = state.remoteExtra?.biography?.takeIf { it.isNotBlank() } ?: lastFm?.text?.takeIf { state.remoteExtra == null }
            val bioUrl = (state.remoteExtra?.externalUrl ?: lastFm?.url.takeIf { state.remoteExtra == null })?.takeIf { it.isNotBlank() }
            bio?.let { bio ->
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Biography", style = MaterialTheme.typography.titleMedium)
                            Text(bio, style = MaterialTheme.typography.bodyLarge)
                            // The server passes on only the short summary from Last.fm; the link is the rest of it.
                            bioUrl?.let { url ->
                                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Read more on Last.fm") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun ArtistUiState.releases(shelf: ArtistReleaseShelf): List<Album> = when (shelf) {
    ArtistReleaseShelf.Albums -> albums
    ArtistReleaseShelf.Singles -> singles
    ArtistReleaseShelf.FeaturedOn -> featuredOn
}

@Composable
fun ArtistReleasesRoute(
    name: String, shelf: ArtistReleaseShelf, onBack: () -> Unit, onOpenAlbum: (Album) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(
        key = "artist:$name:releases:$shelf", creationCallback = { it.create(name) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PageColorSource(state.accent?.let { ColorSource.Seed(it) })
    AlbumGridPage(shelf.title, name, state.releases(shelf), state.loaded, onBack, onOpenAlbum)
}

/** A library artist wears its own picture like every other artist tile; one the library lacks, the server's. */
private fun shelfItem(sim: SimilarArtistRef, library: Map<String, Artist>) =
    ArtistShelfItem(sim.name, sim.albumCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "album" else "albums"}" }, library[sim.name.lowercase()], sim.imageUrl)

@Composable
fun SimilarArtistsRoute(
    name: String, onBack: () -> Unit, onOpenArtist: (String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(
        key = "artist:$name:similar", creationCallback = { it.create(name) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PageColorSource(state.accent?.let { ColorSource.Seed(it) })
    val similar = state.remoteExtra?.similarArtists.orEmpty()
    ArtistGridPage("Similar Artists", name, similar.map { shelfItem(it, state.libraryArtists) }, state.loaded, onBack, onOpenArtist)
}

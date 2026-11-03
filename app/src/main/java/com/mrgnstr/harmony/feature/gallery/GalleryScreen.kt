package com.mrgnstr.harmony.feature.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.model.BlurMode
import com.mrgnstr.harmony.core.designsystem.components.AlbumTile
import com.mrgnstr.harmony.core.designsystem.components.PageScaffold
import com.mrgnstr.harmony.core.designsystem.components.PlayShuffleButtons
import com.mrgnstr.harmony.core.designsystem.components.SectionHeading
import com.mrgnstr.harmony.core.designsystem.components.ShelfTileWidth
import com.mrgnstr.harmony.core.designsystem.components.SongRow
import com.mrgnstr.harmony.core.designsystem.icons.AnimatedSymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import com.mrgnstr.harmony.core.designsystem.motion.rubberBand
import com.mrgnstr.harmony.core.designsystem.theme.ColorSource
import com.mrgnstr.harmony.core.designsystem.theme.PageColorSource
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GalleryViewModel @Inject constructor(
    library: LibraryRepository,
    private val player: PlayerConnection,
    private val settings: SettingsStore,
) : ViewModel() {
    val chromeBlur: StateFlow<BlurMode> = settings.glassBlur.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlurMode.Auto)

    fun setChromeBlur(mode: BlurMode) { viewModelScope.launch { settings.setGlassBlur(mode) } }

    val albums: StateFlow<List<Album>> = library.albums.map { it.take(12) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val songs: StateFlow<List<Song>> = library.songs.map { it.take(5) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(songs: List<Song>, index: Int) = player.playQueue(songs, index, source = "Gallery")
}

private val Seeds = listOf(
    "Wallpaper" to null,
    "Red" to Color(0xFFD32F2F),
    "Green" to Color(0xFF2E7D32),
    "Blue" to Color(0xFF1565C0),
    "Amber" to Color(0xFFFFA000),
)

private val GallerySymbols = listOf(
    Symbols.Home, Symbols.Library, Symbols.Search, Symbols.Favorite, Symbols.Star, Symbols.Album,
    Symbols.Artist, Symbols.QueueMusic, Symbols.Settings, Symbols.Lyrics, Symbols.Shuffle, Symbols.Repeat,
)

/** Debug page for M2: page colour sources, components, symbols and motion primitives. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GalleryRoute(onBack: () -> Unit, viewModel: GalleryViewModel = hiltViewModel()) {
    var seedIndex by rememberSaveable { mutableStateOf(1) }
    PageColorSource(Seeds[seedIndex].second?.let { ColorSource.Seed(it.toArgb()) } ?: ColorSource.Wallpaper)
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val chromeBlur by viewModel.chromeBlur.collectAsStateWithLifecycle()

    PageScaffold(title = "Design gallery", subtitle = "M2 building blocks", onBack = onBack) {
        item { SectionHeading("Page colour") }
        item {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Seeds.forEachIndexed { i, (name, _) ->
                    ToggleButton(checked = i == seedIndex, onCheckedChange = { seedIndex = i }) { Text(name) }
                }
            }
        }
        item { Swatches() }
        item { SectionHeading("Chrome blur") }
        item {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlurMode.entries.forEach { mode ->
                    ToggleButton(checked = mode == chromeBlur, onCheckedChange = { viewModel.setChromeBlur(mode) }) { Text(mode.name) }
                }
            }
        }
        item { SectionHeading("Buttons") }
        item { PlayShuffleButtons(onPlay = { viewModel.play(songs, 0) }, onShuffle = { viewModel.play(songs.shuffled(), 0) }) }
        item { SectionHeading("Albums", onMore = {}) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(albums, key = { "${it.id}:${it.title}" }) { AlbumTile(it, onClick = {}, Modifier.width(ShelfTileWidth)) }
            }
        }
        item { SectionHeading("Songs") }
        items(songs.size) { i -> SongRow(songs[i], onClick = { viewModel.play(songs, i) }, onMore = {}) }
        item { SectionHeading("Material Symbols") }
        item { SymbolsDemo() }
        item { SectionHeading("Motion") }
        item { RubberBandDemo() }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator()
                ContainedLoadingIndicator()
            }
        }
    }
}

@Composable
private fun Swatches() {
    val c = MaterialTheme.colorScheme
    val roles = listOf(
        "primary" to c.primary, "primaryContainer" to c.primaryContainer, "secondary" to c.secondary,
        "secondaryContainer" to c.secondaryContainer, "tertiary" to c.tertiary, "tertiaryContainer" to c.tertiaryContainer,
        "surfaceContainer" to c.surfaceContainer, "surfaceContainerHighest" to c.surfaceContainerHighest,
    )
    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        roles.forEach { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(color))
                Text(name.take(12), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
    }
}

/** Tap a symbol to morph its fill; the slider drives the weight axis (API 26+). */
@Composable
private fun SymbolsDemo() {
    var weight by remember { mutableFloatStateOf(400f) }
    val filled = remember { mutableStateOf(setOf<Int>()) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GallerySymbols.forEachIndexed { i, symbol ->
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable { filled.value = filled.value.let { if (i in it) it - i else it + i } },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedSymbolIcon(symbol, null, filled = i in filled.value, weight = weight.toInt(), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SymbolIcon(Symbols.Tune, null, weight = weight.toInt())
            Slider(value = weight, onValueChange = { weight = it }, valueRange = 100f..700f, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/**
 * A puck on a track: it follows the finger, rubber-bands past the ends, and on release is thrown with the finger's
 * velocity to the nearest of three stops.
 */
@Composable
private fun RubberBandDemo() {
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    val stretch = remember { Animatable(1f) }
    val puck = 56.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(puck)) {
        val travel = with(LocalDensity.current) { (maxWidth - puck).toPx() }
        val stops = listOf(0f, travel / 2, travel)
        Box(Modifier.fillMaxWidth().height(puck).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Box(
            Modifier
                .size(puck)
                .graphicsLayer {
                    translationX = x.value
                    scaleX = stretch.value
                    scaleY = 2f - stretch.value
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(travel) {
                    val tracker = VelocityTracker()
                    var raw = 0f
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking(); raw = x.value; scope.launch { stretch.animateTo(1.12f, Springs.press(0.001f)) } },
                        onDrag = { change, drag: Offset ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            raw += drag.x
                            val shown = when {
                                raw < 0f -> rubberBand(raw, 60f)
                                raw > travel -> travel + rubberBand(raw - travel, 60f)
                                else -> raw
                            }
                            scope.launch { x.snapTo(shown) }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            val landing = x.value + v * 0.15f
                            val target = stops.minBy { kotlin.math.abs(it - landing) }
                            scope.launch { stretch.animateTo(1f, Springs.release(0.001f)) }
                            scope.launch { x.animateTo(target, Springs.toss(visibilityThreshold = 0.5f), initialVelocity = v) }
                        },
                    )
                },
        )
    }
}

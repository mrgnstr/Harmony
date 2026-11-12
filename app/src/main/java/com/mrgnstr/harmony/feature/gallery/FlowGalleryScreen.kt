package com.mrgnstr.harmony.feature.gallery

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.artwork.ArtworkDecoder
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.components.PageScaffold
import com.mrgnstr.harmony.core.designsystem.components.SongRow
import com.mrgnstr.harmony.core.designsystem.flow.FlowBackground
import com.mrgnstr.harmony.core.designsystem.flow.FlowParams
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FlowGalleryViewModel @Inject constructor(
    library: LibraryRepository,
    settings: SettingsStore,
) : ViewModel() {
    val songs: StateFlow<List<Song>> = library.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The classic Now Playing cover radius, so the prototype's cover matches. */
    val coverRadius: StateFlow<Int> = settings.classicCoverRadius.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 15)
}

/** Debug: the Apple Music-style flow background prototype. Pick a song, see it with the classic Now Playing cover over the background. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun FlowGalleryRoute(onBack: () -> Unit, viewModel: FlowGalleryViewModel = hiltViewModel()) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val radius by viewModel.coverRadius.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Song?>(null) }

    Box(Modifier.fillMaxSize()) {
        PageScaffold(title = "Flow background", subtitle = "Apple Music-style, prototype", onBack = onBack) {
            items(songs.size, key = { songs[it].id }) { i ->
                SongRow(songs[i], onClick = { selected = songs[i] }, showGoToAlbum = false, showGoToArtist = false)
            }
        }
        selected?.let { song ->
            BackHandler { selected = null }
            FlowViewer(song, radius, onClose = { selected = null })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FlowViewer(song: Song, coverRadius: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    // Apple shrinks the artwork to a 300 px long edge before using it.
    val cover by produceState<Bitmap?>(null, song.id, song.albumId) {
        value = ArtworkDecoder.decodeAsync(context, song.id, song.albumId, 300)
    }
    var dark by remember { mutableStateOf(true) }
    var mesh by remember { mutableIntStateOf(0) }
    var interval by remember { mutableFloatStateOf(0.5f) }
    var patch by remember { mutableFloatStateOf(1.25f) }
    var drift by remember { mutableFloatStateOf(1f) }
    val ink = if (dark) Color.White else Color(0xFF1C1C1E)

    Box(Modifier.fillMaxSize()) {
        FlowBackground(cover, Modifier.fillMaxSize(), FlowParams(dark = dark, mesh = mesh, interval = interval, scale = patch, drift = drift), background = Color.Black)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { SymbolIcon(Symbols.Close, "Close", tint = ink) }
            }
            // The classic layout: the cover inset by 28 dp, rounded by the user's radius, title and artist under it.
            ArtworkImage(
                song.id, song.albumId,
                Modifier.padding(horizontal = 28.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(coverRadius.dp)),
            )
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                Text(song.title, color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = ink.copy(alpha = 0.7f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(checked = dark, onCheckedChange = { dark = true }) { Text("Dark") }
                    ToggleButton(checked = !dark, onCheckedChange = { dark = false }) { Text("Light") }
                    repeat(5) { i -> ToggleButton(checked = mesh == i, onCheckedChange = { mesh = i }) { Text("Mesh ${i + 1}") } }
                }
                Text("Speed: interval ${"%.2f".format(interval)} (smaller is faster)", color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
                Slider(interval, { interval = it }, valueRange = 0.1f..3f)
                Text("Patch size: x${"%.2f".format(patch)} (1.25 matches Apple Music for Android)", color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
                Slider(patch, { patch = it }, valueRange = 0.8f..2.2f)
                Text("Mesh drift: ${"%.1f".format(drift)} (0 = built-in morph only: sharp spots stay put)", color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
                Slider(drift, { drift = it }, valueRange = 0f..4f)
            }
            // Clear of the floating mini player and tab bar.
            Spacer(Modifier.height(LocalBottomBarInset.current + 16.dp))
        }
    }
}

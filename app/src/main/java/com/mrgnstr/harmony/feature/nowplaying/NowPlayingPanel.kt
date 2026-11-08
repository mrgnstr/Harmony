package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.liveChromeBlur
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.mrgnstr.harmony.core.playback.PlaybackSnapshot
import kotlinx.coroutines.delay

/** The views that replace the cover in Now Playing: the cover shrinks into a header and the view takes its place. */
enum class NowPlayingPanel { None, Lyrics, Queue }

/** Side of the small header cover. */
internal val PanelCoverSize = 76.dp
internal val PanelSide = 24.dp
/** Gap between the status bar and the header. */
internal val PanelHeaderTop = 28.dp
internal val PanelHeaderGap = 12.dp

/** How long the controls stay after the last touch while a song plays in a panel. */
private const val ControlsHideDelayMs = 5_000L

/**
 * What the lyrics and queue views share: how far the panel is open ([q]), the controls that fade away over it, and where the
 * controls start (so the view can fade out above them). Opening is its own spring, on top of the sheet's.
 */
@Stable
class PanelState internal constructor(initial: NowPlayingPanel) {
    /** The panel asked for (changes at once; what is drawn follows [open]). */
    var panel by mutableStateOf(initial)
        private set

    /** 0 = the cover, 1 = the panel. */
    val open = Animatable(if (initial == NowPlayingPanel.None) 0f else 1f)

    /** 0 = controls shown, 1 = hidden. */
    val controlsHidden = Animatable(0f)

    /** Top of the seek bar (screen px): the lyrics fade out above it. */
    var controlsTop by mutableFloatStateOf(0f)

    /** The panel's content is the source of the blur behind the codec pill and the translation button. */
    val frost = ChromeBlurState(HazeState())

    /** Where the pill and the button are on the screen: the sharp lyrics are cut out under them (the blurred ones show there). */
    var pillRect by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    var buttonRect by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /** Counts every touch, to restart the hide timer. */
    var touchTick by mutableIntStateOf(0)
    internal var revealNext = false

    var showTranslation by mutableStateOf(true)
    var showPronunciation by mutableStateOf(true)

    /** The sheet's own progress; the panel gives way as the player collapses, so the cover finds its way back to the mini player. */
    internal var sheetProgress: () -> Float = { 1f }

    /** [open] as drawn: scaled down while the sheet is not fully open. */
    fun q(): Float = open.value * ((sheetProgress() - 0.7f) / 0.3f).coerceIn(0f, 1f)

    /** Composed while it is on screen or on its way. */
    val visible: Boolean get() = panel != NowPlayingPanel.None || open.value > 0f

    suspend fun toggle(target: NowPlayingPanel) {
        if (panel == target) {
            panel = NowPlayingPanel.None
            open.animateTo(0f, PanelSpring)
        } else {
            panel = target
            open.animateTo(1f, PanelSpring)
        }
    }

    suspend fun reset() {
        panel = NowPlayingPanel.None
        open.snapTo(0f)
        controlsHidden.snapTo(0f)
    }

    private companion object {
        val PanelSpring = Springs.land<Float>(stiffness = 260f, visibilityThreshold = 0.001f)
    }
}

@Composable
fun rememberPanelState(): PanelState = rememberSaveable(
    saver = Saver(save = { it.panel.ordinal }, restore = { PanelState(NowPlayingPanel.entries[it]) }),
) { PanelState(NowPlayingPanel.None) }

/**
 * Hides the controls [ControlsHideDelayMs] after the last touch while a song plays in the lyrics, and brings them back for any touch
 * in the bottom part of the screen (below [zoneTop]). Paused, they stay.
 */
@Composable
internal fun ControlsAutoHide(panel: PanelState, playing: Boolean) {
    val on = panel.panel == NowPlayingPanel.Lyrics
    LaunchedEffect(on, playing, panel.touchTick) {
        val hidden = panel.controlsHidden
        val reveal = panel.revealNext || !on || !playing
        panel.revealNext = false
        val h = hidden.value
        if (reveal && h > 0f) hidden.animateTo(0f, tween(250))
        else if (!reveal && h > 0f && h < 1f) hidden.animateTo(if (h < 0.5f) 0f else 1f, tween(250))
        if (on && playing) {
            delay(ControlsHideDelayMs)
            hidden.animateTo(1f, tween(350))
        }
    }
}

/** Sees every touch first (without taking it) to restart the hide timer; a touch below [zoneTop] while hidden brings the controls back and is used up. */
internal fun Modifier.panelTouches(panel: PanelState, zoneTop: () -> Float): Modifier = pointerInput(panel) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.firstOrNull { it.changedToDown() } ?: continue
            if (panel.panel == NowPlayingPanel.None) continue
            if (panel.controlsHidden.value > 0.5f && down.position.y >= zoneTop()) {
                panel.revealNext = true
                event.changes.forEach { it.consume() }
            }
            panel.touchTick++
        }
    }
}

/** Height of the title row in the full player (title, artist and the heart); the controls keep this much room for it. */
internal val TitleRowHeight = 56.dp

/**
 * Title, artist and heart: in the full player where the controls start, and in a panel beside the small cover. [q] moves it between
 * the two (the sheet puts it at the controls' top, [headerCenterY] is where the row's centre goes in a panel); the text shrinks
 * to the panel's sizes and its room narrows as it moves right, so nothing overlaps on the way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayingTitle(
    snap: PlaybackSnapshot,
    favourite: Boolean,
    onFavourite: () -> Unit,
    panel: PanelState,
    headerShift: Float,
    artists: List<String>,
    onOpenAlbum: () -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCoverColors.current
    // It lags behind the panel: it travels further than the cover's bottom edge, which it must not run into on the way up.
    val q = panel.q().let { it * it }
    val big = MaterialTheme.typography.headlineSmallEmphasized
    val title = big.copy(fontWeight = FontWeight.Bold, fontSize = lerp(big.fontSize, 20.sp, q), lineHeight = lerp(big.lineHeight, 26.sp, q))
    val sub = MaterialTheme.typography.titleMedium
    val artist = sub.copy(fontSize = lerp(sub.fontSize, 16.sp, q), lineHeight = lerp(sub.lineHeight, 22.sp, q))
    Row(
        modifier
            .fillMaxWidth()
            .height(TitleRowHeight)
            .graphicsLayer { translationY = headerShift * panel.q().let { it * it } }
            .padding(horizontal = PanelSide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Room for the small cover grows as the panel opens.
        Spacer(Modifier.layout { _, _ -> layout(((PanelCoverSize + PanelHeaderGap).roundToPx() * panel.q().let { it * it }).roundToInt(), 0) {} })
        Column(Modifier.weight(1f)) {
            // The title opens the album; the artist opens the artist, or a menu to pick one when the credit names several.
            Box(Modifier.clickable(interactionSource = null, indication = null) { onOpenAlbum() }) { MarqueeText(snap.title, title, c.text) }
            var picking by remember { mutableStateOf(false) }
            Box(
                Modifier.clickable(interactionSource = null, indication = null) {
                    if (artists.size > 1) picking = true else onOpenArtist(artists.firstOrNull() ?: snap.artist)
                },
            ) {
                MarqueeText(snap.artist, artist, c.textSoft)
                NowPlayingPopupTheme {
                    DropdownMenu(picking, { picking = false }) {
                        artists.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { picking = false; onOpenArtist(name) }) }
                    }
                }
            }
        }
        FavouriteToggle(favourite, onFavourite)
    }
}

/** Placed (drawn, touchable) only while [shown]; still measured, so it keeps its space and stays composed. Read at placement time. */
internal fun Modifier.placedWhen(shown: androidx.compose.runtime.State<Boolean>): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (shown.value) placeable.place(0, 0) }
}

/**
 * Under the codec pill and the translation button, while a panel is open: a blur of the panel's content (the lyrics behind them),
 * with nothing under it, so where there is no text it is just the button's own tint. Only where live blur is on; elsewhere the
 * lyrics are simply not cut out and show behind.
 */
@Composable
internal fun FrostLayer(panel: PanelState, modifier: Modifier = Modifier) {
    if (!liveChromeBlur(panel.frost)) return
    androidx.compose.foundation.layout.Box(
        modifier.graphicsLayer { alpha = panel.q() }.hazeBlur(
            input = HazeInput.Sources(panel.frost.haze, selection = HazeSourceSelection.All),
            style = HazeBlurStyle {
                blurEnabled(true)
                backgroundColor(Color.Transparent)
                blurRadius(18.dp)
                noiseFactor(0f)
            },
            performanceMode = HazePerformanceMode.Performance,
        ),
    )
}

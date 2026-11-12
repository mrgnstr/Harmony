package com.mrgnstr.harmony.feature.nowplaying

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.core.artwork.CoverBox
import com.mrgnstr.harmony.core.artwork.CoverExtension
import com.mrgnstr.harmony.core.artwork.rememberCoverTransition
import com.mrgnstr.harmony.core.designsystem.dither
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.designsystem.liveCoverBlur
import com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
import com.mrgnstr.harmony.core.designsystem.blur.FloatingChromeMargin
import com.mrgnstr.harmony.core.designsystem.blur.FloatingChromeWidth
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.device.VisualTier
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

val MiniPlayerHeight = 64.dp
val MiniPlayerGap = 8.dp
private val MiniArt = 44.dp
private val MiniArtRadius = 8.dp
private val PanelCoverRadius = 12.dp
/** How blurred the cover behind the lyrics gets (a gaussian sigma). */
private val PanelBlur = 46.dp

/** The panel's progress at which the unblurred strips around the cover have faded out completely. */
private const val StripsFadeEnd = 0.3f
/** Left gap of the thumbnail: larger than the vertical one so its corners clear the pill's curve. */
private val MiniArtInset = 14.dp
/** Release speed in dp/s that throws the mini player away (as tuned in the old app: 2000 px/s on the 11T, without being harder on lower densities). */
private val DismissFling = 725.dp
/** Thrown down from Now Playing so hard that the pill sags this far into the tab bar, the bar is left minimized (it first touches it at [MiniPlayerGap]). */
private val DigInDepth = 30.dp
private const val CoverArcBend = 1.6f
private const val CoverCenterBend = 2f
private fun coverRise(p: Float) = p.pow(CoverArcBend)
private fun coverCenter(p: Float) = 1f - (1f - p).pow(CoverCenterBend)

/** A rounded rect at fixed coordinates: the sheet's clip, rebuilt per frame without any relayout. */
private class RectShape(private val rect: RoundRect) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Rounded(rect)
}

/** The display's four corner radii in px (top-left, top-right, bottom-right, bottom-left); all 0 where the system reports none (before Android 12). */
private class ScreenCorners(val tl: Float, val tr: Float, val br: Float, val bl: Float) {
    /**
     * The sheet's corners at [t] of the way from the fully round mini pill ([miniH] tall) to the display's own, capped to fit the
     * rect. [keep] scales the result (1 = as is, 0 = square).
     */
    fun morph(miniH: Float, t: Float, left: Float, top: Float, right: Float, bottom: Float, keep: Float = 1f): RoundRect {
        val cap = minOf(right - left, bottom - top) / 2f
        fun r(corner: Float) = CornerRadius((lerp(miniH / 2f, corner, t) * keep).coerceIn(0f, cap))
        return RoundRect(left, top, right, bottom, r(tl), r(tr), r(br), r(bl))
    }

    companion object {
        val None = ScreenCorners(0f, 0f, 0f, 0f)

        fun of(view: View): ScreenCorners {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return None
            val insets = view.rootWindowInsets ?: return None
            fun at(position: Int) = insets.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
            return ScreenCorners(
                at(RoundedCorner.POSITION_TOP_LEFT), at(RoundedCorner.POSITION_TOP_RIGHT),
                at(RoundedCorner.POSITION_BOTTOM_RIGHT), at(RoundedCorner.POSITION_BOTTOM_LEFT),
            )
        }
    }
}

/**
 * Where the mini player sits when the tab bar is minimized: down on the bar's row, between the bar's circle and the search button.
 * [progress] is how far it has moved down; [drop] is how far down that is, [left] / [right] the room the two buttons take. On the
 * Search tab the bar's pill folds away ([fold] 1) and the mini player takes the whole left side, so [left] is not reserved.
 */
class MiniDock(
    val progress: () -> Float, val drop: Dp, val left: Dp, val right: Dp, val fold: () -> Float = { 0f },
    /** Whether the pill is narrowed to the inline size (the next button goes); false in a wide layout, where it stays wide. */
    val compact: Boolean = true,
    /** The widest the pill gets, centred in what the bar leaves free (the wide layouts keep it the size it has upright). */
    val maxWidth: Dp = Dp.Infinity,
)

/** The pill's shape at any size: fully round at [miniH] tall, the display's own corners at [screenH] tall (the height is linear in progress). */
private class MorphingPillShape(private val miniH: Float, private val screenH: Float, private val corners: ScreenCorners) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = ((size.height - miniH) / (screenH - miniH)).coerceIn(0f, 1f)
        return Outline.Rounded(corners.morph(miniH, t, 0f, 0f, size.width, size.height))
    }
}

/**
 * Mini player and Now Playing as one layer morphing on [PlayerSheetState.progress]. Nothing is resized while it moves: the sheet
 * is a full-screen box clipped to an interpolated rounded rect, the cover is one layer with a transform, and progress is only
 * read in graphicsLayer lambdas, so a drag redraws but never recomposes or relayouts.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
/** How far up into the cover the darkening under it begins, as a share of the cover's height. */
private const val ScrimIntoCover = 0.15f

/** How many songs ahead of the playing one get their cover blur built in advance. */
private const val PrewarmAhead = 3

/** The classic layout's cover: side margin, breathing room above and below it, corner radius. */
private val ClassicArtMargin = 28.dp
private val ClassicArtGap = 16.dp

/** Themes the full player with the cover-seeded [scheme] in the classic layout (null: as it is). */
@Composable
private fun ClassicTheme(scheme: androidx.compose.material3.ColorScheme?, content: @Composable () -> Unit) {
    if (scheme == null) { content(); return }
    com.mrgnstr.harmony.core.designsystem.theme.HarmonyTheme(
        scheme, LocalVisualTier.current, com.mrgnstr.harmony.core.designsystem.LocalChromeBlurMode.current,
        com.mrgnstr.harmony.core.designsystem.LocalCoverBlurMode.current,
    ) {
        CompositionLocalProvider(
            LocalClassicPlayer provides true, LocalLyricsInk provides scheme.onSurface, LocalContentColor provides scheme.onSurface,
            content = content,
        )
    }
}

@Composable
fun PlayerSheet(
    state: PlayerSheetState,
    restingBottom: Dp,
    blur: ChromeBlurState,
    modifier: Modifier = Modifier,
    dock: MiniDock? = null,
    schemes: com.mrgnstr.harmony.core.designsystem.theme.SchemeFactory? = null,
    dark: Boolean = false,
    onThrownDown: () -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()
    var hasAppeared by rememberSaveable { mutableStateOf(false) }
    // Swiped away and cleared: re-arm the entrance for the next queue, not for a song change or rotation.
    LaunchedEffect(snap.hasMedia) {
        if (!snap.hasMedia) {
            hasAppeared = false
            state.finishSwipeAway()
        }
    }
    if (!snap.hasMedia) return
    val songId = snap.songId ?: return
    val entrance = remember { Animatable(if (hasAppeared || state.isExpanded) 0f else 1f) }
    LaunchedEffect(entrance) {
        hasAppeared = true
        if (entrance.value > 0f) entrance.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
    }
    val panel = rememberPanelState()
    val showAudioInfo = viewModel.showAudioInfo.collectAsStateWithLifecycle().value
    val alwaysDark = viewModel.alwaysDark.collectAsStateWithLifecycle().value
    val classic = viewModel.classicLayout.collectAsStateWithLifecycle().value && schemes != null
    val classicRadius = viewModel.classicCoverRadius.collectAsStateWithLifecycle().value.dp
    val seed = viewModel.accentSeed.collectAsStateWithLifecycle().value
    // The album page's own scheme for this cover (same seed, same palette rules), settling like a page does.
    val classicScheme = if (classic && schemes != null) {
        com.mrgnstr.harmony.core.designsystem.theme.animateColorScheme(
            schemes.scheme(seed?.let { com.mrgnstr.harmony.core.designsystem.theme.ColorSource.Seed(it) } ?: com.mrgnstr.harmony.core.designsystem.theme.ColorSource.Wallpaper, dark),
            snap = false, lowTier = LocalVisualTier.current == VisualTier.LOW,
        )
    } else null
    val vmColors = (if (alwaysDark) viewModel.darkCoverColors else viewModel.coverColors).collectAsStateWithLifecycle().value
    // Over the darkened, blurred lyrics background the dark controls of a bright cover would not be seen: they turn light.
    val darkened = animateCoverColors(if (panel.panel != NowPlayingPanel.None && vmColors.scrim < 0.01f) DefaultCoverColors else vmColors)
    val coverColors = if (classicScheme != null) remember(classicScheme) { classicCoverColors(classicScheme) } else darkened
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lowTier = LocalVisualTier.current == VisualTier.LOW
    val fade: (Float) -> Float = { x -> if (lowTier) (if (x >= 0.5f) 1f else 0f) else x.coerceIn(0f, 1f) }
    val clamped: () -> Float = { state.visual.coerceIn(0f, 1f) }
    // Whether the current drag began with the mini player resting.
    val fromRest = remember { mutableStateOf(false) }
    StatusBarIconsFollowCover(state, coverColors, panelOpen = panel.panel != NowPlayingPanel.None, classic = classic)
    ControlsAutoHide(panel, snap.isPlaying)
    LaunchedEffect(state, panel) {
        // Collapsed: the lyrics stay open for next time; the queue does not.
        snapshotFlow { state.progress.value < 0.01f && !state.progress.isRunning }.collect { if (it && panel.panel == NowPlayingPanel.Queue) panel.reset() }
    }

    // Back closes the player; the gesture scrubs the morph and lets go with its own spring.
    PredictiveBackHandler(enabled = state.isExpanded) { events ->
        try {
            events.collect { state.progress.snapTo(1f - 0.35f * it.progress) }
            state.collapse()
        } catch (e: CancellationException) {
            state.expand()
            throw e
        }
    }

    // Back closes the lyrics before it collapses the player (registered last, so it is asked first).
    BackHandler(enabled = panel.panel != NowPlayingPanel.None && state.isExpanded) { scope.launch { panel.toggle(panel.panel) } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val miniH = with(density) { MiniPlayerHeight.toPx() }
        // As wide as the tab bar, centred.
        val fullW = minOf(screenW - 2 * with(density) { FloatingChromeMargin.toPx() }, with(density) { FloatingChromeWidth.toPx() })
        val fullMargin = (screenW - fullW) / 2f
        // With the tab bar minimized it moves down beside the bar's circle, narrower. (Read here, so the sheet recomposes while it moves.)
        val docked = dock?.progress?.invoke() ?: 0f
        val dockLeft = dock?.let { with(density) { it.left.toPx() } * (1f - it.fold()) } ?: 0f
        val dockRight = dock?.let { with(density) { it.right.toPx() } } ?: 0f
        // How much of the next button is gone (only in the narrow inline size).
        val nextGone = if (dock?.compact == false) 0f else docked
        val availableW = fullW - (dockLeft + dockRight) * docked
        val miniW = minOf(availableW, dock?.let { with(density) { it.maxWidth.toPx() } } ?: Float.POSITIVE_INFINITY)
        val margin = fullMargin + dockLeft * docked + (availableW - miniW) / 2f
        val restingTop = screenH - with(density) { restingBottom.toPx() } + (dock?.let { with(density) { it.drop.toPx() } } ?: 0f) * docked - miniH
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        val miniArt = with(density) { MiniArt.toPx() }
        val miniArtX = margin + with(density) { MiniArtInset.toPx() }
        val miniArtY = restingTop + (miniH - miniArt) / 2f

        // The cover is edge to edge and square. On a tall screen it sits centred between the status bar and the controls with blur
        // above and below; on a short one it is pinned to the top, with no blur above, and the controls share what is left.
        // In the classic layout it is inset with rounded corners, centred between the status bar and the controls (smaller if short of room).
        val controlsNatural = with(density) { fullControlsHeight(showAudioInfo).toPx() }
        val naturalControlsTop = screenH - navBottom - with(density) { FullControlsBottomGap.toPx() } - controlsNatural
        val fullArt = if (classic) {
            minOf(screenW - 2 * with(density) { ClassicArtMargin.toPx() }, naturalControlsTop - statusTop - 2 * with(density) { ClassicArtGap.toPx() })
                .coerceAtLeast(with(density) { MiniArt.toPx() } * 2f)
        } else screenW
        val artX = (screenW - fullArt) / 2f
        val compact = !classic && naturalControlsTop - fullArt < statusTop
        val fullArtY = if (compact) 0f else (statusTop + (naturalControlsTop - statusTop - fullArt) / 2f).roundToInt().toFloat()
        val controlsTop = if (compact) fullArt else naturalControlsTop
        val controlsBottom = if (compact) screenH - navBottom else naturalControlsTop + controlsNatural

        // Where the cover goes in a panel: small, top left, with the title beside it.
        val headerSize = with(density) { PanelCoverSize.toPx() }
        val headerX = with(density) { PanelSide.toPx() }
        val headerY = statusTop + with(density) { PanelHeaderTop.toPx() }
        val listTop = headerY + headerSize + with(density) { 10.dp.toPx() }
        // The lyrics view starts this much above that, under the header, so the fade at its top has room above the lines.
        val listOverlap = with(density) { LyricsTopOverlap.toPx() }
        val liveBlur = liveCoverBlur()
        // The blur bitmaps (where the live blur is off) are built for this song and the ones about to play, whether or not Now Playing
        // is open, so a song change or opening the player finds them ready.
        val shaderBlur = com.mrgnstr.harmony.core.designsystem.realTimeBlur()
        val specOf = com.mrgnstr.harmony.core.artwork.rememberCoverStripSpecFactory()
        val upcoming = viewModel.queue.collectAsStateWithLifecycle().value.upcoming.take(PrewarmAhead).map { it.songId to it.albumId }
        if (!classic && (!shaderBlur || !liveBlur)) {
            val wPx = screenW.roundToInt(); val hPx = screenH.roundToInt()
            val topPx = fullArtY.roundToInt(); val bottomPx = (fullArtY + fullArt).roundToInt()
            LaunchedEffect(songId, snap.albumId, upcoming, specOf, wPx, hPx, topPx, bottomPx, shaderBlur, liveBlur) {
                for ((id, album) in listOf(songId to snap.albumId) + upcoming) {
                    if (id == 0L) continue
                    if (!shaderBlur) viewModel.coverStrips(specOf(id, album, wPx, hPx, topPx, bottomPx))
                    if (!liveBlur) viewModel.diffusedCover(id, album)
                }
            }
        }
        val cover = rememberCoverTransition(songId, snap.albumId, fullArt.roundToInt())
        val showFull by remember { derivedStateOf { state.progress.value > 0.01f } }
        val showMini by remember { derivedStateOf { state.progress.value < 0.99f } }
        val showGlass by remember { derivedStateOf { state.progress.value < 1f } }
        val travel = restingTop
        // The pill digging into the tab bar (the sag of a hard throw down, see PlayerSheetState.visual) leaves the bar minimized.
        val digIn = with(density) { DigInDepth.toPx() }
        val currentTravel by rememberUpdatedState(travel)
        val thrownDown by rememberUpdatedState(onThrownDown)
        LaunchedEffect(state, digIn) {
            var armed = true
            snapshotFlow { -state.visual * currentTravel }.collect { sagPx ->
                if (armed && sagPx >= digIn) { armed = false; thrownDown() }
                else if (sagPx < digIn / 3f) armed = true
            }
        }
        // Swiped away, the pill is thrown down the gap to the tab bar (where it touches it and starts pushing it) and then its own
        // height further, fading over that last stretch; that travel is where it is gone.
        val gapPx = with(density) { MiniPlayerGap.toPx() }
        val swipeDistance = miniH + gapPx
        // The entrance retraces the dismissal's translation and fade, with fixed timing and no tab-bar push.
        val swipeOffset: () -> Float = { state.swipeAway.value + entrance.value * swipeDistance }
        val swipeAlpha: () -> Float = { fade(1f - ((swipeOffset() - gapPx) / miniH).coerceIn(0f, 1f)) }
        // The open player ends with the display's own corner radii (Android 12+; square where the system reports none).
        val view = LocalView.current
        val corners = remember(screenW, screenH) { ScreenCorners.of(view) }
        // The spring may dip below 0 (the pill sags past its resting place); everything drawn follows it down by this much.
        val sag: () -> Float = { (-state.visual).coerceAtLeast(0f) * travel + swipeOffset() }
        // The display's radius only shapes the motion. Once the player has opened and stopped it eases to square, so a device that
        // misreports its corners never keeps a wrong radius; it comes straight back as soon as the sheet moves.
        val cornerKeep = remember { Animatable(1f) }
        LaunchedEffect(state) {
            snapshotFlow { state.progress.value >= 1f && !state.progress.isRunning }.collect { settled ->
                if (settled) cornerKeep.animateTo(0f, tween(300)) else cornerKeep.snapTo(1f)
            }
        }

        // The cover's place at sheet progress p: not a straight line. Its centre heads for the screen's horizontal centre early and
        // rises late, so it reaches the middle before it fills the screen (rising early would push it above the sheet's top, which
        // rises linearly, and clip it). Its height is the centre's share of the way to the open cover, which dips below the start
        // before rising because the cover grows downward faster than its top rises.
        val coverSize: (Float) -> Float = { p -> lerp(miniArt, fullArt, p) }
        val coverLeft: (Float) -> Float = { p -> lerp(miniArtX + miniArt / 2f, artX + fullArt / 2f, coverCenter(p)) - coverSize(p) / 2f }
        val coverTop: (Float) -> Float = { p -> lerp(miniArtY, fullArtY, coverRise(p)) }

        // The expanding object is the mini player's own blurred pill, resized every frame to the full screen with corners that
        // morph into the display's. It never fades: the scene (cover, blur, controls) grows over it and hides it at the end.
        if (showGlass) {
            ChromeSurface(
                state = blur,
                shape = remember(miniH, screenH, corners) { MorphingPillShape(miniH, screenH, corners) },
                modifier = Modifier.graphicsLayer { alpha = swipeAlpha() }.layout { measurable, incoming ->
                    val p = clamped()
                    val w = lerp(miniW, screenW, p).roundToInt().coerceAtLeast(0)
                    val h = lerp(miniH, screenH, p).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(incoming.maxWidth, incoming.maxHeight) {
                        placeable.place(lerp(margin, 0f, p).roundToInt(), (lerp(restingTop, 0f, p) + sag()).roundToInt())
                    }
                },
            )
        }

        // A full-screen layer riding on the cover: its anchor (the full-width cover's square) is kept exactly on the cover, same
        // scale and position, and it is clipped to the anchor plus however much of the screen is open. So what is on it (the
        // blur, the controls) travels down with the cover and is cropped into it as the cover shrinks, instead of just fading.
        val riding: GraphicsLayerScope.() -> Unit = {
            val p = clamped()
            // In a panel the scene does not shrink onto the cover: it stays full screen, moves down with the pill's top edge and is clipped by it (k = 1).
            val k = panel.open.value
            val s0 = lerp(miniArt / fullArt, 1f, p)
            val s = lerp(s0, 1f, k)
            transformOrigin = TransformOrigin(0f, fullArtY / screenH)
            scaleX = s; scaleY = s
            // The anchor (the cover's square, at artX in the layer) sits on the cover.
            translationX = lerp(coverLeft(p) - s0 * artX, 0f, k)
            // In a panel it is pinned to the pill's top edge as it rises, like the mini player's own row.
            translationY = lerp(coverTop(p) - fullArtY, lerp(restingTop, 0f, p), k) + sag()
            // The cover's small radius at rest; open, the display's own radius at each corner (divided by the scale: the layer is scaled).
            val rest = MiniArtRadius.toPx()
            fun r(display: Float) = CornerRadius(lerp(rest, display, p) * cornerKeep.value / s)
            shape = RectShape(
                RoundRect(
                    left = lerp(artX * (1f - p), 0f, k), top = lerp(fullArtY * (1f - p), 0f, k),
                    right = lerp(artX + fullArt + p * (screenW - artX - fullArt), screenW, k),
                    bottom = lerp(fullArtY + fullArt + p * (screenH - fullArtY - fullArt), screenH, k),
                    topLeftCornerRadius = r(corners.tl), topRightCornerRadius = r(corners.tr),
                    bottomRightCornerRadius = r(corners.br), bottomLeftCornerRadius = r(corners.bl),
                ),
            )
            clip = true
        }

        // The scene fades as it is cropped; slower in a panel, where it is the whole screen going into the pill.
        val sceneAlpha: () -> Float = { fade(clamped() / lerp(0.15f, 0.6f, panel.open.value)) }

        val drag = rememberDraggableState { delta ->
            // Having pulled the player up at any point in this drag rules the swipe-away out, however it ends.
            if (state.progress.value > 0.02f) fromRest.value = false
            if (!state.swipingAway) scope.launch(start = CoroutineStart.UNDISPATCHED) { state.dragBy(delta, travel) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = clamped()
                    val dy = sag()
                    val rect = corners.morph(
                        miniH, p,
                        left = lerp(margin, 0f, p), top = lerp(restingTop, 0f, p) + dy,
                        right = lerp(margin + miniW, screenW, p), bottom = lerp(restingTop + miniH, screenH, p) + dy,
                        keep = cornerKeep.value,
                    )
                    // Once faded out, an empty clip: nothing drawn, and the pill lying over the tab bar takes no touches.
                    shape = if (state.swipeGone) RectShape(RoundRect(0f, 0f, 0f, 0f, CornerRadius.Zero)) else RectShape(rect)
                    // Thrown down, it only starts to fade where it touches the tab bar, and is gone one pill height later.
                    alpha = swipeAlpha()
                    clip = true
                }
                .draggable(
                    drag, Orientation.Vertical,
                    onDragStarted = { fromRest.value = state.isResting },
                    onDragStopped = { v ->
                        when {
                            state.swipingAway -> Unit
                            // Only a drag that began with the mini player at rest and only ever went down (not one pulled down from
                            // Now Playing, mid-morph, or after first being pulled up), and fast enough: it is thrown away and the
                            // queue is cleared.
                            fromRest.value && v > with(density) { DismissFling.toPx() } && state.progress.value <= 0.02f ->
                                state.swipeAway(v, gapPx, swipeDistance, onGone = viewModel::clearAll)
                            else -> state.settle(v, travel)
                        }
                    },
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !state.isExpanded) { state.expand() }
                .panelTouches(panel) { panel.controlsTop - with(density) { 64.dp.toPx() } },
        ) {

            // Classic: the plain surface (a menu's colour) takes the pill's place as it opens; the mini row fades out over it.
            if (classicScheme != null) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade(clamped() / 0.3f) }.background(classicScheme.surfaceContainer))
            }
            // Under the whole scene (cover, blur, controls); taps reach it because the scene is cropped to the cover while collapsed.
            if (showMini) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Row(
                    Modifier
                        .offset { IntOffset(margin.roundToInt(), restingTop.roundToInt()) }
                        .size(with(density) { miniW.toDp() }, MiniPlayerHeight)
                        .graphicsLayer { // Stays solid for the first half of the motion, then fades out under the growing scene.
                            alpha = fade(1f - (clamped() - 0.5f) / 0.5f)
                            // Pinned to the pill's top edge as it rises.
                            translationY = lerp(restingTop, 0f, clamped()) - restingTop + sag() }
                        .padding(start = MiniArtInset + MiniArt + 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // In a panel the full player's title travels here instead (until it is composed, this one stays).
                    Column(Modifier.weight(1f).graphicsLayer { alpha = if (showFull) 1f - panel.open.value else 1f }) {
                        MarqueeText(snap.title, MaterialTheme.typography.titleMediumEmphasized, MaterialTheme.colorScheme.onSurface)
                        MarqueeText(snap.artist, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MorphingPlayButton(
                        snap.isPlaying, viewModel::togglePlayPause, size = 48.dp, glyph = 24.dp, buffering = snap.isBuffering,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    val nextKick = rememberSkipKick(next = true, reach = 6.dp)
                    // Beside the minimized bar there is no room for it (as in Music): it fades and gives its width back.
                    Box(Modifier.width(48.dp * (1f - nextGone)).clipToBounds().graphicsLayer { alpha = 1f - nextGone }, contentAlignment = Alignment.Center) {
                        androidx.compose.material3.IconButton(
                            onClick = { nextKick.kick(); viewModel.next() }, enabled = snap.hasNext && nextGone < 0.5f,
                            modifier = Modifier.requiredWidth(48.dp),
                        ) {
                            SkipIcon(Symbols.SkipNext, "Next", nextKick)
                        }
                    }
                }
                }
            }


            // Solid ground under the scene, cropped and grown like it: the blur layers are bitmaps or a shader that can end a pixel or
            // two short of the screen edge, and the page behind must never show through there.
            if (!classic) Box(
                Modifier.fillMaxSize().graphicsLayer(riding).graphicsLayer { alpha = sceneAlpha() }.background(Color.Black),
            )

            // Where the cover is: the mini player's thumbnail grows into the full-width picture, or, in a panel, goes to the header.
            // Two covers share this, the one here and the panel's (composed with the full player, further up), and take over from
            // each other when that is composed (a hair into the opening), so they must be in the same place at every moment.
            val placeCover: GraphicsLayerScope.() -> Unit = {
                val p = clamped()
                val q = panel.q()
                // Between the header and the mini player the cover moves in a straight line.
                val hp = lerp(miniArt, headerSize, p)
                val s = lerp(lerp(miniArt / fullArt, 1f, p), hp / fullArt, q)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = s; scaleY = s
                translationX = lerp(coverLeft(p), lerp(miniArtX, headerX, p), q)
                translationY = lerp(coverTop(p), lerp(miniArtY, headerY, p), q) + sag()
                // The radius is set in the layer's own (unscaled) space, so divide the on-screen one by the scale.
                val radius = lerp(lerp(MiniArtRadius.toPx(), if (classic) classicRadius.toPx() else 0f, p), lerp(MiniArtRadius.toPx(), PanelCoverRadius.toPx(), p), q) / s
                shape = RectShape(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                clip = true
            }
            CoverBox(
                cover,
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(with(density) { fullArt.toDp() }, with(density) { fullArt.toDp() })
                    .graphicsLayer {
                        placeCover()
                        // In a panel the cover is drawn by the one further up (the header's); until that is composed, this one stays.
                        alpha = if (showFull) 1f - panel.open.value else 1f
                    },
            )

            // The darkening and the strips are drawn twice while a panel opens or closes (see [stripsOverlay]), so they are shared.
            val sceneScrim: @Composable () -> Unit = {
                // Darkening under the cover (never on it) keeps light controls readable on a mid-tone blur: how much depends on the
                // cover (none on a bright one, whose controls are dark). Eased out, so it is already strong where the title sits.
                if (coverColors.scrim > 0.01f && com.mrgnstr.harmony.core.debug.DebugOptions.scrim.on) {
                    // The curve starts inside the cover's bottom edge (by [ScrimIntoCover] of the cover's height), so it is already strong at the title.
                    val from = ((fullArtY + fullArt * (1f - ScrimIntoCover)) / screenH).coerceIn(0f, 1f)
                    val a = coverColors.scrim
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                from to Color.Transparent,
                                from + (1f - from) * 0.25f to Color.Black.copy(alpha = a * 0.7f),
                                from + (1f - from) * 0.55f to Color.Black.copy(alpha = a * 0.95f),
                                1f to Color.Black.copy(alpha = a),
                            ),
                        ),
                    )
                }
            }

            // The blur around the cover, drawn over it: it fades into the picture's own edge rows. In a panel it is the background:
            // the whole scene (a copy of the cover under the blur around it) is blurred, scaled up and slid up.
            if (!classic) Box(
                Modifier.fillMaxSize().graphicsLayer(riding).graphicsLayer {
                    // At rest in the mini player only a faint rim would show inside the thumbnail, so it fades out over the last stretch.
                    alpha = sceneAlpha()
                }.then(if (lowTier) Modifier else Modifier.dither()),
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val q = panel.q()
                        val grow = lerp(1f, 1.3f, q)
                        scaleX = grow; scaleY = grow
                        translationY = -q * 0.05f * screenH
                        if (liveBlur && q > 0.001f) {
                            // Fully blurred from the first moment the cover moves off it (it is behind the cover until then).
                            val r = (q / 0.04f).coerceAtMost(1f) * PanelBlur.toPx()
                            renderEffect = BlurEffect(r, r, TileMode.Clamp)
                        }
                    },
                ) {
                    if (panel.visible) {
                        CoverBox(
                            cover,
                            Modifier.offset { IntOffset(0, fullArtY.roundToInt()) }.size(with(density) { fullArt.toDp() }),
                        )
                    }
                    if (showFull) {
                        CoverExtension(
                            songId = songId, albumId = snap.albumId, cover = cover,
                            coverTopPx = fullArtY, coverSizePx = fullArt, load = viewModel::coverStrips, ready = viewModel::coverStripsIfReady,
                        )
                        sceneScrim()
                    }
                }
                if (panel.visible) {
                    if (!liveBlur) {
                        // No live blur: the small pre-blurred cover, stretched over everything.
                        val diffused by produceState(viewModel.diffusedCoverIfReady(songId, snap.albumId)?.asImageBitmap(), songId, snap.albumId) {
                            value = viewModel.diffusedCover(songId, snap.albumId)?.asImageBitmap()
                        }
                        diffused?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = panel.q() }, contentScale = ContentScale.Crop) }
                    }
                    // The lyrics are white: the background gets darker under them, more on a bright cover.
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = panel.q() * (if (coverColors.scrim < 0.01f) 0.5f else 0.38f) }.background(Color.Black))
                }
            }

            if (showFull) ClassicTheme(classicScheme) {
                val progress by viewModel.progress.collectAsStateWithLifecycle()
                val queue by viewModel.queue.collectAsStateWithLifecycle()
                val favourite by viewModel.isFavourite.collectAsStateWithLifecycle()
                val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
                val artists by viewModel.artists.collectAsStateWithLifecycle()
                val openAlbum = com.mrgnstr.harmony.core.designsystem.LocalOpenAlbum.current
                val openArtist = com.mrgnstr.harmony.core.designsystem.LocalOpenArtist.current
                val fileAccess by viewModel.lyricsFileAccess.collectAsStateWithLifecycle()
                androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                    viewModel.recheckFileAccess()
                    onPauseOrDispose {}
                }
                if (panel.visible) {
                    // The lyrics come up from under the cover as it shrinks away (inside the fade above the controls, which stays put).
                    Box(Modifier.fillMaxSize().graphicsLayer(riding).graphicsLayer { alpha = sceneAlpha() }) {
                        LyricsView(
                            lyrics, progress, snap.isPlaying, viewModel::positionNow, panel, listTop - listOverlap, fileAccess, { (1f - panel.q()) * (fullArtY + fullArt - listTop + listOverlap).coerceAtLeast(0f) }, viewModel::seekTo,
                            Modifier
                                .offset { IntOffset(0, (listTop - listOverlap).roundToInt()) }
                                .size(with(density) { screenW.toDp() }, with(density) { (screenH - listTop + listOverlap).toDp() }),
                        )
                    }
                    // The cover itself, on its way to the header (the copy behind it is the background).
                    CoverBox(
                        cover,
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(with(density) { fullArt.toDp() }, with(density) { fullArt.toDp() })
                            .graphicsLayer { placeCover() }
                            // After the layer, so the touch area is the cover as drawn: it closes the panel.
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = panel.panel != NowPlayingPanel.None) {
                                scope.launch { panel.toggle(panel.panel) }
                            },
                    )
                }
                // The scene is blurred as one in a panel, so its progressive blur is gone there; and the panel's cover lies over the
                // strips' fade into it, which would only appear (snap in) when the panel is gone. So the same strips and darkening,
                // unblurred, moving with the scene, are drawn over the cover and fade out as the panel opens and back in as it closes.
                val stripsOverlay by remember { derivedStateOf { !classic && liveBlur && panel.visible && panel.q() < StripsFadeEnd + 0.05f } }
                if (stripsOverlay) {
                    Box(Modifier.fillMaxSize().graphicsLayer(riding).graphicsLayer { alpha = fade(clamped() / 0.15f) }) {
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val q = panel.q()
                                val grow = lerp(1f, 1.3f, q)
                                scaleX = grow; scaleY = grow
                                translationY = -q * 0.05f * screenH
                                val t = (q / StripsFadeEnd).coerceIn(0f, 1f)
                                alpha = 1f - t * t * (3f - 2f * t)
                            },
                        ) {
                            CoverExtension(
                                songId = songId, albumId = snap.albumId, cover = cover,
                                coverTopPx = fullArtY, coverSizePx = fullArt, load = viewModel::coverStrips,
                            )
                            sceneScrim()
                        }
                    }
                }
                // The title is on its own layer: in a panel it goes to the mini player, while the controls fade with the scene.
                Box(Modifier.fillMaxSize().graphicsLayer(riding)) {
                    CompositionLocalProvider(LocalCoverColors provides coverColors) {
                        PlayingTitle(
                            snap, favourite, viewModel::toggleFavourite, panel,
                            headerShift = headerY + with(density) { PanelCoverSize.toPx() / 2f - TitleRowHeight.toPx() / 2f } - controlsTop,
                            sheetProgress = clamped,
                            miniShift = androidx.compose.ui.geometry.Offset(
                                headerX + headerSize + with(density) { PanelHeaderGap.toPx() } - (miniArtX + miniArt + with(density) { 12.dp.toPx() }),
                                headerY + headerSize / 2f - miniH / 2f,
                            ),
                            miniTextWidth = {
                                miniW - with(density) { (MiniArtInset + MiniArt + 12.dp + 8.dp + 48.dp + 48.dp * (1f - nextGone)).toPx() }
                            },
                            miniTitleSize = MaterialTheme.typography.titleMediumEmphasized.fontSize,
                            miniArtistSize = MaterialTheme.typography.bodySmall.fontSize,
                            artists = artists,
                            onOpenAlbum = {
                                if (snap.albumId != 0L) {
                                    openAlbum?.invoke(com.mrgnstr.harmony.core.model.Album(snap.albumId, snap.albumTitle, snap.artist, songId, 0, 0L, 0L))
                                    state.collapse()
                                }
                            },
                            onOpenArtist = { name -> openArtist?.invoke(name); state.collapse() },
                            // Outside a panel it fades in with the scene; in one it is on its way to the mini player and stays.
                            // The alpha layer goes after the offset: before it, the layer's bounds would be where the title is not, and cut it off.
                            modifier = Modifier
                                .offset { IntOffset(0, controlsTop.roundToInt()) }
                                .graphicsLayer { alpha = lerp(fade((clamped() - 0.2f) / 0.8f), 1f, panel.open.value) },
                        )
                    }
                }
                Box(Modifier.fillMaxSize().graphicsLayer(riding).graphicsLayer { alpha = fade((clamped() - 0.2f) / 0.8f) }) {
                    CompositionLocalProvider(LocalCoverColors provides coverColors) {
                        FullPlayerControls(
                            snap, progress, queue, favourite, viewModel, panel,
                            Modifier
                                .offset { IntOffset(0, controlsTop.roundToInt()) }
                                .size(with(density) { screenW.toDp() }, with(density) { (controlsBottom - controlsTop).toDp() }),
                        )
                    }
                }
                val debugShown by remember { derivedStateOf { state.progress.value > 0.99f && panel.panel == NowPlayingPanel.None } }
                if (!classic && com.mrgnstr.harmony.core.debug.DebugOptions.enabled && debugShown) {
                    DebugOverlay(
                        Modifier
                            .offset { IntOffset(artX.roundToInt(), fullArtY.roundToInt()) }
                            .size(with(density) { fullArt.toDp() }),
                    )
                }
            }
        }
    }
}

/**
 * The status bar's icons follow what is under them: the cover (or its blur) while Now Playing is open, the theme otherwise.
 */
@Composable
private fun StatusBarIconsFollowCover(state: PlayerSheetState, colors: CoverColors, panelOpen: Boolean, classic: Boolean) {
    val view = LocalView.current
    val themeDark = MaterialTheme.colorScheme.background.red < 0.5f
    // The lyrics' background is darkened: light icons.
    // The classic layout is a plain surface: the icons follow its brightness.
    val coverIsLight = if (classic) colors.lightTop else colors.lightTop && !panelOpen
    LaunchedEffect(coverIsLight, themeDark) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        snapshotFlow { state.progress.value > 0.5f }.collect { open ->
            controller.isAppearanceLightStatusBars = if (open) coverIsLight else !themeDark
        }
    }
}

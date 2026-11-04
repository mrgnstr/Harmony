package com.mrgnstr.harmony.feature.nowplaying

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
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
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.device.VisualTier
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

val MiniPlayerHeight = 64.dp
val MiniPlayerGap = 8.dp
private val MiniMargin = 12.dp
private val MiniArt = 44.dp
private val MiniArtRadius = 8.dp
/** Left gap of the thumbnail: larger than the vertical one so its corners clear the pill's curve. */
private val MiniArtInset = 14.dp

/** A rounded rect at fixed coordinates: the sheet's clip, rebuilt per frame without any relayout. */
private class RectShape(private val rect: RoundRect) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = Outline.Rounded(rect)
}

/** The display's four corner radii in px (top-left, top-right, bottom-right, bottom-left); all 0 where the system reports none (before Android 12). */
private class ScreenCorners(val tl: Float, val tr: Float, val br: Float, val bl: Float) {
    /** The sheet's corners at [t] of the way from the fully round mini pill ([miniH] tall) to the display's own, capped to fit [rect]. */
    fun morph(miniH: Float, t: Float, left: Float, top: Float, right: Float, bottom: Float): RoundRect {
        val cap = minOf(right - left, bottom - top) / 2f
        fun r(corner: Float) = CornerRadius(lerp(miniH / 2f, corner, t).coerceIn(0f, cap))
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
@Composable
fun PlayerSheet(
    state: PlayerSheetState,
    restingBottom: Dp,
    blur: ChromeBlurState,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val snap by viewModel.snapshot.collectAsStateWithLifecycle()
    if (!snap.hasMedia) return
    val songId = snap.songId ?: return
    val coverColors = animateCoverColors(viewModel.coverColors.collectAsStateWithLifecycle().value)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lowTier = LocalVisualTier.current == VisualTier.LOW
    val fade: (Float) -> Float = { x -> if (lowTier) (if (x >= 0.5f) 1f else 0f) else x.coerceIn(0f, 1f) }
    val clamped: () -> Float = { state.visual.coerceIn(0f, 1f) }

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

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val miniH = with(density) { MiniPlayerHeight.toPx() }
        val margin = with(density) { MiniMargin.toPx() }
        val miniW = screenW - 2 * margin
        val restingTop = screenH - with(density) { restingBottom.toPx() } - miniH
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
        val miniArt = with(density) { MiniArt.toPx() }
        val miniArtX = margin + with(density) { MiniArtInset.toPx() }
        val miniArtY = restingTop + (miniH - miniArt) / 2f

        // The cover is edge to edge and square. On a tall screen it sits centred between the status bar and the controls with blur
        // above and below; on a short one it is pinned to the top, with no blur above, and the controls share what is left.
        val fullArt = screenW
        val controlsNatural = with(density) { FullControlsHeight.toPx() }
        val naturalControlsTop = screenH - navBottom - with(density) { FullControlsBottomGap.toPx() } - controlsNatural
        val compact = naturalControlsTop - fullArt < statusTop
        val fullArtY = if (compact) 0f else (statusTop + (naturalControlsTop - statusTop - fullArt) / 2f).roundToInt().toFloat()
        val controlsTop = if (compact) fullArt else naturalControlsTop
        val controlsBottom = if (compact) screenH - navBottom else naturalControlsTop + controlsNatural

        val cover = rememberCoverTransition(songId, snap.albumId, fullArt.roundToInt())
        val showFull by remember { derivedStateOf { state.progress.value > 0.01f } }
        val showMini by remember { derivedStateOf { state.progress.value < 0.99f } }
        val showGlass by remember { derivedStateOf { state.progress.value < 1f } }
        val travel = restingTop
        // The open player ends with the display's own corner radii (Android 12+; square where the system reports none).
        val view = LocalView.current
        val corners = remember(screenW, screenH) { ScreenCorners.of(view) }
        // The spring may dip below 0 (the pill sags past its resting place); everything drawn follows it down by this much.
        val sag: () -> Float = { (-state.visual).coerceAtLeast(0f) * travel }

        // The expanding object is the mini player's own blurred pill, resized every frame to the full screen with corners that
        // morph into the display's. It never fades: the scene (cover, blur, controls) grows over it and hides it at the end.
        if (showGlass) {
            ChromeSurface(
                state = blur,
                shape = remember(miniH, screenH, corners) { MorphingPillShape(miniH, screenH, corners) },
                modifier = Modifier.layout { measurable, incoming ->
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
            val s = lerp(miniArt / fullArt, 1f, p)
            transformOrigin = TransformOrigin(0f, fullArtY / screenH)
            scaleX = s; scaleY = s
            translationX = lerp(miniArtX, 0f, p)
            translationY = lerp(miniArtY, fullArtY, p) - fullArtY + sag()
            // The cover's small radius at rest; open, the display's own radius at each corner (divided by the scale: the layer is scaled).
            val rest = MiniArtRadius.toPx()
            fun r(display: Float) = CornerRadius(lerp(rest, display, p) / s)
            shape = RectShape(
                RoundRect(
                    left = 0f, top = fullArtY * (1f - p),
                    right = fullArt + p * (screenW - fullArt), bottom = fullArtY + fullArt + p * (screenH - fullArtY - fullArt),
                    topLeftCornerRadius = r(corners.tl), topRightCornerRadius = r(corners.tr),
                    bottomRightCornerRadius = r(corners.br), bottomLeftCornerRadius = r(corners.bl),
                ),
            )
            clip = true
        }

        val drag = rememberDraggableState { delta -> scope.launch { state.dragBy(delta, travel) } }
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
                    )
                    shape = RectShape(rect)
                    clip = true
                }
                .draggable(drag, Orientation.Vertical, onDragStopped = { state.settle(it, travel) })
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !state.isExpanded) { state.expand() },
        ) {

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
                    Column(Modifier.weight(1f)) {
                        Text(snap.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(snap.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalIconButton(onClick = viewModel::togglePlayPause, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
                        PlayPauseIcon(snap.isPlaying, 24.dp)
                    }
                    val nextKick = rememberSkipKick(next = true, reach = 6.dp)
                    androidx.compose.material3.IconButton(onClick = { nextKick.kick(); viewModel.next() }, enabled = snap.hasNext) {
                        SkipIcon(Symbols.SkipNext, "Next", nextKick)
                    }
                }
                }
            }


            // Solid ground under the scene, cropped and grown like it: the blur layers are bitmaps or a shader that can end a pixel or
            // two short of the screen edge, and the page behind must never show through there.
            Box(
                Modifier.fillMaxSize().graphicsLayer(riding).graphicsLayer { alpha = fade(clamped() / 0.15f) }.background(Color.Black),
            )

            // The cover: the mini player's thumbnail grows into the full-width picture.
            CoverBox(
                cover,
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(with(density) { fullArt.toDp() }, with(density) { fullArt.toDp() })
                    .graphicsLayer {
                        val p = clamped()
                        val s = lerp(miniArt / fullArt, 1f, p)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = s; scaleY = s
                        translationX = lerp(miniArtX, 0f, p)
                        translationY = lerp(miniArtY, fullArtY, p) + sag()
                        // The radius is set in the layer's own (unscaled) space, so divide the on-screen one by the scale.
                        val radius = lerp(MiniArtRadius.toPx(), 0f, p) / s
                        shape = RectShape(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                        clip = true
                    },
            )

            // The blur around the cover, drawn over it: it fades into the picture's own edge rows.
            Box(
                Modifier.fillMaxSize().graphicsLayer(riding).graphicsLayer {
                    // At rest in the mini player only a faint rim would show inside the thumbnail, so it fades out over the last stretch.
                    alpha = fade(clamped() / 0.15f)
                },
            ) {
                if (showFull) {
                    CoverExtension(
                        songId = songId, albumId = snap.albumId, cover = cover,
                        coverTopPx = fullArtY, coverSizePx = fullArt, load = viewModel::coverStrips,
                    )
                    // Darkening under the cover (never on it) keeps light controls readable on a mid-tone blur: how much depends on the
                    // cover (none on a bright one, whose controls are dark). Eased out, so it is already strong where the title sits.
                    if (coverColors.scrim > 0.01f) {
                        val from = ((fullArtY + fullArt) / screenH).coerceIn(0f, 1f)
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
            }

            if (showFull) {
                val progress by viewModel.progress.collectAsStateWithLifecycle()
                val queue by viewModel.queue.collectAsStateWithLifecycle()
                val favourite by viewModel.isFavourite.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().graphicsLayer(riding).graphicsLayer { alpha = fade((clamped() - 0.2f) / 0.8f) }) {
                    CompositionLocalProvider(LocalCoverColors provides coverColors) {
                        FullPlayerControls(
                            snap, progress, queue, favourite, viewModel,
                            Modifier
                                .offset { IntOffset(0, controlsTop.roundToInt()) }
                                .size(with(density) { screenW.toDp() }, with(density) { (controlsBottom - controlsTop).toDp() }),
                        )
                    }
                }
            }
        }
    }
}

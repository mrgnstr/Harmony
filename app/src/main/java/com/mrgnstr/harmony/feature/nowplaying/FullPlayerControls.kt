package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import com.mrgnstr.harmony.core.model.FormatTag
import com.mrgnstr.harmony.core.designsystem.components.FormatGlyph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.core.designsystem.icons.AnimatedSymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.playback.PlaybackProgress
import com.mrgnstr.harmony.core.playback.PlaybackSnapshot
import com.mrgnstr.harmony.core.playback.QueueState
import com.mrgnstr.harmony.core.playback.RepeatMode


/** The controls' own height, title row to the shuffle/repeat row (what is between them is spread evenly); the cover is centred above it. */
internal val FullControlsHeight = 360.dp
/** Space under the controls, above the navigation bar. */
internal val FullControlsBottomGap = 24.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FullPlayerControls(
    snap: PlaybackSnapshot,
    progress: PlaybackProgress,
    queue: QueueState,
    favourite: Boolean,
    vm: NowPlayingViewModel,
    panel: PanelState,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
        val c = LocalCoverColors.current
        var showQueue by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val lyrics by vm.lyrics.collectAsStateWithLifecycle()
        val hasNavidrome by vm.hasNavidrome.collectAsStateWithLifecycle()
        // In a panel the title row lives in the panel's header, and the rows from the seek bar down fade away after a while.
        val controlsShown = remember(panel) { derivedStateOf { panel.controlsHidden.value < 0.98f } }
        val away = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
        val hide = Modifier
            .graphicsLayer {
                val h = panel.controlsHidden.value
                alpha = 1f - h
                translationY = h * away
            }
            .placedWhen(controlsShown)

        // The title block is drawn by the sheet ([PlayingTitle]), which slides it into the panels' header; this keeps its room.
        Spacer(Modifier.height(TitleRowHeight))

        Column(hide) {
            val format by vm.format.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                format?.let { FormatPill(it, panel) }
                // Translations and pronunciations: only over lyrics that have them.
                if (lyrics?.hasTranslations == true || lyrics?.hasPronunciations == true) {
                    LyricsOptions(
                        lyrics?.hasTranslations == true, lyrics?.hasPronunciations == true, panel,
                        Modifier
                            .align(Alignment.CenterStart)
                            .graphicsLayer { alpha = panel.q() }
                            .placedWhen(remember(panel) { derivedStateOf { panel.q() > 0.5f } }),
                    )
                }
            }
            Box(Modifier.onGloballyPositioned { panel.controlsTop = it.positionInRoot().y }) { SeekBar(progress, snap.isPlaying, vm::seekTo) }
        }

        // Transport
        val prevKick = rememberSkipKick(next = false)
        val nextKick = rememberSkipKick(next = true)
        Row(hide.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { prevKick.kick(); vm.previous() }, enabled = snap.hasPrevious || progress.positionMs > 3_000, shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = c.tonal, contentColor = c.text, disabledContainerColor = c.tonal, disabledContentColor = c.text.copy(alpha = 0.38f),
                ),
                modifier = Modifier.size(width = 84.dp, height = 64.dp),
            ) { SkipIcon(Symbols.SkipPrevious, "Previous", prevKick, size = 32.dp) }
            // The glyph is cut out of the white button (an offscreen layer lets it clear the container), so the cover shows through.
            PlayButton(snap.isPlaying, vm::togglePlayPause)
            FilledTonalIconButton(
                onClick = { nextKick.kick(); vm.next() }, enabled = snap.hasNext, shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = c.tonal, contentColor = c.text, disabledContainerColor = c.tonal, disabledContentColor = c.text.copy(alpha = 0.38f),
                ),
                modifier = Modifier.size(width = 84.dp, height = 64.dp),
            ) { SkipIcon(Symbols.SkipNext, "Next", nextKick, size = 32.dp) }
        }

        // Modes & overlays: Lyrics, Shuffle, Repeat, Queue, and Bookmark/Jukebox
        val shuffleFlip = rememberFlourish(bounce = 0.8f)
        val repeatSpin = rememberFlourish(bounce = 0.8f)
        Row(hide.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
            ModeToggle(panel.panel == NowPlayingPanel.Lyrics, { scope.launch { panel.toggle(NowPlayingPanel.Lyrics) } }) { blend, tint ->
                SymbolIcon(Symbols.Lyrics, "Lyrics", tint = tint, blendMode = blend)
            }
            ModeToggle(queue.shuffled, { shuffleFlip.play(); vm.toggleShuffle() }) { blend, tint ->
                // The crossing arrows flip over like a card.
                SymbolIcon(
                    Symbols.Shuffle, "Shuffle", tint = tint, blendMode = blend, weight = if (queue.shuffled) 600 else 400,
                    modifier = Modifier.graphicsLayer { rotationX = 360f * shuffleFlip.value.value; cameraDistance = 12f * density },
                )
            }
            ModeToggle(queue.repeat != RepeatMode.Off, { repeatSpin.play(); vm.cycleRepeat() }) { blend, tint ->
                SymbolIcon(
                    if (queue.repeat == RepeatMode.One) Symbols.RepeatOne else Symbols.Repeat, "Repeat", tint = tint, blendMode = blend,
                    weight = if (queue.repeat != RepeatMode.Off) 600 else 400,
                    modifier = Modifier.graphicsLayer { rotationZ = 360f * repeatSpin.value.value },
                )
            }
            ModeToggle(showQueue, { showQueue = true }) { blend, tint ->
                SymbolIcon(Symbols.QueueMusic, "Queue", tint = tint, blendMode = blend)
            }
        }

        if (showQueue) {
            QueueModalSheet(
                queue = queue,
                hasNavidrome = hasNavidrome,
                onSkipToIndex = vm::skipToQueueIndex,
                onRemoveIndex = vm::removeQueueItem,
                onSonicJourneyTo = vm::insertSonicJourneyTo,
                onSaveCloudQueue = vm::saveCloudQueue,
                onLoadCloudQueue = vm::loadCloudQueue,
                onDismiss = { showQueue = false },
            )
        }
    }
}

/** The heart: pops when switched. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FavouriteToggle(favourite: Boolean, onToggle: () -> Unit) {
    val pop = rememberFlourish(bounce = 0.45f)
    ModeToggle(favourite, { pop.play(); onToggle() }) { blend, tint ->
        AnimatedSymbolIcon(
            Symbols.Favorite, if (favourite) "Unfavourite" else "Favourite", filled = favourite, tint = tint, blendMode = blend,
            modifier = Modifier.graphicsLayer { (0.5f + 0.5f * pop.value.value).let { scaleX = it; scaleY = it } },
        )
    }
}

/** The small button over the seek bar in the lyrics: a menu to switch translations and pronunciations on and off, each on its own. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsOptions(hasTranslations: Boolean, hasPronunciations: Boolean, panel: PanelState, modifier: Modifier = Modifier) {
    val c = LocalCoverColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        DisposableEffect(panel) { onDispose { panel.buttonRect = null } }
        Box(Modifier.size(40.dp).clip(CircleShape).onGloballyPositioned { panel.buttonRect = it.boundsInRoot() }) {
            FrostLayer(panel, Modifier.matchParentSize())
            FilledTonalIconButton(
                onClick = { open = true }, shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.tonal, contentColor = c.text),
                modifier = Modifier.size(40.dp),
            ) { SymbolIcon(Symbols.Translate, "Translations and pronunciation", size = 20.dp, tint = c.text) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (hasTranslations) {
                DropdownMenuItem(
                    text = { Text("Translation") },
                    onClick = { panel.showTranslation = !panel.showTranslation },
                    trailingIcon = { if (panel.showTranslation) SymbolIcon(Symbols.Check, null, size = 20.dp) },
                )
            }
            if (hasPronunciations) {
                DropdownMenuItem(
                    text = { Text("Pronunciation") },
                    onClick = { panel.showPronunciation = !panel.showPronunciation },
                    trailingIcon = { if (panel.showPronunciation) SymbolIcon(Symbols.Check, null, size = 20.dp) },
                )
            }
        }
    }
}

/**
 * A tonal toggle in the favourite button's shape. Off: white glyph on translucent white. On: a white button with the glyph
 * cut out of it (an offscreen layer lets it clear the container), like the play button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ModeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, content: @Composable (BlendMode, Color) -> Unit) {
    val c = LocalCoverColors.current
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = c.tonal, contentColor = c.text, checkedContainerColor = c.accent, checkedContentColor = c.onAccent,
        ),
        modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) { content(if (checked) BlendMode.Clear else BlendMode.SrcOver, if (checked) c.onAccent else c.text) }
}

/** Codec, layout and quality of the playing file, on a translucent pill over the blurred cover. */
@Composable
private fun FormatPill(tag: FormatTag, panel: PanelState, modifier: Modifier = Modifier) {
    val c = LocalCoverColors.current
    DisposableEffect(panel) { onDispose { panel.pillRect = null } }
    Box(modifier.clip(CircleShape).onGloballyPositioned { panel.pillRect = it.boundsInRoot() }) {
        // Over the lyrics the pill sits on a blur of them.
        FrostLayer(panel, Modifier.matchParentSize())
        Row(
            Modifier.background(c.tonal).padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatGlyph(tag.icon, Modifier.padding(end = 6.dp), tint = c.textSoft)
            Text(tag.text, style = MaterialTheme.typography.labelMedium, color = c.textSoft, maxLines = 1)
        }
    }
}

/**
 * A wavy progress bar in Material's expressive style, with a handle on the join (no gap between the parts): the wave ripples
 * while the song plays and eases flat when it is paused or being dragged. Tap or drag anywhere on it to seek.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeekBar(progress: PlaybackProgress, playing: Boolean, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = progress.durationMs.coerceAtLeast(1L)
    val fraction = dragging ?: (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    // The player reports its position a few times a second; between reports the bar moves on the frame clock, so it glides
    // instead of stepping (a step is a visible jump on a short song). A new report only corrects it when it has drifted
    // (a seek, a new song, a stall), so the ordinary report neither jerks it nor sets it back.
    val smooth = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(progress.positionMs, duration, playing) {
        val reported = progress.positionMs
        val shownMs = smooth.floatValue * duration
        val startMs = if (playing && kotlin.math.abs(reported - shownMs) < 400f) shownMs else reported.toFloat()
        smooth.floatValue = (startMs / duration).coerceIn(0f, 1f)
        if (!playing) return@LaunchedEffect
        val t0 = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            smooth.floatValue = ((startMs + (now - t0) / 1_000_000f) / duration).coerceIn(0f, 1f)
        }
    }
    val grip = animateFloatAsState(if (dragging != null) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "grip")
    val amplitude = animateFloatAsState(if (playing && dragging == null) 1f else 0f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "wave")
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f).times(duration).toLong()) }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { dragging?.let { f -> onSeek((f * duration).toLong()) }; dragging = null },
                        onDragCancel = { dragging = null },
                    ) { change, _ -> change.consume(); dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            WaveTrack({ dragging ?: smooth.floatValue }, amplitude, grip, LocalCoverColors.current, Modifier.matchParentSize())
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock((fraction * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = LocalCoverColors.current.textSoft)
            Text("-" + clock(duration - (fraction * duration).toLong()), style = MaterialTheme.typography.labelMedium, color = LocalCoverColors.current.textSoft)
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Drawn by hand, not with [androidx.compose.material3.LinearWavyProgressIndicator]: that does not redraw when only its amplitude changes, so the wave could not ease out. */
@Composable
private fun WaveTrack(fraction: () -> Float, amplitude: State<Float>, grip: State<Float>, colors: CoverColors, modifier: Modifier) {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { amplitude.value > 0.001f }.collectLatest { active ->
            if (!active) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                phase.floatValue += (now - last) / 1_000_000_000f * WaveSpeedDp
                last = now
            }
        }
    }
    val path = remember { Path() }
    val track = remember { Path() }
    Canvas(modifier) {
        val stroke = 6.dp.toPx()
        val wavelength = 40.dp.toPx()
        val cy = size.height / 2f
        val x = (fraction() * size.width).coerceIn(0f, size.width)
        // One bar made of overlapping pieces, with the handle on top hiding every join: the inactive track runs from the handle's
        // centre (a plain rectangle there, so it has no curved start) to a rounded end, and the wave starts at the left end and
        // stops at the handle's right edge (its round cap ends flush with it), so no curved start or end shows beside the handle.
        val w = 4.dp.toPx() + 2.dp.toPx() * grip.value
        val h = 32.dp.toPx() + 6.dp.toPx() * grip.value
        val hx = x.coerceIn(w / 2, size.width - w / 2)
        val left = stroke / 2
        val right = size.width - stroke / 2
        // One shape (the track is translucent, so overlapping pieces would show), square at the handle and round at the far end.
        track.reset()
        track.addRoundRect(
            RoundRect(
                left = hx, top = cy - stroke / 2, right = size.width, bottom = cy + stroke / 2,
                topLeftCornerRadius = CornerRadius.Zero, topRightCornerRadius = CornerRadius(stroke / 2),
                bottomRightCornerRadius = CornerRadius(stroke / 2), bottomLeftCornerRadius = CornerRadius.Zero,
            ),
        )
        drawPath(track, colors.tonal)
        val activeEnd = hx + w / 2 - stroke / 2
        // The active part spans from the bar's left end to the handle's right edge. While that is shorter than the bar is thick
        // (the first moments of a song) it is a pill whose width grows from nothing, hidden behind the handle; from then on it is
        // the wave, so the active part never pops in.
        val length = hx + w / 2
        if (length < stroke) {
            drawRoundRect(colors.accent, Offset(0f, cy - stroke / 2), Size(length, stroke), CornerRadius(minOf(length, stroke) / 2))
        } else {
            val amp = amplitude.value * 3.dp.toPx() * (x / wavelength).coerceIn(0f, 1f)
            path.reset()
            val shift = phase.floatValue * density
            var px = left
            path.moveTo(left, cy + amp * sin((left - shift) * 2f * PI.toFloat() / wavelength))
            // do-while: at the very switch-over the wave has no length yet, and a lone point still draws its round cap.
            do {
                px = (px + 3f).coerceAtMost(activeEnd)
                path.lineTo(px, cy + amp * sin((px - shift) * 2f * PI.toFloat() / wavelength))
            } while (px < activeEnd)
            drawPath(path, colors.accent, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawRoundRect(colors.accent, Offset(hx - w / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
    }
}

/** How fast the wave travels, in dp per second. */
private const val WaveSpeedDp = 36f

/**
 * One line that scrolls when it does not fit, fading out at both ends where it is cut off (only when it overflows). The text
 * carries a leading gap as wide as the left fade, and the whole thing is shifted back by it, so at rest the first letters sit
 * clear of the fade and scrolled text still melts into it.
 */
@Composable
internal fun MarqueeText(text: String, style: TextStyle, color: Color) {
    val measurer = rememberTextMeasurer()
    val fade = 16.dp
    BoxWithConstraints(
        Modifier.fillMaxWidth().layout { measurable, constraints ->
            val gap = fade.roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + gap, maxWidth = constraints.maxWidth + gap))
            layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-gap, 0) }
        },
    ) {
        val room = constraints.maxWidth - with(LocalDensity.current) { fade.roundToPx() }
        val overflows = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width > room
        Text(
            text, style = style, color = color, maxLines = 1,
            modifier = Modifier
                .then(if (overflows) Modifier.fadeEdges(fade, fade + 8.dp) else Modifier)
                .basicMarquee()
                .padding(start = fade),
        )
    }
}

/** Fades the first [start] and the last [end] of this node to transparent. */
private fun Modifier.fadeEdges(start: Dp, end: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val a = (start.toPx() / size.width).coerceIn(0f, 0.5f)
        val b = (end.toPx() / size.width).coerceIn(0f, 0.5f)
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, a to Color.Black, (1f - b) to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

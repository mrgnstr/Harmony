package com.mrgnstr.harmony.feature.nowplaying

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.device.VisualTier
import com.mrgnstr.harmony.core.designsystem.blur.chromeSource
import com.mrgnstr.harmony.core.designsystem.blur.liveChromeBlur
import com.mrgnstr.harmony.core.model.LyricLine
import com.mrgnstr.harmony.core.model.LyricWordCue
import com.mrgnstr.harmony.core.model.Lyrics
import com.mrgnstr.harmony.core.playback.PlaybackProgress
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Main lines are bold and large; what is not being sung sits at the same low opacity, lit text is full.
private val MainSize = 30.sp
private val MainLineHeight = 38.sp
private val SubSize = 20.sp
private val SubLineHeight = 26.sp
/** Pronunciation set under each syllable of the main line: its size, and the room it takes under every visual line. */
private val RubySize = SubSize
private val RubyExtra = SubLineHeight
/** The same for the backing vocal (its own text is [SubSize]). */
private val BgRubySize = 14.sp
private val BgRubyExtra = 18.sp
/** The least room between two pronunciations. */
private val RubyGap = 4.dp
private val TranslationSize = 17.sp
private val TranslationLineHeight = 23.sp
private val SideInset = 32.dp
private val LineInset = 15.dp

private const val DimAlpha = 0.35f
private const val BackgroundDim = 0.25f
private const val BackgroundLit = 0.55f
/** A translation is steady: above the unsung lines and the lit background vocals, below the main line. */
private const val TranslationAlpha = 0.62f
/** Lines that are not being sung shrink to this (from the side they are aligned to). */
private const val IdleScale = 0.92f

/** A gap between two lines at least this long gets the three dots. */
private const val GapDotsMs = 5_000L
/** The soft edge of the sweep, in font sizes. */
private const val FadeEm = 0.45f
/** How much of the sweep's soft ramp is ahead of the sung position at the start (see [edgeOf]). */
private const val EdgeLead = 0.3f
private const val PrerollMs = 120L

// A held word grows and glows: sung for at least this long and this long per letter (so a long word sung fast does not),
// with a strength from GlowMin at GlowFromMs to 1 at GlowFullMs.
private const val GlowFromMs = 900L
private const val GlowPerLetterMs = 220L
private const val GlowFullMs = 2_400L
private const val GlowMin = 0.25f
private const val GlowSettleMs = 1_100f
private const val GlowGrowth = 0.1f

/**
 * What is drawn is ahead of the player's position by this much: the position is of what has been played, the frame shows a
 * frame or two later, and a sweep with a soft edge reads as lit only once the edge has gone by (each word is a quarter of a second).
 */
private const val SyncLeadMs = 180L

/** The lyrics' clock follows its estimate with this time constant, and takes a difference above [SnapMs] (a seek) at once. */
private const val SlewMs = 120.0
private const val SnapMs = 300.0

/** A line lights this much before it is sung, so it is already bright when the first word starts (what is late feels late). */
private const val LeadMs = 250L

// Lines that are not sung are blurred a little more the further they are, until the list is looked around in.
private val BlurFirst = 1.6.dp
private val BlurStep = 1.5.dp
private val BlurMax = 9.dp

/** A syllable rises this much (in font sizes) above the rest of the line, all of it at once, over the time it is sung. */
private const val LiftEm = 0.065f

/** How far the view reaches up under the header: the fade at the top has this much room above the first line that can rest. */
internal val LyricsTopOverlap = 36.dp
private val TopFade = 72.dp
private val BottomFade = 96.dp

/** Where the sung line rests, as a share of the view's height (below the overlap). */
private const val AnchorShare = 0.085f

// How the lines follow the song: each one springs up to its place, the ones below a little after the one above ("pulled").
// The slide is an eased glide (soft start, soft stop) of [FollowMs], started [FollowLeadMs] before the line so it lands as the line starts.
private const val FollowMs = 480f
private const val FollowLeadMs = 480L
private val FollowEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
/** How far a line must run past the next one's start to hold the place (else the glide would wait for it to end). */
private const val OverlapMs = 150L
private const val StaggerMs = 45f
private const val StaggerMax = 7
private const val BrowseResumeMs = 3_500L

/** What is shown, one per list row: a lyric line or the dots of a pause. */
internal sealed class LyricRow {
    abstract val start: Long
    abstract val end: Long

    class Line(val line: LyricLine, override val start: Long, override val end: Long) : LyricRow()
    class Gap(override val start: Long, override val end: Long) : LyricRow()
}

/** Rows for [lyrics]: pauses between lines (breaks in the file, long gaps between lines with known ends, a long intro) become dots. */
internal fun buildRows(lyrics: Lyrics): List<LyricRow> {
    if (!lyrics.synced) return lyrics.lines.map { LyricRow.Line(it, -1L, -1L) }
    val lines = lyrics.lines.sortedBy { it.startMs }
    val rows = ArrayList<LyricRow>(lines.size + 4)
    val first = lines.firstOrNull { !it.isBreak }
    if (first != null && first.startMs >= GapDotsMs - 1_000L && lines.first().let { !it.isBreak || it.startMs > 0L }) {
        rows += LyricRow.Gap(0L, first.startMs)
    }
    for ((i, line) in lines.withIndex()) {
        val next = lines.getOrNull(i + 1)?.startMs
        if (line.isBreak) {
            val end = next ?: (line.startMs + 1)
            if (end - line.startMs >= 1_000L) rows += LyricRow.Gap(line.startMs, end)
            continue
        }
        val known = max(
            line.endMs ?: line.cues.lastOrNull()?.endMs ?: -1L,
            line.background?.let { it.endMs ?: it.cues.lastOrNull()?.endMs } ?: -1L,
        )
        if (known < 0L) {
            rows += LyricRow.Line(line, line.startMs, next ?: (line.startMs + 4_000L))
        } else if (next != null && next - known >= GapDotsMs) {
            rows += LyricRow.Line(line, line.startMs, known)
            rows += LyricRow.Gap(known, next)
        } else {
            rows += LyricRow.Line(line, line.startMs, max(known, next ?: known))
        }
    }
    return rows
}

/** When row [row] stops being lit: a line with syllables when it ends, any other when the next one lights. */
private fun litEndOf(row: LyricRow): Long = when {
    row is LyricRow.Line && row.line.cues.isNotEmpty() -> row.end
    else -> row.end - LeadMs
}

/**
 * The row the list rests on: the first one that is still sung. A line that starts while another is still being sung does not take
 * the place until the one above it is over; with none sung, the last one that started.
 */
private fun focusedRow(rows: List<LyricRow>, litEnds: LongArray, p: Long): Int {
    var lo = 0
    var hi = rows.lastIndex
    var found = 0
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (rows[mid].start - FollowLeadMs <= p) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    var best = found
    for (i in found - 1 downTo max(0, found - 8)) if (p < litEnds[i] && litEnds[i] > rows[found].start + OverlapMs) best = i
    return best
}

/**
 * The position the lyrics are drawn at, plus [SyncLeadMs]. The player's position is a staircase (it changes every ~250 ms, and
 * is up to that old in between), and through the media session it is also corrected every 3 s from such a sample, so it steps by
 * up to ~130 ms: drawn as it is, the sweep ran late and jumped. Each time the position changes, that value is fresh: the clock is
 * anchored there and runs on the frame clock until the next one (a small slew hides what clock drift adds). A big difference
 * (a seek) is taken at once.
 */
@Composable
private fun rememberSmoothPosition(positionMs: Long, playing: Boolean, active: Boolean, positionNow: () -> Long): State<Long> {
    val smooth = remember { mutableLongStateOf(positionMs + SyncLeadMs) }
    LaunchedEffect(positionMs, playing, active) {
        if (!playing || !active) smooth.longValue = positionMs + SyncLeadMs
    }
    LaunchedEffect(playing, active) {
        if (!playing || !active) return@LaunchedEffect
        var last = withFrameNanos { it }
        var lastRaw = positionNow()
        var anchorRaw = lastRaw.toDouble()
        var anchorAt = last
        var shown = anchorRaw
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000.0
            last = now
            val raw = positionNow()
            if (raw != lastRaw) {
                lastRaw = raw
                anchorRaw = raw.toDouble()
                anchorAt = now
            }
            val estimate = anchorRaw + (now - anchorAt) / 1_000_000.0
            shown += dt
            val error = estimate - shown
            if (Math.abs(error) > SnapMs) shown = estimate
            else shown += error * (dt / SlewMs).coerceAtMost(1.0)
            smooth.longValue = shown.toLong() + SyncLeadMs
        }
    }
    return smooth
}

/**
 * Where everything sits. A row is placed at its natural top and moved by `anchor - top(ref) - browse + off[i]`: the row being
 * sung ([ref]) rests at the anchor, [browse] is how far the finger moved the list from there, and [off] is each row's own
 * spring (so the rows follow the sung one one after the other instead of all at once).
 */
@Stable
private class ListState(val count: Int, initialRef: Int = 0) {
    var tops = FloatArray(count + 1)
    var anchor = 0f
    var viewH = 0f
    var synced = true
    var ref by mutableIntStateOf(initialRef)
    var browse by mutableFloatStateOf(0f)
    var browsing by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    var kick by mutableIntStateOf(0)
    val off = FloatArray(count)
    val from = FloatArray(count)
    val t = FloatArray(count)
    val delayMs = FloatArray(count)

    fun dy(i: Int): Float {
        tick
        return anchor - tops[ref] - browse + off[i]
    }

    /** The row being sung changed. Following: the others spring from where they were. Browsing: the list stays where the finger left it. */
    fun follow(new: Int, animate: Boolean = true) {
        if (new == ref) return
        val d = tops[new] - tops[ref]
        if (!animate && !browsing) {
            // Not while the list is still sliding in: it is just there.
            ref = new
            return
        }
        if (browsing) {
            browse -= d
            ref = new
            return
        }
        val cap = viewH * 1.2f
        val capped = d.coerceIn(-cap, cap)
        for (i in 0 until count) {
            off[i] += capped
            from[i] = off[i]
            t[i] = 0f
            delayMs[i] = if (i > new) min(i - new, StaggerMax) * StaggerMs else 0f
        }
        // What the cap took off is a jump.
        if (capped != d) browse += d - capped
        ref = new
        kick++
    }

    fun range(): ClosedFloatingPointRange<Float> {
        val refTop = tops[ref]
        val last = count - 1
        return if (synced) -refTop..max(-refTop, tops[last] - refTop)
        else -refTop..max(-refTop, tops[count] + anchor - viewH + 120f * 3f - refTop)
    }
}

/** Steps every row's glide on the frame clock until all rest. */
@Composable
private fun FollowDriver(state: ListState) {
    LaunchedEffect(state) {
        snapshotFlow { state.kick }.collect {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.032f)
                last = now
                var moving = false
                for (i in 0 until state.count) {
                    if (state.delayMs[i] > 0f) {
                        state.delayMs[i] -= dt * 1000f
                        moving = true
                        continue
                    }
                    if (state.t[i] < FollowMs) {
                        state.t[i] += dt * 1000f
                        val k = FollowEasing.transform((state.t[i] / FollowMs).coerceIn(0f, 1f))
                        state.off[i] = state.from[i] * (1f - k)
                        moving = true
                    } else {
                        state.off[i] = 0f
                    }
                }
                state.tick++
                if (!moving) break
            }
        }
    }
}

/**
 * The lyrics: the sung line rests near the top, bold and lit, the rest dimmer; the lines follow the song with a ripple. Dragging
 * the list looks around (it does not follow while the finger is on it, and comes back a few seconds after). [listTop] is where
 * this view starts on the screen, to line the fade above the controls up with them.
 */
@Composable
internal fun LyricsView(
    lyrics: Lyrics?,
    progress: PlaybackProgress,
    playing: Boolean,
    positionNow: () -> Long,
    panel: PanelState,
    listTop: Float,
    fileAccess: Boolean,
    slide: () -> Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val topFade = with(density) { TopFade.toPx() }
    val bottomFade = with(density) { BottomFade.toPx() }
    val aboveControls = with(density) { 6.dp.toPx() }
    val frosted = liveChromeBlur(panel.frost)
    Box(
        modifier
            // While the controls are shown, a touch that starts in their area is theirs (the gaps between the buttons would otherwise
            // reach the lines under them and seek); it is held back from the lines until the finger is up.
            .pointerInput(panel, listTop) {
                val blocked = HashSet<PointerId>()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val edge = (panel.controlsTop - listTop - aboveControls).coerceIn(topFade * 2, size.height.toFloat())
                        for (change in event.changes) {
                            if (change.changedToDown() && panel.controlsHidden.value < 0.5f && change.position.y > edge) blocked += change.id
                            if (change.id in blocked) {
                                change.consume()
                                if (change.changedToUp() || !change.pressed) blocked -= change.id
                            }
                        }
                    }
                }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // The pill and the button have a blur of the lyrics on them: the sharp ones are cut out under them.
                if (frosted) {
                    val keep = 1f - panel.controlsHidden.value
                    for (r in listOfNotNull(panel.pillRect, panel.buttonRect)) {
                        drawRoundRect(
                            Color.Black.copy(alpha = keep), Offset(r.left, r.top - listTop), androidx.compose.ui.geometry.Size(r.width, r.height),
                            androidx.compose.ui.geometry.CornerRadius(r.height / 2f), blendMode = BlendMode.DstOut,
                        )
                    }
                }
                // Fades out under the header, and above the controls (down to the screen's bottom when they are away).
                val shownEdge = (panel.controlsTop - listTop - aboveControls).coerceIn(topFade * 2, size.height)
                val edge = lerp(shownEdge, size.height, panel.controlsHidden.value)
                val fade = min(bottomFade, edge / 2)
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent, topFade / size.height to Color.Black,
                        (edge - fade) / size.height to Color.Black, (edge / size.height).coerceAtMost(1f) to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        // The fade above stays where the controls are while the lyrics slide up into it; what is behind the pill and the
        // translation button is this content, blurred.
        Box(
            Modifier
                .fillMaxSize()
                .chromeSource(panel.frost)
                .graphicsLayer {
                    translationY = slide()
                    alpha = (panel.q() * 2f).coerceAtMost(1f)
                },
        ) {
            if (lyrics == null || lyrics.lines.isEmpty()) {
                LyricsEmpty(fileAccess)
            } else {
                val rows = remember(lyrics) { buildRows(lyrics) }
                LyricsList(rows, lyrics.synced, progress, playing, positionNow, panel, onSeek)
            }
        }
    }
}

@Composable
private fun LyricsEmpty(fileAccess: Boolean) {
    // Shown only after a moment: lyrics load after the song changes, and a flash of "No lyrics" in between is wrong.
    val alpha = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay(600)
        alpha.floatValue = 1f
    }
    val shown = animateFloatAsState(alpha.floatValue, tween(300), label = "empty")
    Column(
        Modifier.fillMaxSize().graphicsLayer { this.alpha = shown.value },
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No lyrics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = DimAlpha + 0.2f))
        if (!fileAccess) {
            val context = LocalContext.current
            Spacer(Modifier.height(16.dp))
            Text(
                "Allow Access to Lyrics Files",
                Modifier
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable { openAllFilesAccess(context) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            )
        }
    }
}

@Composable
private fun LyricsList(
    rows: List<LyricRow>,
    synced: Boolean,
    progress: PlaybackProgress,
    playing: Boolean,
    positionNow: () -> Long,
    panel: PanelState,
    onSeek: (Long) -> Unit,
) {
    val density = LocalDensity.current
    val pos = rememberSmoothPosition(progress.positionMs, playing, active = synced, positionNow)
    val litEnds = remember(rows) { LongArray(rows.size) { litEndOf(rows[it]) } }
    // The list starts on the sung row (no ripple on the way in).
    val state = remember(rows) { ListState(rows.size, if (synced) focusedRow(rows, litEnds, pos.value) else 0).also { it.synced = synced } }
    FollowDriver(state)
    // The glow of a held word is for a song that plays; the blur of the lines that are not sung, for a list that follows it.
    val play = animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "play")

    // The row being sung.
    val sung by remember(rows) { derivedStateOf { if (!synced) 0 else focusedRow(rows, litEnds, pos.value) } }
    LaunchedEffect(state) { snapshotFlow { sung }.collect { state.follow(it, animate = panel.q() > 0.98f) } }

    // Looking around: the finger moves the list, and some seconds after it lets go the list returns to the song.
    val scroll = rememberScrollableState { delta ->
        val r = state.range()
        val old = state.browse
        state.browse = (old - delta).coerceIn(r.start, r.endInclusive)
        state.browsing = true
        -(state.browse - old)
    }
    LaunchedEffect(state.browsing, scroll.isScrollInProgress, panel.touchTick) {
        if (!state.browsing || scroll.isScrollInProgress) return@LaunchedEffect
        delay(BrowseResumeMs)
        resume(state)
    }
    val blurOn = animateFloatAsState(if (state.browsing || scroll.isScrollInProgress) 0f else 1f, tween(if (state.browsing) 90 else 450), label = "blur-on")
    val seek: (Long) -> Unit = { ms ->
        onSeek(ms)
        // Straight back to following (the song is about to be there).
        if (state.browsing) state.browsing = false
    }
    LaunchedEffect(state.browsing) { if (!state.browsing && state.browse != 0f) resume(state) }

    Layout(
        content = {
            rows.forEachIndexed { i, row ->
                key(i) {
                    when (row) {
                        is LyricRow.Line -> LineItem(row, synced, pos, play, panel, abs(i - state.ref), blurOn, seek)
                        is LyricRow.Gap -> GapItem(row, pos)
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .scrollable(scroll, Orientation.Vertical, flingBehavior = ScrollableDefaults.flingBehavior()),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val placeables = measurables.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
        val tops = FloatArray(placeables.size + 1)
        var y = 0f
        placeables.forEachIndexed { i, p -> tops[i] = y; y += p.height }
        tops[placeables.size] = y
        state.tops = tops
        state.viewH = h.toFloat()
        val overlap = with(density) { LyricsTopOverlap.toPx() }
        state.anchor = overlap + if (synced) (h - overlap) * AnchorShare else with(density) { 12.dp.toPx() }
        layout(w, h) {
            placeables.forEachIndexed { i, p ->
                p.placeWithLayer(0, tops[i].roundToInt()) { translationY = state.dy(i) }
            }
        }
    }
}

/** Eases the list's browsing offset back to zero: the sung line is at its place again. */
private suspend fun resume(state: ListState) {
    state.browsing = false
    val from = state.browse
    if (from == 0f) return
    val anim = androidx.compose.animation.core.Animatable(from)
    anim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 150f, visibilityThreshold = 0.5f)) {
        if (!state.browsing) state.browse = value
    }
    if (!state.browsing) state.browse = 0f
}

// -- lines ---------------------------------------------------------------------------------------------------------------

@Composable
private fun LineItem(
    row: LyricRow.Line,
    synced: Boolean,
    pos: State<Long>,
    play: State<Float>,
    panel: PanelState,
    distance: Int,
    blurOn: State<Float>,
    onSeek: (Long) -> Unit,
) {
    val line = row.line
    // Lit a little before it is sung (and, without syllables, handing over to the next line as that one lights).
    val litEnd = litEndOf(row)
    val lit by remember(row, synced) { derivedStateOf { !synced || pos.value in (row.start - LeadMs) until litEnd } }
    val litAmount = animateFloatAsState(if (lit) 1f else 0f, tween(if (lit) 300 else 450, easing = FastOutSlowInEasing), label = "lit")
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocalVisualTier.current != VisualTier.LOW
    val blurTarget = if (!synced || !canBlur || lit || distance == 0) 0f else min(BlurFirst.value + BlurStep.value * (distance - 1), BlurMax.value)
    val blurDp = animateFloatAsState(blurTarget, tween(400), label = "blur")
    val density = LocalDensity.current.density
    val align = if (line.duet) TextAlign.End else TextAlign.Start
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = if (synced) lerp(IdleScale, 1f, litAmount.value) else 1f
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(if (line.duet) 1f else 0f, 0.5f)
                val r = blurDp.value * blurOn.value * density
                if (r > 0.05f) renderEffect = BlurEffect(r, r, TileMode.Decal)
            }
            .then(if (synced) Modifier.clickable(interactionSource = null, indication = null) { onSeek(row.start) } else Modifier)
            .padding(horizontal = SideInset, vertical = LineInset),
        horizontalAlignment = if (line.duet) Alignment.End else Alignment.Start,
    ) {
        // With timed pronunciation the syllables carry it under them (as in Apple Music); otherwise it is a line of its own.
        val ruby = if (panel.showPronunciation && synced) remember(line, row.end) { rubyOf(line, row.end) } else null
        LyricText(
            text = line.text, cues = line.cues, litAmount = litAmount, play = play, pos = pos, lineEnd = row.end,
            size = MainSize, lineHeight = MainLineHeight, align = align, dim = DimAlpha, litAlpha = 1f, glow = true, ruby = ruby,
        )
        if (ruby == null && panel.showPronunciation && !line.pronunciation.isNullOrBlank()) {
            // A line-by-line line (no syllable timing) lights as a whole, its pronunciation too: no sweep.
            val cues = if (line.cues.isEmpty()) emptyList()
            else line.pronunciationCues.ifEmpty { listOf(LyricWordCue(row.start, row.end, text = line.pronunciation)) }
            LyricText(
                text = line.pronunciation, cues = if (synced) cues else emptyList(), litAmount = litAmount, play = play, pos = pos, lineEnd = row.end,
                size = SubSize, lineHeight = SubLineHeight, align = align, dim = DimAlpha, litAlpha = 1f, glow = false,
            )
        }
        if (panel.showTranslation && !line.translation.isNullOrBlank()) {
            Text(
                line.translation,
                Modifier.fillMaxWidth().graphicsLayer { alpha = TranslationAlpha },
                fontSize = TranslationSize, lineHeight = TranslationLineHeight, fontWeight = FontWeight.SemiBold,
                color = Color.White, textAlign = align,
            )
        }
        val bg = line.background
        if (bg != null && synced) {
            // Only while the line it belongs to is sung: it opens under the line and folds away after.
            val open = animateFloatAsState(if (lit) 1f else 0f, spring(dampingRatio = 1f, stiffness = 300f), label = "bg-open")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints)
                        val h = (p.height * open.value).roundToInt()
                        layout(p.width, h) { p.place(0, 0) }
                    }
                    .graphicsLayer { alpha = open.value },
            ) {
                val cues = bg.cues.ifEmpty { listOf(LyricWordCue(bg.startMs, bg.endMs ?: row.end, text = bg.text)) }
                val bgRuby = if (panel.showPronunciation) remember(bg, row.end) { rubyOf(bg, bg.endMs ?: row.end) } else null
                LyricText(
                    text = bg.text, cues = cues, litAmount = litAmount, play = play, pos = pos, lineEnd = bg.endMs ?: row.end,
                    size = SubSize, lineHeight = SubLineHeight, align = align, dim = BackgroundDim, litAlpha = BackgroundLit, glow = false,
                    modifier = Modifier.padding(top = 4.dp), ruby = bgRuby, rubySize = BgRubySize, rubyExtra = BgRubyExtra,
                )
            }
        }
    }
}

/** The dots of a pause: folded away until it is playing, then they light up one by one over its length. */
@Composable
private fun GapItem(row: LyricRow.Gap, pos: State<Long>) {
    val on by remember(row) { derivedStateOf { pos.value in row.start until litEndOf(row) } }
    val open = animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 1f, stiffness = 260f), label = "gap-open")
    val dot = 10.dp
    val full = 54.dp
    Canvas(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = full.roundToPx()))
                val h = (full.roundToPx() * open.value).roundToInt()
                layout(p.width, h) { p.place(0, 0) }
            }
            .height(full)
            .graphicsLayer { alpha = open.value },
    ) {
        val f = if (on) ((pos.value - row.start).toFloat() / (row.end - row.start).coerceAtLeast(1L)).coerceIn(0f, 1f) else 0f
        val r = dot.toPx() / 2
        val gap = 8.dp.toPx()
        // The dots breathe a little while the pause plays, and the last one leaves as it ends.
        val pulse = 1f + 0.12f * kotlin.math.sin(pos.value / 400f)
        for (d in 0 until 3) {
            val lit = (f * 3f - d).coerceIn(0f, 1f)
            val x = SideInset.toPx() + r + d * (2 * r + gap)
            drawCircle(Color.White.copy(alpha = lerp(DimAlpha, 1f, lit)), r * pulse, Offset(x, size.height / 2f))
        }
    }
}

// -- the text of one line --------------------------------------------------------------------------------------------------

/** Where a cue sits in the line's text: chars [a, b) (trailing spaces included), over [t0, t1] ms. */
private class Located(val a: Int, val b: Int, val t0: Long, val t1: Long, val wordEnd: Boolean)

/** Finds each cue in [text], in order (the cue text may leave out the spaces between words); null when one is not there. */
private fun locate(text: String, cues: List<LyricWordCue>, lineEnd: Long): List<Located>? {
    if (cues.isEmpty()) return null
    val out = ArrayList<Located>(cues.size)
    var cursor = 0
    val noSpaces = text.none { it.isWhitespace() }
    for ((k, cue) in cues.withIndex()) {
        val core = cue.text.trim()
        if (core.isEmpty()) continue
        val a = text.indexOf(core, cursor)
        if (a < 0) return null
        var b = a + core.length
        while (b < text.length && text[b].isWhitespace()) b++
        cursor = b
        val next = cues.getOrNull(k + 1)?.startMs
        val end = max(cue.startMs, cue.endMs ?: next ?: lineEnd)
        out += Located(a, b, cue.startMs, end, wordEnd = noSpaces || b >= text.length || text[b - 1].isWhitespace())
    }
    return out.takeIf { it.isNotEmpty() }
}

/**
 * The pronunciation of each located cue of [line] (same order as [locate]), matched by time; null when the line has no timed
 * pronunciation or its cues cannot be found in the text.
 */
private fun rubyOf(line: LyricLine, lineEnd: Long): List<String>? {
    if (line.cues.isEmpty() || line.pronunciationCues.isEmpty()) return null
    val located = locate(line.text, line.cues, lineEnd) ?: return null
    val out = Array(located.size) { StringBuilder() }
    for (pc in line.pronunciationCues) {
        val k = located.indexOfLast { it.t0 <= pc.startMs + 30 }.coerceAtLeast(0)
        out[k].append(pc.text)
    }
    return out.map { it.toString().trim() }.takeIf { r -> r.any { it.isNotEmpty() } }
}

private const val WordJoiner = '\u2060'

/** [text] with a word joiner between the characters of each cue in [located] (not next to a space), and [located] moved to match. */
private fun joinCues(text: String, located: List<Located>): Pair<String, List<Located>> {
    val sb = StringBuilder()
    var cursor = 0
    val moved = ArrayList<Located>(located.size)
    for (loc in located) {
        sb.append(text, cursor, loc.a)
        val a = sb.length
        for (i in loc.a until loc.b) {
            sb.append(text[i])
            if (i < loc.b - 1 && !text[i].isWhitespace() && !text[i + 1].isWhitespace()) sb.append(WordJoiner)
        }
        moved += Located(a, sb.length, loc.t0, loc.t1, loc.wordEnd)
        cursor = loc.b
    }
    sb.append(text, cursor, text.length)
    return sb.toString() to moved
}

/** A cue's slice of one visual line, in sweep space (mirrored for right-to-left text), with the share of the cue's time it gets. */
private class Piece(val l: Float, val r: Float, val t0: Long, val t1: Long, val realL: Float, val realR: Float)
private class VLine(val top: Float, val bottom: Float) { val pieces = ArrayList<Piece>() }
/** One syllable (a cue): [text] without its trailing space. It grows and glows if it alone is held long enough. */
private class Word(val t0: Long, val t1: Long, val pieces: List<Pair<VLine, Piece>>, val text: String) {
    val sung: Long = t1 - t0
    val letters: Int = text.count { it.isLetterOrDigit() }
    /** Held long enough (this syllable, not the word it is in), on one line. */
    val glows: Boolean = pieces.size == 1 && sung >= GlowFromMs && sung >= GlowPerLetterMs * letters
    val strength: Float = lerp(GlowMin, 1f, ((sung - GlowFromMs).toFloat() / (GlowFullMs - GlowFromMs)).coerceIn(0f, 1f))
}
private class Geo(val lines: List<VLine>, val words: List<Word>, val rtl: Boolean, val width: Float)

private fun buildGeo(layout: TextLayoutResult, text: String, located: List<Located>): Geo {
    val width = layout.size.width.toFloat()
    val rtl = layout.getParagraphDirection(0) == ResolvedTextDirection.Rtl
    val vlines = LinkedHashMap<Int, VLine>()
    val words = ArrayList<Word>()
    for (loc in located) {
        val cuePieces = ArrayList<Pair<VLine, Piece>>()
        // The cue's chars, grouped per visual line.
        val groups = ArrayList<Triple<Int, Float, Float>>()
        var i = loc.a
        while (i < loc.b) {
            val ln = layout.getLineForOffset(i)
            var l = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            while (i < loc.b && layout.getLineForOffset(i) == ln) {
                val box = layout.getBoundingBox(i)
                l = min(l, box.left); r = max(r, box.right)
                i++
            }
            groups += Triple(ln, l, r)
        }
        val total = groups.sumOf { (it.third - it.second).toDouble() }.toFloat().coerceAtLeast(1f)
        var acc = 0f
        for ((ln, l, r) in groups) {
            val t0 = loc.t0 + ((loc.t1 - loc.t0) * (acc / total)).toLong()
            acc += r - l
            val t1 = loc.t0 + ((loc.t1 - loc.t0) * (acc / total)).toLong()
            val vl = vlines.getOrPut(ln) { VLine(layout.getLineTop(ln), layout.getLineBottom(ln)) }
            val piece = if (rtl) Piece(width - r, width - l, t0, t1, l, r) else Piece(l, r, t0, t1, l, r)
            vl.pieces += piece
            cuePieces += vl to piece
        }
        words += Word(loc.t0, loc.t1, cuePieces, text.substring(loc.a, loc.b).trimEnd())
    }
    return Geo(vlines.values.toList(), words, rtl, width)
}

/**
 * Where the sweep's edge (the far end of its soft ramp) is on [line] at [t]: continuous along the syllables. The ramp sits partly
 * AHEAD of the position ([EdgeLead] of its width, growing to all of it at the end), so a letter reads as lit when it is sung and
 * not a ramp's width later (which on a slow song is a long time); before the first syllable it eases in over [PrerollMs].
 */
private fun edgeOf(line: VLine, t: Long, fade: Float): Float {
    val first = line.pieces.first()
    val last = line.pieces.last()
    if (t >= last.t1) return last.r + fade
    if (t < first.t0) return first.l + fade * EdgeLead * smooth((t - (first.t0 - PrerollMs)).toFloat() / PrerollMs)
    var x = last.r
    for (p in line.pieces) {
        if (t < p.t1) {
            x = if (t <= p.t0) p.l else p.l + (p.r - p.l) * (t - p.t0).toFloat() / (p.t1 - p.t0).coerceAtLeast(1L)
            break
        }
    }
    val share = (x - first.l) / (last.r - first.l).coerceAtLeast(1f)
    return x + fade * (EdgeLead + (1f - EdgeLead) * share)
}

/** 0 → 1 over the time a syllable is sung (a very short one still takes a moment). */
private fun riseOf(t: Long, start: Long, end: Long): Float = smooth((t - start).toFloat() / (end - start).coerceAtLeast(80L))

private fun smooth(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/**
 * 0 → [Word.strength] over the time the syllable is sung (it starts at once and is soft at both ends), then a long soft tail
 * back to 0 after it ends.
 */
private fun glowOf(word: Word, t: Long): Float {
    val p = ((t - word.t0).toFloat() / (word.t1 - word.t0).coerceAtLeast(1L)).coerceIn(0f, 1f)
    // Early in the syllable, not late: p^0.6 under a smoothstep.
    val up = smooth(Math.pow(p.toDouble(), 0.6).toFloat())
    val after = if (t > word.t1) 1f - smooth((t - word.t1) / GlowSettleMs) else 1f
    return up * after * word.strength
}

/** The glow follows the sweep: strongest just behind its edge, trailing off behind it (the part not yet sung has none). */
private fun glowBrush(edge: Float, fade: Float): Brush {
    val span = fade * 4f
    return Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.3f), 0.7f to Color.White, 1f to Color.Transparent, startX = edge - span, endX = edge)
}

private fun DrawScope.clipOutAll(rects: List<Rect>, i: Int, block: DrawScope.() -> Unit) {
    if (i >= rects.size) block() else clipRect(rects[i].left, rects[i].top, rects[i].right, rects[i].bottom, androidx.compose.ui.graphics.ClipOp.Difference) {
        clipOutAll(rects, i + 1, block)
    }
}

private fun sweepBrush(edge: Float, fade: Float, rtl: Boolean, width: Float): Brush =
    if (!rtl) Brush.horizontalGradient(0f to Color.White, 1f to Color.Transparent, startX = edge - fade, endX = edge)
    else Brush.horizontalGradient(0f to Color.White, 1f to Color.Transparent, startX = width - (edge - fade), endX = width - edge)

/**
 * One line of text. Without timed syllables it is dimmed, and lit as a whole while it is sung. With them, the text is dimmed and
 * a lit copy is swept over it: its edge slides through the syllables with their timing and fades out softly. A word held long
 * enough grows and glows ([glow]) while the line is sung and the song plays, and settles back after: it is drawn on its own (its
 * place cut out of the line) with a blurred copy behind it as the glow.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun LyricText(
    text: String,
    cues: List<LyricWordCue>,
    litAmount: State<Float>,
    play: State<Float>,
    pos: State<Long>,
    lineEnd: Long,
    size: TextUnit,
    lineHeight: TextUnit,
    align: TextAlign,
    dim: Float,
    litAlpha: Float,
    glow: Boolean,
    modifier: Modifier = Modifier,
    /** One pronunciation per located cue, drawn centred under its syllable in room added under every visual line. */
    ruby: List<String>? = null,
    rubySize: TextUnit = RubySize,
    rubyExtra: TextUnit = RubyExtra,
) {
    val located = remember(text, cues, lineEnd) { locate(text, cues, lineEnd) }
    val rubyOn = ruby != null && located != null && ruby.size == located.size
    val style = TextStyle(
        fontSize = size, fontWeight = FontWeight.Bold, textAlign = align,
        lineHeight = if (rubyOn) (lineHeight.value + rubyExtra.value).sp else lineHeight,
        lineHeightStyle = if (rubyOn) LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None) else null,
    )
    if (located == null) {
        Text(
            text, modifier.fillMaxWidth().graphicsLayer { alpha = lerp(dim, litAlpha, litAmount.value) },
            style = style, color = Color.White,
        )
        return
    }
    // With pronunciations a syllable must not be split over two lines (its pronunciation could not follow it): word joiners keep
    // its characters together, and [placed] is [located] in the joined text.
    val (shown, placed) = remember(text, located, rubyOn) { if (rubyOn) joinCues(text, located) else text to located }
    var layout by remember(shown) { mutableStateOf<TextLayoutResult?>(null) }
    val geo = remember(layout, placed) { layout?.let { buildGeo(it, shown, placed) } }
    // The ones that glow at the same time need a layer each.
    val glowLayers = if (glow) listOf(rememberGraphicsLayer(), rememberGraphicsLayer()) else emptyList()
    val density = LocalDensity.current
    val fade = with(density) { size.toPx() } * FadeEm
    val liftPx = with(density) { size.toPx() } * LiftEm
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val measurer = rememberTextMeasurer()
    val rubyStyle = TextStyle(fontSize = rubySize, fontWeight = FontWeight.Bold)
    val rubyLayouts = remember(ruby, rubyOn, density) {
        if (rubyOn) ruby!!.map { if (it.isEmpty()) null else measurer.measure(it, rubyStyle) } else emptyList()
    }
    // A syllable narrower than its pronunciation is followed by the missing width (as letter spacing on its last char), so the
    // pronunciations never run into each other.
    val display = remember(shown, placed, rubyLayouts, style, density) {
        if (!rubyOn) AnnotatedString(shown)
        else {
            val plain = measurer.measure(shown, style, softWrap = false)
            val gap = with(density) { RubyGap.toPx() }
            val b = AnnotatedString.Builder(shown)
            for ((i, loc) in placed.withIndex()) {
                val r = rubyLayouts.getOrNull(i) ?: continue
                val w = plain.getBoundingBox(loc.b - 1).right - plain.getBoundingBox(loc.a).left
                val extra = r.size.width + gap - w
                if (extra > 0.5f) b.addStyle(SpanStyle(letterSpacing = with(density) { extra.toSp() }), loc.b - 1, loc.b)
            }
            b.toAnnotatedString()
        }
    }
    Text(
        display,
        modifier
            .fillMaxWidth()
            .drawWithContent {
                val g = geo
                val l = layout
                val amount = litAmount.value
                // The pronunciations sit still under their syllables: dim, with the line's sweep lit over them.
                fun drawRuby(lit: Boolean, t: Long) {
                    if (g == null || g.rtl) return
                    for ((wi, w) in g.words.withIndex()) {
                        val r = rubyLayouts.getOrNull(wi) ?: continue
                        val (vl, p) = w.pieces[0]
                        // Flush with the start of its syllable (its end, for the right-aligned second voice).
                        val x = (if (align == TextAlign.End) p.realR - r.size.width else p.realL).coerceIn(0f, max(0f, g.width - r.size.width))
                        val at = Offset(x, vl.bottom - rubyExtra.toPx())
                        if (!lit) drawText(r, Color.White, topLeft = at, alpha = dim)
                        else {
                            // Its own sweep, over the time its syllable is sung and over its own width (it can be wider than the
                            // syllable); the brush is in the text's own coordinates.
                            val pr = ((t - w.t0).toFloat() / (w.t1 - w.t0).coerceAtLeast(1L)).coerceIn(0f, 1f)
                            val edge = r.size.width * pr + fade * (EdgeLead + (1f - EdgeLead) * pr) * (if (t > w.t0) 1f else 0f)
                            drawText(r, sweepBrush(edge, fade, false, r.size.width.toFloat()), topLeft = at, alpha = litAlpha * amount)
                        }
                    }
                }
                if (g == null || l == null || amount <= 0.001f) {
                    drawRuby(false, 0L)
                    drawContent()
                    return@drawWithContent
                }
                val t = pos.value
                if (rubyOn) { drawRuby(false, t); drawRuby(true, t) }
                // While the line is lit the syllables that can glow are drawn on their own (also at rest, so nothing shifts when one starts).
                val own = if (!glow || g.rtl) emptyList() else g.words.indices.filter { g.words[it].glows }
                val holes = own.map { val (vl, p) = g.words[it].pieces[0]; Rect(p.realL, vl.top, p.realR, vl.bottom) }
                clipOutAll(holes, 0) {
                    for (vl in g.lines) {
                        val edge = edgeOf(vl, t, fade)
                        val brush = if (edge > vl.pieces.first().l + 0.5f) sweepBrush(edge, fade, g.rtl, g.width) else null
                        // Every syllable rises as a whole when it starts: the ones that have the same lift (all those done, all those
                        // to come) are drawn as one run, the one or two on their way up on their own.
                        val pieces = vl.pieces
                        var i = 0
                        while (i < pieces.size) {
                            val lift = liftPx * amount * riseOf(t, pieces[i].t0, pieces[i].t1)
                            var j = i + 1
                            while (j < pieces.size && abs(liftPx * amount * riseOf(t, pieces[j].t0, pieces[j].t1) - lift) < 0.25f) j++
                            val from = if (i == 0) 0f else pieces[i].l
                            val to = if (j >= pieces.size) g.width else pieces[j].l
                            clipLine(vl, g, from, to, liftPx * 1.5f) {
                                translate(top = -lift) {
                                    drawText(l, Color.White, alpha = dim)
                                    if (brush != null) drawText(l, brush, alpha = litAlpha * amount)
                                }
                            }
                            i = j
                        }
                    }
                }
                var layerIndex = 0
                for (wi in own) {
                    val w = g.words[wi]
                    val (vl, p) = w.pieces[0]
                    val strength = (glowOf(w, t) * amount * play.value).coerceIn(0f, 1f)
                    val scale = 1f + GlowGrowth * strength
                    val lift = liftPx * amount * riseOf(t, w.t0, w.t1)
                    val edge = edgeOf(vl, t, fade)
                    val brush = sweepBrush(edge, fade, false, g.width)
                    // The line's own glyphs (the same font and place as the rest of it), scaled about the syllable's bottom centre.
                    withTransform({ scale(scale, scale, Offset((p.realL + p.realR) / 2f, vl.bottom)) }) {
                        translate(top = -lift) {
                            if (blurs && strength > 0.01f && layerIndex < glowLayers.size) {
                                // The glow: a blurred copy of the lit part, behind it.
                                val layer = glowLayers[layerIndex++]
                                val r = (6.dp.toPx() + 10.dp.toPx() * strength)
                                layer.renderEffect = BlurEffect(r, r, TileMode.Decal)
                                layer.alpha = 0.9f * strength
                                layer.record(this, layoutDirection, IntSize(l.size.width, l.size.height)) {
                                    clipRect(p.realL, vl.top, p.realR, vl.bottom) {
                                        drawText(l, glowBrush(edge, fade), alpha = litAlpha * amount)
                                    }
                                }
                                drawLayer(layer)
                            }
                            clipRect(p.realL, vl.top - liftPx * 1.5f, p.realR, vl.bottom) {
                                drawText(l, Color.White, alpha = dim)
                                drawText(l, brush, alpha = litAlpha * amount)
                            }
                        }
                    }
                }
            },
        style = style, color = Color.White.copy(alpha = dim),
        onTextLayout = { layout = it },
    )
}

/** Clips to [from]..[to] (sweep space, mirrored for right-to-left text) of visual line [vl], [extraTop] taller above. */
private fun DrawScope.clipLine(vl: VLine, g: Geo, from: Float, to: Float, extraTop: Float, block: DrawScope.() -> Unit) {
    val a = if (!g.rtl) from else g.width - to
    val b = if (!g.rtl) to else g.width - from
    clipRect(a, vl.top - extraTop, b, vl.bottom, block = block)
}

/** This app's all-files access page (Android 11+); the general list where a device lacks the per-app page. */
private fun openAllFilesAccess(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

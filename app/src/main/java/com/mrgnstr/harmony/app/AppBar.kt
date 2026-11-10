package com.mrgnstr.harmony.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.common.rememberTickHaptics
import com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
import com.mrgnstr.harmony.core.designsystem.blur.FloatingChromeMargin
import com.mrgnstr.harmony.core.designsystem.blur.FloatingChromeWidth
import com.mrgnstr.harmony.core.designsystem.icons.AnimatedSymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbol
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import com.mrgnstr.harmony.core.designsystem.motion.pressResponse
import com.mrgnstr.harmony.core.designsystem.motion.rubberBand
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Height of the floating tab bar and the search button. */
val AppBarHeight = 64.dp
/**
 * The side (vertical) bar is [AppBarHeight] wide; each of its two tabs is at most this long. [sideBarSlotLength] shortens them where
 * the window is too short for the whole bar.
 */
val SideBarMaxSlotLength = 120.dp

/**
 * The tab length when [free] is the height the bar may use: [SideBarMaxSlotLength] if the whole bar (Search, gap, two tabs) fits in it,
 * otherwise scaled down to what does (never below a usable minimum).
 */
fun sideBarSlotLength(free: Dp): Dp = ((free - AppBarHeight - BarGap) / 2).coerceIn(MinSlotLength, SideBarMaxSlotLength)

private val MinSlotLength = 44.dp
/** Space between the bar and the navigation bar / screen edge. */
val AppBarEdgeGap = 12.dp
internal val BarGap = 10.dp
private val IndicatorInset = 6.dp
/** The least a finger travels to pull the indicator out into Search (the vertical bar is short; see [BarGeometry]). */
private val MinPull = 100.dp

private val AllTabs = listOf(Tab.Home, Tab.Library, Tab.Search)
private const val SearchIndex = 2
private const val LiftScale = 1.08f
// Between Library and Search (fractions of the distance the indicator travels to leave the pill): it stretches out of the pill,
// slowing as it goes (it cannot get past StretchReach), until the finger has pulled ChooseAt far; then the resistance gives,
// Search is chosen and the indicator slips out. Pulling back to LetGoAt (less, so it doesn't flicker) takes Search back.
private const val StretchReach = 0.65f
private const val ChooseAt = 0.7f
private const val LetGoAt = 0.5f
/** Between Home and Library the indicator sits on a tab and only leans this far towards the finger; it hops to the next at halfway. */
private const val MagnetPull = 0.3f
/** How far the Search button jumps up in size when a held indicator is dragged far enough to choose it. */
private const val SearchPop = 1.12f
/** The indicator is this much smaller by the time it has slid out of the pill. */
private const val ExitShrink = 0.85f

private fun Tab.symbol(): Symbol = when (this) {
    Tab.Home -> Symbols.Home
    Tab.Library -> Symbols.Library
    Tab.Search -> Symbols.Search
}

/**
 * Where the three slots sit along the bar's axis, in px, counted from the axis's start: the left edge of the horizontal bar, the
 * bottom edge of the vertical one (Home at the start, Search at the end). The indicator is one number (its centre) running through all
 * of them, so everything below is the same in both directions.
 */
internal class BarGeometry(val pill: Float, val button: Float, val gap: Float, val inset: Float, minPull: Float = 0f) {
    val item = pill / 2
    /** Search's slot is just outside the pill's edge: the indicator slides out of view there and is never drawn in the button. */
    val centers = floatArrayOf(item / 2, item * 1.5f, pill + (item - 2 * inset) / 2)

    /** [x] in tab units: 0 = Home, 1 = Library, 2 = Search. */
    fun unitsAt(x: Float): Float = when {
        x <= centers[0] -> 0f
        x <= centers[1] -> (x - centers[0]) / (centers[1] - centers[0])
        x <= centers[2] -> 1f + (x - centers[1]) / (centers[2] - centers[1])
        else -> 2f
    }

    fun progressToSearch(x: Float) = ((x - centers[1]) / (centers[2] - centers[1])).coerceIn(0f, 1f)

    /** The indicator shrinks a bit as it leaves the pill. */
    fun scaleAt(x: Float) = androidx.compose.ui.util.lerp(1f, ExitShrink, progressToSearch(x))

    fun nearest(x: Float) = centers.indices.minByOrNull { kotlin.math.abs(centers[it] - x) } ?: 0

    val span get() = centers[2] - centers[1]

    /**
     * How far the finger travels (px) for the indicator to cover [span]: at least [minPull], so a short bar (the vertical one) still has
     * a long pull and a visible stretch before Search is chosen. The indicator itself only ever moves within [span].
     */
    private val k = maxOf(1f, minPull / span)
    val pull get() = span * k

    /** Where the indicator is drawn for a finger-side position [raw] while Search is not chosen: on the nearest of Home / Library (leaning to the finger), a rubber band past Home, a stretch into the edge past Library. */
    fun drawn(raw: Float): Float {
        val c = centers
        return when {
            raw < c[0] -> c[0] + rubberBand(raw - c[0], 0.35f * item)
            raw <= c[1] -> {
                val n = if (raw < (c[0] + c[1]) / 2) 0 else 1
                c[n] + (raw - c[n]) * MagnetPull
            }
            else -> c[1] + stretch(raw - c[1])
        }
    }

    private val reach get() = StretchReach * span
    /** Slope 1 at first, flattening towards [reach]. */
    private fun stretch(d: Float) = reach * kotlin.math.tanh(d / (reach * k))
    /** How fast the indicator follows the finger at finger distance [d] past Library. */
    fun slopeAt(d: Float) = (1f - kotlin.math.tanh(d / (reach * k)).let { it * it }) / k

    /** The finger-side position matching a resting or sliding indicator at [x], so a drag starts from where it is. */
    fun rawOf(x: Float): Float =
        if (x > centers[1]) centers[1] + reach * k * kotlin.math.atanh(((x - centers[1]) / reach).coerceAtMost(0.999f)) else x
}

/**
 * The floating chrome at the bottom: a blurred pill with Home and Library, and Search as its own round button beside it
 * (Apple Music's arrangement, built from Material parts).
 *
 * One indicator runs through all three slots, but only the pill draws it: going to Search slides it out of the pill's edge,
 * shrinking a little, and it is gone. Search lights up instead (a fill spreading from the icon). A touch lifts the indicator where
 * it is; a drag moves it by the finger's travel (not to the finger), with resistance on the way to Search; once that is far enough
 * to choose it there is a tick, the button jumps up in size and fills. On release the indicator is thrown with the finger's
 * velocity to the slot it lands nearest.
 */
@Composable
fun AppBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    blur: ChromeBlurState?,
    modifier: Modifier = Modifier,
    /** 0 = full bar, 1 = minimized to the selected tab's circle (the mini player moves in beside it). */
    minimize: () -> Float = { 0f },
    onExpand: () -> Unit = {},
    /** 0..1: on the Search tab the tab pill folds away entirely (the mini player takes its side); the Search button unfolds it. */
    fold: () -> Float = { 0f },
    onSearchAgain: () -> Unit = {},
    /** How far Now Playing is open (0..1). Minimized, the bar's two buttons slide out to the sides with it instead of the bar sliding down. */
    sideSlide: () -> Float = { 0f },
    /**
     * Wide layouts (see `WindowLayout.sideChrome`): the bar stands on a side of the screen, Search on top, Library in the middle, Home at
     * the bottom, with the same indicator running up and down. It does not minimize there (the caller passes no minimize / fold).
     */
    vertical: Boolean = false,
    /** The side bar's tab length (see [sideBarSlotLength]); only read when [vertical]. */
    slotLength: Dp = SideBarMaxSlotLength,
) {
    val selectedIndex = AllTabs.indexOf(current)
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberTickHaptics()
    val select by rememberUpdatedState(onSelect)
    val foldNow by rememberUpdatedState(fold)
    val minimizeNow by rememberUpdatedState(minimize)
    val searchAgain by rememberUpdatedState(onSearchAgain)
    val sideSlideNow by rememberUpdatedState(sideSlide)
    var barLeft by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val rootWidth = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toFloat()
    val expand by rememberUpdatedState(onExpand)
    val selected by rememberUpdatedState(selectedIndex)
    var dragging by remember { mutableStateOf(false) }
    // The slot the held indicator is nearest while dragging (-1 when not dragging).
    var dragNear by remember { mutableIntStateOf(-1) }
    val lift = remember { Animatable(1f) }
    var x by remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }
    val slide = Springs.toss<Float>(stiffness = 500f, visibilityThreshold = 0.5f)

    Box(modifier, contentAlignment = Alignment.Center) {
    BoxWithConstraints(
        // Measured after the padding and width limits: it is the bar's own left edge the buttons' travel is counted from.
        (if (vertical) Modifier.size(AppBarHeight, AppBarHeight + BarGap + slotLength * 2)
        else Modifier.padding(horizontal = FloatingChromeMargin).widthIn(max = FloatingChromeWidth).fillMaxWidth().height(AppBarHeight))
            .onGloballyPositioned { barLeft = it.positionInRoot().x },
    ) {
        val geo = remember(maxWidth, density, vertical, slotLength) {
            with(density) {
                BarGeometry(
                    pill = if (vertical) (slotLength * 2).toPx() else (maxWidth - BarGap - AppBarHeight).toPx(),
                    button = AppBarHeight.toPx(),
                    gap = BarGap.toPx(),
                    inset = IndicatorInset.toPx(),
                    minPull = MinPull.toPx(),
                )
            }
        }
        val pos = x ?: Animatable(geo.centers[selectedIndex]).also { x = it }

        // A new selection (tap, back, search opened elsewhere) slides the indicator there, out of or into the pill.
        LaunchedEffect(selectedIndex) { if (!dragging) pos.animateTo(geo.centers[selectedIndex], slide) }
        LaunchedEffect(geo) { if (!dragging) pos.snapTo(geo.centers[selected]) }

        // Search is lit while it is selected, or while the held indicator is over towards it; it jumps up the moment it is chosen.
        val searchLit = if (dragging) dragNear == SearchIndex else current == Tab.Search
        val fill = remember { Animatable(if (searchLit) 1f else 0f) }
        val pop = remember { Animatable(1f) }
        LaunchedEffect(searchLit) { fill.animateTo(if (searchLit) 1f else 0f, Springs.land(stiffness = 500f, visibilityThreshold = 0.001f)) }
        LaunchedEffect(dragging && searchLit) {
            pop.animateTo(if (dragging && searchLit) SearchPop else 1f, if (dragging) Springs.press(0.001f) else Springs.release(0.001f))
        }

        val weight = { i: Int -> (1f - kotlin.math.abs(geo.unitsAt(pos.value) - i)).coerceIn(0f, 1f) }
        val indicatorColor = MaterialTheme.colorScheme.secondaryContainer

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(geo, vertical) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // A minimized bar is only a button: a touch expands it, there is nothing to drag.
                        if (minimizeNow() > 0.02f || foldNow() > 0.02f) return@awaitEachGesture
                        // Holding the bar lifts the indicator where it is; only a drag moves it.
                        scope.launch { lift.animateTo(LiftScale, Springs.press(0.001f)) }
                        val slop =
                            if (vertical) awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                            else awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (slop == null) {
                            scope.launch { lift.animateTo(1f, Springs.release(0.001f)) }
                            return@awaitEachGesture
                        }
                        dragging = true
                        tracker.resetTracking()
                        tracker.addPosition(slop.uptimeMillis, slop.position)
                        // Starting from Search the indicator is out of sight: the finger sits at the point where it was chosen.
                        var chosen = selected == SearchIndex
                        var raw = if (chosen) geo.centers[1] + ChooseAt * geo.pull else geo.rawOf(pos.value)
                        var catchingUp = false
                        var lastNear = if (chosen) SearchIndex else geo.nearest(pos.value)
                        dragNear = lastNear
                        // Along the axis: to the right, or up (the vertical bar counts from its bottom).
                        val onDrag: (PointerInputChange) -> Unit = { change ->
                            raw += change.positionChange().let { if (vertical) -it.y else it.x }
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val d = (raw - geo.centers[1]) / geo.pull
                            if (!chosen && d >= ChooseAt) {
                                chosen = true
                                scope.launch { pos.animateTo(geo.centers[SearchIndex], slide) }
                            } else if (chosen && d <= LetGoAt) {
                                chosen = false
                                catchingUp = true
                            }
                            if (!chosen) {
                                val shown = geo.drawn(raw)
                                // Hopping between tabs, and coming back from Search, are quick springs; the stretch follows the finger exactly.
                                if (catchingUp && kotlin.math.abs(pos.value - shown) <= 2f) catchingUp = false
                                if (raw <= geo.centers[1] || catchingUp) {
                                    scope.launch { pos.animateTo(shown, Springs.press(0.5f)) }
                                } else {
                                    scope.launch { pos.snapTo(shown) }
                                }
                            }
                            val near = if (chosen) SearchIndex else geo.nearest(geo.drawn(raw)).coerceAtMost(1)
                            if (near != lastNear) { lastNear = near; dragNear = near; haptics.tick() }
                        }
                        val finished = if (vertical) verticalDrag(slop.id, onDrag) else horizontalDrag(slop.id, onDrag)
                        val v = tracker.calculateVelocity().let { if (vertical) -it.y else it.x }
                        val ahead = raw + v * 0.12f
                        val target = when {
                            !finished -> selected
                            ahead > geo.centers[1] -> if ((ahead - geo.centers[1]) / geo.pull >= (ChooseAt + LetGoAt) / 2) SearchIndex else 1
                            else -> geo.nearest(geo.drawn(ahead)).coerceAtMost(1)
                        }
                        dragging = false
                        dragNear = -1
                        if (target != selected) select(AllTabs[target])
                        scope.launch { lift.animateTo(1f, Springs.release(0.001f)) }
                        // The indicator moves slower than the finger while it is stretched.
                        val speed = if (raw > geo.centers[1]) v * geo.slopeAt(raw - geo.centers[1]) else v
                        scope.launch { pos.animateTo(geo.centers[target], slide, initialVelocity = speed) }
                    }
                },
        ) {
            if (vertical) {
                // Search on top, then the pill with Library above Home: the order of the horizontal bar, read from the bottom up.
                Column(Modifier.fillMaxSize()) {
                    SearchButton(
                        selected = current == Tab.Search,
                        fill = { fill.value },
                        pop = { pop.value },
                        fillColor = indicatorColor,
                        onClick = { if (current == Tab.Search) searchAgain() else select(Tab.Search) },
                        blur = blur,
                    )
                    Spacer(Modifier.height(BarGap))
                    TabPill(
                        blur = blur,
                        indicator = Modifier.indicator(pos, lift, geo, indicatorColor, vertical = true) { 1f },
                        selected = current,
                        weight = weight,
                        onSelect = { select(it) },
                        geo = geo,
                        collapse = 0f,
                        vertical = true,
                        modifier = Modifier.size(AppBarHeight, slotLength * 2),
                    )
                }
            } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val m = minimize()
                val f = fold()
                TabPill(
                    blur = blur,
                    indicator = Modifier.indicator(pos, lift, geo, indicatorColor) { (1f - 3f * minimizeNow()).coerceIn(0f, 1f) },
                    selected = current,
                    weight = weight,
                    onSelect = { if (minimizeNow() > 0.5f) expand() else select(it) },
                    geo = geo,
                    collapse = maxOf(m, f),
                    modifier = Modifier
                        .width(with(density) { androidx.compose.ui.util.lerp(androidx.compose.ui.util.lerp(geo.pill, geo.button, m), 0f, f).toDp() })
                        // The circle leaves to the left as Now Playing opens.
                        .graphicsLayer {
                            val d = minimizeNow()
                            translationX = -sideSlideNow() * d * (barLeft + androidx.compose.ui.util.lerp(geo.pill, geo.button, d))
                        }
                        // Anywhere on the minimized circle brings the bar back.
                        .then(if (m > 0.5f) Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { expand() } else Modifier),
                )
                Spacer(Modifier.weight(1f).widthIn(min = BarGap))
                SearchButton(
                    selected = current == Tab.Search,
                    fill = { fill.value },
                    pop = { pop.value },
                    fillColor = indicatorColor,
                    onClick = { if (current == Tab.Search) searchAgain() else select(Tab.Search) },
                    blur = blur,
                    // ... and the Search button to the right.
                    modifier = Modifier.graphicsLayer {
                        val d = maxOf(minimizeNow(), foldNow())
                        translationX = sideSlideNow() * d * (rootWidth - (barLeft + geo.pill + geo.gap))
                    },
                )
            }
        }
    }
    }
}

/** The indicator's pill, drawn at the shared centre [x] in the pill's coordinates; the pill's clip hides it once it has left. */
private fun Modifier.indicator(
    x: Animatable<Float, AnimationVector1D>,
    lift: Animatable<Float, AnimationVector1D>,
    geo: BarGeometry,
    color: Color,
    vertical: Boolean = false,
    alpha: () -> Float,
) = drawBehind {
    val scale = geo.scaleAt(x.value) * lift.value
    val along = (geo.item - 2 * geo.inset) * scale
    val across = ((if (vertical) size.width else size.height) - 2 * geo.inset) * scale
    val tint = color.copy(alpha = color.alpha * alpha())
    if (vertical) {
        // The axis runs up from the pill's bottom edge.
        drawRoundRect(tint, Offset((size.width - across) / 2, size.height - x.value - along / 2), Size(across, along), CornerRadius(across / 2))
    } else {
        drawRoundRect(tint, Offset(x.value - along / 2, (size.height - across) / 2), Size(along, across), CornerRadius(across / 2))
    }
}

@Composable
private fun TabPill(
    blur: ChromeBlurState?,
    indicator: Modifier,
    selected: Tab,
    weight: (Int) -> Float,
    onSelect: (Tab) -> Unit,
    geo: BarGeometry,
    collapse: Float,
    modifier: Modifier,
    vertical: Boolean = false,
    iconSize: Dp = 24.dp,
) {
    val density = LocalDensity.current
    ChromeSurface(blur, CircleShape, if (vertical) modifier else modifier.height(AppBarHeight)) {
        if (vertical) {
            Box(Modifier.fillMaxSize().then(indicator)) {
                Column(Modifier.fillMaxSize()) {
                    // Library above Home: the axis counts from the bottom.
                    AllTabs.take(SearchIndex).reversed().forEach { tab ->
                        TabItem(
                            tab = tab,
                            selectedness = { weight(AllTabs.indexOf(tab)) },
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                            labelAlpha = 1f,
                            iconSize = iconSize,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
            return@ChromeSurface
        }
        Box(Modifier.fillMaxSize().then(indicator), contentAlignment = Alignment.CenterStart) {
            // The row keeps its full width while the pill shrinks around it: the selected tab slides to the middle of what is left
            // (and drops into the space its label leaves), the others fade, and the pill's clip hides the rest.
            // (requiredWidth would centre the row in the shrinking pill; this keeps it anchored at the left.)
            Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(with(density) { geo.pill.toDp() }).fillMaxHeight()) {
                AllTabs.take(SearchIndex).forEachIndexed { i, tab ->
                    TabItem(
                        tab = tab,
                        selectedness = { weight(i) },
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        labelAlpha = 1f - collapse,
                        modifier = Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                            if (tab == selected) {
                                translationX = collapse * (geo.button / 2f - geo.centers[i])
                                translationY = collapse * 8.dp.toPx()
                            } else {
                                alpha = 1f - collapse
                                scaleX = 1f - 0.3f * collapse
                                scaleY = scaleX
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: Tab, selectedness: () -> Float, selected: Boolean, onClick: () -> Unit, labelAlpha: Float, modifier: Modifier, iconSize: Dp = 24.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val active = MaterialTheme.colorScheme.onSecondaryContainer
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .semantics { role = Role.Tab; this.selected = selected }
            .clickable(interaction, indication = null, onClick = onClick)
            .pressResponse(interaction, pressedScale = 0.88f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Colours follow the indicator as it passes, not the selection, so a drag recolours the item under the finger.
        val tint = androidx.compose.ui.graphics.lerp(idle, active, selectedness())
        AnimatedSymbolIcon(tab.symbol(), null, filled = selected, tint = tint, size = iconSize)
        Text(tab.label, style = if (iconSize > 28.dp) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.graphicsLayer { alpha = labelAlpha })
    }
}

@Composable
private fun SearchButton(
    selected: Boolean,
    fill: () -> Float,
    pop: () -> Float,
    fillColor: Color,
    onClick: () -> Unit,
    blur: ChromeBlurState?,
    modifier: Modifier = Modifier,
    diameter: Dp = AppBarHeight,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onSecondaryContainer,
        fill(),
    )
    ChromeSurface(
        blur,
        CircleShape,
        modifier
            .size(diameter)
            .graphicsLayer { pop().let { scaleX = it; scaleY = it } }
            .pressResponse(interaction, pressedScale = 0.86f)
            .semantics { role = Role.Tab; this.selected = selected },
    ) {
        Box(
            Modifier.fillMaxSize()
                // Lit: a disc grows from the middle of the icon while fading in, up to the whole button.
                .drawBehind {
                    val f = fill()
                    if (f > 0f) drawCircle(fillColor.copy(alpha = fillColor.alpha * f.coerceIn(0f, 1f)), radius = size.minDimension / 2 * f)
                }
                .clickable(interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedSymbolIcon(Symbols.Search, Tab.Search.label, filled = selected, tint = tint, size = 28.dp + (diameter - AppBarHeight).coerceAtLeast(0.dp) * 0.25f, weight = if (selected) 600 else 400)
        }
    }
}

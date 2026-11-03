package com.mrgnstr.harmony.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.common.rememberTickHaptics
import com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
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
/** Space between the bar and the navigation bar / screen edge. */
val AppBarEdgeGap = 12.dp
private val BarGap = 10.dp
private val IndicatorInset = 6.dp

private val BarTabs = listOf(Tab.Home, Tab.Library)

private fun Tab.symbol(): Symbol = when (this) {
    Tab.Home -> Symbols.Home
    Tab.Library -> Symbols.Library
    Tab.Search -> Symbols.Search
}

/**
 * The floating chrome at the bottom: a blurred pill with Home and Library, and Search as its own round button beside it
 * (Apple Music's arrangement, built from Material parts).
 */
@Composable
fun AppBar(current: Tab, onSelect: (Tab) -> Unit, blur: ChromeBlurState?, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TabPill(current, onSelect, blur, Modifier.weight(1f))
        Spacer(Modifier.width(BarGap))
        SearchButton(selected = current == Tab.Search, onClick = { onSelect(Tab.Search) }, blur = blur)
    }
}

/**
 * The selected tab sits on a pill-shaped indicator. Tapping a tab moves it there on a spring; dragging across the bar
 * picks the indicator up (it grows a little), it follows the finger (rubber-banding past the ends), and on release it is
 * thrown with the finger's velocity to the tab it lands nearest.
 */
@Composable
private fun TabPill(current: Tab, onSelect: (Tab) -> Unit, blur: ChromeBlurState?, modifier: Modifier) {
    val selectedIndex = BarTabs.indexOf(current)
    val position = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    val shown = remember { Animatable(if (selectedIndex >= 0) 1f else 0f) }
    val lift = remember { Animatable(1f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberTickHaptics()
    val select by rememberUpdatedState(onSelect)

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            launch { shown.animateTo(1f, Springs.land(visibilityThreshold = 0.001f)) }
            if (!dragging) position.animateTo(selectedIndex.toFloat(), Springs.toss(stiffness = 500f, visibilityThreshold = 0.001f))
        } else {
            // Search is open: the indicator shrinks away where it is.
            shown.animateTo(0f, Springs.land(visibilityThreshold = 0.001f))
        }
    }

    ChromeSurface(blur, CircleShape, modifier.height(AppBarHeight)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / BarTabs.size
            val itemPx = with(LocalDensity.current) { itemWidth.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(itemPx) {
                        val tracker = VelocityTracker()
                        var raw = 0f
                        var lastIndex = -1
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                tracker.resetTracking()
                                raw = offset.x / itemPx - 0.5f
                                lastIndex = raw.roundToInt()
                                scope.launch { shown.animateTo(1f, Springs.land(visibilityThreshold = 0.001f)) }
                                scope.launch { lift.animateTo(1.08f, Springs.press(0.001f)) }
                                scope.launch { position.animateTo(raw.coerceIn(0f, BarTabs.lastIndex.toFloat()), Springs.press(0.001f)) }
                            },
                            onHorizontalDrag = { change, delta ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                raw += delta / itemPx
                                val max = BarTabs.lastIndex.toFloat()
                                val shownAt = when {
                                    raw < 0f -> rubberBand(raw, 0.35f)
                                    raw > max -> max + rubberBand(raw - max, 0.35f)
                                    else -> raw
                                }
                                scope.launch { position.snapTo(shownAt) }
                                val near = raw.roundToInt().coerceIn(0, BarTabs.lastIndex)
                                if (near != lastIndex) { lastIndex = near; haptics.tick() }
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().x / itemPx
                                // Where a free throw would come to rest, then the nearest tab to that.
                                val target = (position.value + v * 0.12f).roundToInt().coerceIn(0, BarTabs.lastIndex)
                                dragging = false
                                select(BarTabs[target])
                                scope.launch { lift.animateTo(1f, Springs.release(0.001f)) }
                                scope.launch { position.animateTo(target.toFloat(), Springs.toss(stiffness = 500f, visibilityThreshold = 0.001f), initialVelocity = v) }
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { lift.animateTo(1f, Springs.release(0.001f)) }
                                val back = BarTabs.indexOf(current)
                                if (back >= 0) scope.launch { position.animateTo(back.toFloat(), Springs.toss(stiffness = 500f, visibilityThreshold = 0.001f)) }
                            },
                        )
                    },
            ) {
                Indicator(position, shown, lift, itemWidth)
                Row(Modifier.fillMaxSize()) {
                    BarTabs.forEachIndexed { i, tab ->
                        TabItem(
                            tab = tab,
                            selectedness = { (1f - kotlin.math.abs(position.value - i)).coerceIn(0f, 1f) * shown.value },
                            selected = tab == current,
                            onClick = { select(tab) },
                            modifier = Modifier.width(itemWidth).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Indicator(
    position: Animatable<Float, AnimationVector1D>,
    shown: Animatable<Float, AnimationVector1D>,
    lift: Animatable<Float, AnimationVector1D>,
    itemWidth: androidx.compose.ui.unit.Dp,
) {
    val color = MaterialTheme.colorScheme.secondaryContainer
    Box(
        Modifier
            .width(itemWidth)
            .fillMaxHeight()
            .padding(IndicatorInset)
            .graphicsLayer {
                translationX = position.value * itemWidth.toPx()
                val s = shown.value * lift.value
                scaleX = s
                scaleY = s
                alpha = shown.value
            }
            .background(color, CircleShape),
    )
}

@Composable
private fun TabItem(tab: Tab, selectedness: () -> Float, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
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
        AnimatedSymbolIcon(tab.symbol(), null, filled = selected, tint = tint)
        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun SearchButton(selected: Boolean, onClick: () -> Unit, blur: ChromeBlurState?) {
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "search-container",
    )
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "search-tint",
    )
    ChromeSurface(
        blur,
        CircleShape,
        Modifier
            .size(AppBarHeight)
            .pressResponse(interaction, pressedScale = 0.86f)
            .semantics { role = Role.Tab; this.selected = selected },
        container = container,
    ) {
        Box(Modifier.fillMaxSize().clickable(interaction, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
            AnimatedSymbolIcon(Symbols.Search, Tab.Search.label, filled = selected, tint = tint, size = 28.dp, weight = if (selected) 600 else 400)
        }
    }
}

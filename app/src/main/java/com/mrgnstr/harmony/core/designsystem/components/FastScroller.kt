package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.common.rememberTickHaptics
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val ThumbHeight = 56.dp
private val ThumbTouchWidth = 28.dp
private val BubbleSize = 64.dp
private const val HideAfterMs = 1200L

/**
 * A scroll bar for a long list: it shows only while the list scrolls (and while held), then fades. Only the thumb takes touches, so
 * nothing in the list is covered. Dragging the thumb jumps through the list; [labelOf] names the item it is over (a bubble beside the
 * thumb, a tick each time the name changes). With no label (a list not ordered by letter) the ticks come every twentieth of the list.
 *
 * Put it over the list in a [Box]; [topInset]/[bottomInset] keep the thumb's track clear of the bars. [fraction] is the list's
 * position, 0..1, read in the draw phase.
 */
@Composable
fun FastScroller(
    fraction: () -> Float,
    scrolling: Boolean,
    itemCount: Int,
    labelOf: (Int) -> String?,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    val haptics = rememberTickHaptics()
    val density = LocalDensity.current
    val thumbPx = with(density) { ThumbHeight.toPx() }
    val bubblePx = with(density) { BubbleSize.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    var lastIndex by remember { mutableIntStateOf(-1) }
    var lastKey by remember { mutableStateOf<String?>(null) }
    var bubble by remember { mutableStateOf<String?>(null) }
    val count by rememberUpdatedState(itemCount)
    val label by rememberUpdatedState(labelOf)
    val jump by rememberUpdatedState(onJump)

    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) visible = true else { delay(HideAfterMs); visible = false }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 120 else 300), label = "scrollbarAlpha")
    val bubbleAlpha by animateFloatAsState(if (dragging && bubble != null) 1f else 0f, tween(120), label = "bubbleAlpha")
    val width by animateDpAsState(if (dragging) 10.dp else 4.dp, tween(150), label = "thumbWidth")

    BoxWithConstraints(modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset)) {
        val trackPx = constraints.maxHeight.toFloat()
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)
        val y: () -> Float = { (if (dragging) dragFraction else fraction().coerceIn(0f, 1f)) * travel }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(ThumbTouchWidth)
                .size(ThumbTouchWidth, ThumbHeight)
                .graphicsLayer { translationY = y(); this.alpha = alpha }
                // Only while visible: a hidden thumb must not grab touches meant for the list.
                .then(
                    if (!visible) Modifier else Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragFraction = fraction().coerceIn(0f, 1f)
                                dragging = true
                                lastIndex = -1
                                lastKey = null
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, dy ->
                            change.consume()
                            dragFraction = (dragFraction + dy / travel).coerceIn(0f, 1f)
                            val index = (dragFraction * (count - 1).coerceAtLeast(0)).roundToInt()
                            if (index != lastIndex) {
                                lastIndex = index
                                jump(index)
                                val name = label(index)
                                bubble = name
                                val key = name ?: "part:${index * 20 / count.coerceAtLeast(1)}"
                                if (key != lastKey) { if (lastKey != null) haptics.tick(); lastKey = key }
                            }
                        }
                    },
                ),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
                    .width(width)
                    .fillMaxHeight()
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(50),
                    ),
            )
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = ThumbTouchWidth + 8.dp)
                .size(BubbleSize)
                .graphicsLayer {
                    translationY = y() + (thumbPx - bubblePx) / 2f
                    this.alpha = bubbleAlpha
                    val s = 0.8f + 0.2f * bubbleAlpha
                    scaleX = s; scaleY = s
                }
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(bubble.orEmpty(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * [FastScroller] for a [LazyListState]: the thumb follows the first visible item, and dragging scrolls it to the item under the
 * finger. [labelOf] gets list item indexes (a header item counts).
 */
@Composable
fun LazyListFastScroller(
    state: LazyListState,
    itemCount: Int,
    labelOf: (Int) -> String?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    FastScroller(
        fraction = {
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            val range = (itemCount - info.visibleItemsInfo.size).coerceAtLeast(1)
            if (first == null) 0f
            else ((state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / first.size.toFloat().coerceAtLeast(1f)) / range).coerceIn(0f, 1f)
        },
        scrolling = state.isScrollInProgress,
        itemCount = itemCount,
        labelOf = labelOf,
        onJump = { state.requestScrollToItem(it) },
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
    )
}

/** [FastScroller] for a [LazyGridState]: the position counts items, so a row of tiles moves it by one row's share. */
@Composable
fun LazyGridFastScroller(
    state: LazyGridState,
    itemCount: Int,
    labelOf: (Int) -> String?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    FastScroller(
        fraction = {
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            val range = (itemCount - info.visibleItemsInfo.size).coerceAtLeast(1)
            if (first == null) 0f
            else ((state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / first.size.height.toFloat().coerceAtLeast(1f)) / range).coerceIn(0f, 1f)
        },
        scrolling = state.isScrollInProgress,
        itemCount = itemCount,
        labelOf = labelOf,
        onJump = { state.requestScrollToItem(it) },
        modifier = modifier,
        topInset = topInset,
        bottomInset = bottomInset,
    )
}

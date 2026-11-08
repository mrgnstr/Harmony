@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import kotlin.math.abs
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.designsystem.components.ItemFadeSpec
import com.mrgnstr.harmony.core.device.VisualTier
import com.mrgnstr.harmony.core.designsystem.icons.PlayingBars
import com.mrgnstr.harmony.core.designsystem.icons.Symbol
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.playback.QueueItem
import com.mrgnstr.harmony.core.playback.QueueSourceKind
import com.mrgnstr.harmony.core.playback.QueueState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RowHeight = 64.dp
private val RowGap = 6.dp
private val RowShape = RoundedCornerShape(16.dp)
private val HeaderHeight = 48.dp

/** Rows glide to their new place a little faster than the library lists do, so a row that is being passed does not leave a hole. */
private val QueuePlaceSpec = spring<IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium, visibilityThreshold = IntOffset.VisibilityThreshold)

@Composable
private fun LazyItemScope.queueMotion(): Modifier =
    if (LocalVisualTier.current == VisualTier.LOW) Modifier
    else Modifier.animateItem(fadeInSpec = ItemFadeSpec, placementSpec = QueuePlaceSpec, fadeOutSpec = ItemFadeSpec)

/** One song in the sheet. [key] is unique even for the same song queued twice; [index] is its place in the whole queue when the sheet last synced. */
private class QueueEntry(val key: String, val item: QueueItem, val index: Int)

private fun entriesOf(queue: QueueState): List<QueueEntry> {
    val seen = HashMap<String, Int>()
    return queue.items.mapIndexed { i, item ->
        val n = seen.merge(item.mediaId, 1, Int::plus)!!
        QueueEntry("${item.mediaId}#$n", item, i)
    }
}

private fun QueueSourceKind.icon(): Symbol = when (this) {
    QueueSourceKind.Album -> Symbols.Album
    QueueSourceKind.Artist -> Symbols.Artist
    QueueSourceKind.Favourites -> Symbols.Favorite
    QueueSourceKind.Radio -> Symbols.Radio
    QueueSourceKind.Mix -> Symbols.AutoAwesome
    QueueSourceKind.Playlist, QueueSourceKind.Other -> Symbols.QueueMusic
}

/** Which row is being dragged and how far the finger is from the row's slot (the dragged row follows it, the list only places the others). */
private class DragState {
    var key by mutableStateOf<String?>(null)
    var delta by mutableFloatStateOf(0f)
    var rows: SnapshotStateList<QueueEntry>? = null
    var startPosition = 0
    var staleOffset = Int.MIN_VALUE
}

/**
 * The play order as it will be heard: the playing song on top, the rest of what it was started from ("Playing from ..."), then the
 * Play Next / Play Last songs ("Queue"). Section titles stick to the top and the next one pushes them away. A row's handle drags it
 * within its own section, swiping a row away removes it. Edits show at once in the local row lists and are sent to the player
 * when they land; the lists are rebuilt whenever the player's own queue changes.
 */
@Composable
fun QueueModalSheet(
    queue: QueueState,
    onSkipToIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor) {
        val all = remember(queue.items) { entriesOf(queue) }
        val current = all.getOrNull(queue.currentIndex)
        // The source section is everything up to the end of what the queue was started from (the songs already played, the playing
        // one, the rest); the Queue section is the Play Next / Play Last songs after it.
        val split = (if (current?.item?.queued == true) queue.currentIndex else queue.currentIndex + 1 + queue.sourceCount).coerceIn(0, all.size)
        val sourceRows = remember(all, split) { all.take(split).toMutableStateList() }
        val queuedRows = remember(all, split) { all.drop(split).toMutableStateList() }
        // Where each section starts in the whole queue, right now (local edits change it before the player reports back).
        fun baseOf(rows: SnapshotStateList<QueueEntry>) = if (rows === queuedRows) sourceRows.size else 0

        val listState = rememberLazyListState()
        val currentKey = current?.key
        val headerPx = with(LocalDensity.current) { HeaderHeight.roundToPx() }
        // The playing song sits at the top, just under the section title; the songs played before it are above.
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(currentKey) {
            val key = currentKey ?: return@LaunchedEffect
            val inSource = sourceRows.indexOfFirst { it.key == key }
            val at = if (inSource >= 0) 1 + inSource else (if (sourceRows.isEmpty()) 1 else sourceRows.size + 2) + queuedRows.indexOfFirst { it.key == key }
            if (!placed) { listState.scrollToItem(at, -headerPx); placed = true }
            else listState.animateScrollToItem(at, -headerPx)
        }
        val drag = remember { DragState() }
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        val edge = with(LocalDensity.current) { 56.dp.toPx() }

        // Swaps the dragged row with the one under its centre. Rows are all one height, so after a swap the row's slot moves by
        // exactly the distance to the row it passed; the finger offset is corrected by that much so the row stays under the finger.
        fun settleSwap() {
            val rows = drag.rows ?: return
            val key = drag.key ?: return
            val info = listState.layoutInfo.visibleItemsInfo
            val me = info.firstOrNull { it.key == key } ?: return
            if (me.offset == drag.staleOffset) return // not laid out since the last swap
            val centre = me.offset + me.size / 2 + drag.delta
            val target = info.firstOrNull { it.key != key && rows.any { r -> r.key == it.key } && centre >= it.offset && centre < it.offset + it.size } ?: return
            val from = rows.indexOfFirst { it.key == key }
            val to = rows.indexOfFirst { it.key == target.key }
            if (from < 0 || to < 0) return
            rows.add(to, rows.removeAt(from))
            drag.delta -= (target.offset - me.offset)
            drag.staleOffset = me.offset
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }

        // Near the top or bottom edge the list scrolls under the held row.
        LaunchedEffect(drag.key) {
            if (drag.key == null) return@LaunchedEffect
            while (true) {
                val key = drag.key ?: break
                val me = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                if (me != null) {
                    val centre = me.offset + me.size / 2 + drag.delta
                    val top = listState.layoutInfo.viewportStartOffset + edge
                    val bottom = listState.layoutInfo.viewportEndOffset - edge
                    val speed = when {
                        centre < top -> -(top - centre) / edge
                        centre > bottom -> (centre - bottom) / edge
                        else -> 0f
                    }.coerceIn(-1f, 1f) * 18f
                    if (speed != 0f) {
                        drag.delta += listState.scrollBy(speed)
                        settleSwap()
                    }
                }
                delay(16)
            }
        }

        val dragging = drag.key != null
        QueueList(
            listState = listState,
            currentKey = currentKey,
            sourceTitle = queue.source?.let { "Playing from $it" } ?: "Up Next",
            sourceIcon = if (queue.source != null) queue.sourceKind.icon() else Symbols.QueueMusic,
            sourceRows = sourceRows,
            queuedRows = queuedRows,
            sheetColor = sheetColor,
            drag = drag,
            dragging = dragging,
            onSkip = { rows, entry -> onSkipToIndex(baseOf(rows) + rows.indexOf(entry)) },
            onRemove = { rows, entry ->
                val at = baseOf(rows) + rows.indexOf(entry)
                rows.remove(entry)
                onRemoveIndex(at)
            },
            onDragStart = { rows, entry ->
                drag.rows = rows
                drag.startPosition = rows.indexOf(entry)
                drag.delta = 0f
                drag.staleOffset = Int.MIN_VALUE
                drag.key = entry.key
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { dy ->
                drag.delta += dy
                settleSwap()
            },
            onDragEnd = {
                val rows = drag.rows
                val key = drag.key
                if (rows != null && key != null) {
                    val from = baseOf(rows) + drag.startPosition
                    val to = baseOf(rows) + rows.indexOfFirst { it.key == key }
                    if (from != to) onMove(from, to)
                }
                // Settle into the slot with a spring; the row keeps following the sheet's offset until it is home.
                scope.launch {
                    animate(drag.delta, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { v, _ -> drag.delta = v }
                    drag.key = null
                    drag.rows = null
                }
            },
        )
    }
}

@Composable
private fun QueueList(
    listState: LazyListState,
    currentKey: String?,
    sourceTitle: String,
    sourceIcon: Symbol,
    sourceRows: SnapshotStateList<QueueEntry>,
    queuedRows: SnapshotStateList<QueueEntry>,
    sheetColor: androidx.compose.ui.graphics.Color,
    drag: DragState,
    dragging: Boolean,
    onSkip: (SnapshotStateList<QueueEntry>, QueueEntry) -> Unit,
    onRemove: (SnapshotStateList<QueueEntry>, QueueEntry) -> Unit,
    onDragStart: (SnapshotStateList<QueueEntry>, QueueEntry) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        section("source", sourceTitle, sourceIcon, sourceRows, currentKey, sheetColor, drag, dragging, onSkip, onRemove, onDragStart, onDrag, onDragEnd)
        section("queue", "Queue", Symbols.QueueMusic, queuedRows, currentKey, sheetColor, drag, dragging, onSkip, onRemove, onDragStart, onDrag, onDragEnd)
        item(key = "end") { Box(Modifier.navigationBarsPadding()) }
    }
}

private fun LazyListScope.section(
    id: String,
    title: String,
    icon: Symbol,
    rows: SnapshotStateList<QueueEntry>,
    currentKey: String?,
    sheetColor: androidx.compose.ui.graphics.Color,
    drag: DragState,
    dragging: Boolean,
    onSkip: (SnapshotStateList<QueueEntry>, QueueEntry) -> Unit,
    onRemove: (SnapshotStateList<QueueEntry>, QueueEntry) -> Unit,
    onDragStart: (SnapshotStateList<QueueEntry>, QueueEntry) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    if (rows.isEmpty()) return
    stickyHeader(key = "header-$id") { SectionTitle(title, icon, sheetColor) }
    items(rows, key = { it.key }) { entry ->
        if (entry.key == currentKey) {
            PlayingRow(entry.item, queueMotion())
            return@items
        }
        val isDragged = drag.key == entry.key
        QueueRow(
            entry.item,
            isDragged = isDragged,
            swipeEnabled = !dragging,
            drag = drag,
            onClick = { onSkip(rows, entry) },
            onRemove = { onRemove(rows, entry) },
            onDragStart = { onDragStart(rows, entry) },
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
    }
}

@Composable
private fun SectionTitle(title: String, icon: Symbol, background: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(HeaderHeight).background(background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(icon, null, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
        Box(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Cover(item: QueueItem, modifier: Modifier = Modifier) {
    ArtworkImage(item.songId, item.albumId, modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
}

@Composable
private fun Texts(item: QueueItem, modifier: Modifier, titleColor: androidx.compose.ui.graphics.Color, subColor: androidx.compose.ui.graphics.Color) {
    Column(modifier) {
        Text(item.title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.artist, style = MaterialTheme.typography.bodySmall, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The song that is playing: no cover, the equaliser mark in its place. Not draggable or removable. */
@Composable
private fun PlayingRow(item: QueueItem, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(RowHeight).clip(RowShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            PlayingBars(tint = MaterialTheme.colorScheme.primary)
        }
        Box(Modifier.width(12.dp))
        Texts(item, Modifier.weight(1f), MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun LazyItemScope.QueueRow(
    item: QueueItem,
    isDragged: Boolean,
    swipeEnabled: Boolean,
    drag: DragState,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }
    // Half the row's width in either direction turns it fully red; let go past 40% (or with a quick flick) and it goes.
    val p = (abs(x) / (width * 0.5f)).coerceIn(0f, 1f)
    // A dragged row follows the finger itself; every other row glides into place with the list's springs.
    val lift by animateFloatAsState(if (isDragged) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium), label = "lift")
    val motion = if (isDragged) Modifier.zIndex(1f).graphicsLayer { translationY = drag.delta } else queueMotion()
    Box(
        motion
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = swipeEnabled,
                state = rememberDraggableState { dx -> x += dx },
                onDragStopped = { velocity ->
                    val gone = abs(x) > width * 0.4f || abs(velocity) > 2500f && velocity * x >= 0f
                    // Gone: out of the list at once, so the rows below close the gap while this one fades where it was let go.
                    if (gone) onRemove()
                    else scope.launch { animate(x, 0f, velocity, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) { v, _ -> x = v } }
                },
            ),
    ) {
        // The bin sits where the row is leaving from, and fades in with the red.
        Box(
            Modifier.matchParentSize().padding(horizontal = 22.dp).graphicsLayer { alpha = p },
            contentAlignment = if (x < 0f) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            SymbolIcon(Symbols.Delete, "Remove from queue", tint = colors.error)
        }
        val container = lerp(colors.surfaceContainerHigh, colors.error, p)
        val onContainer = lerp(colors.onSurface, colors.onError, p)
        val subtle = lerp(colors.onSurfaceVariant, colors.onError, p)
        CompositionLocalProvider(LocalContentColor provides onContainer) {
            Row(
                Modifier
                    .graphicsLayer {
                        translationX = x
                        // The row being moved lifts off the list: a touch larger, with a shadow.
                        scaleX = 1f + 0.03f * lift
                        scaleY = scaleX
                        shadowElevation = 14.dp.toPx() * lift
                        shape = RowShape
                        clip = false
                    }
                    .fillMaxWidth().height(RowHeight).clip(RowShape).background(container)
                    .clickable(onClick = onClick).padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(item)
                Box(Modifier.width(12.dp))
                Texts(item, Modifier.weight(1f), onContainer, subtle)
                Box(
                    Modifier
                        .size(width = 56.dp, height = RowHeight)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                                onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    SymbolIcon(Symbols.DragHandle, "Hold to move", tint = subtle)
                }
            }
        }
    }
}

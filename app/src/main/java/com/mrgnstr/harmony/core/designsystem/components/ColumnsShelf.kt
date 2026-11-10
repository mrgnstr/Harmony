package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.bleedSides

/** Rows in one column of a [ColumnsShelf]. */
const val ColumnShelfRows = 5

/** The narrowest a column of a [ColumnsShelf] gets before another one is put beside it. */
val ColumnShelfMinWidth = 320.dp

private val ShelfSide = 16.dp
private val ColumnGap = 12.dp

/** How many columns of at least [minWidth] fit in [usable] (with [gap] between them): at least one. */
internal fun columnCount(usable: Float, minWidth: Float, gap: Float): Int = maxOf(1, ((usable + gap) / (minWidth + gap)).toInt())

/** The width of each of [count] columns that together fill [usable] (with [gap] between them). */
internal fun columnWidth(usable: Float, count: Int, gap: Float): Float = (usable - gap * (count - 1)) / count

/**
 * A list scrolled sideways: [items] are dealt into columns of [rowsPerColumn] rows (down, then the next column). As many columns as fit
 * at their [minColumnWidth] are shown side by side and stretched to fill the width, so a phone in portrait shows one and a tablet
 * several; the rest scroll in, a column at a time. Meant for a lazy page's shelf item, like the album shelves (same side padding and
 * bleed to the display edges). [row] gets the item and its index in [items].
 */
@Composable
fun <T> ColumnsShelf(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    rowsPerColumn: Int = ColumnShelfRows,
    minColumnWidth: Dp = ColumnShelfMinWidth,
    row: @Composable (item: T, index: Int) -> Unit,
) {
    if (items.isEmpty()) return
    val sides = LocalSideInsets.current
    val columns = remember(items, rowsPerColumn) { items.chunked(rowsPerColumn) }
    BoxWithConstraints(modifier.bleedSides(sides)) {
        val start = sides.left + ShelfSide
        val end = sides.right + ShelfSide
        val usable = maxWidth - start - end
        val count = columnCount(usable.value, minColumnWidth.value, ColumnGap.value)
        val width = columnWidth(usable.value, count, ColumnGap.value).dp
        val state = rememberLazyListState()
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            contentPadding = PaddingValues.Absolute(left = start, right = end),
            horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        ) {
            itemsIndexed(columns, key = { _, column -> key(column.first()) }) { columnIndex, column ->
                Column(Modifier.width(width)) {
                    column.forEachIndexed { i, item -> row(item, columnIndex * rowsPerColumn + i) }
                }
            }
        }
    }
}

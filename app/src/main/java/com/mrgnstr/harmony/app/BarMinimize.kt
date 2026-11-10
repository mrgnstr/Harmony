package com.mrgnstr.harmony.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Whether the tab bar is minimized, from how the pages under it scroll (iOS 26's behaviour). Scrolling down by [collapseAt] px
 * collapses it; scrolling back up by [expandAt] px, or pulling down at the top of a page, brings it back. It listens to what the
 * pages actually scrolled (`consumed`), so a page too short to scroll never collapses it, and horizontal shelves are ignored.
 */
@Stable
class BarMinimizeState(private val collapseAt: Float, private val expandAt: Float) {
    var minimized by mutableStateOf(false)
        private set
    private var travel = 0f

    /** Off while no song is loaded: the bar never minimizes without a mini player to move in beside it. */
    var enabled by mutableStateOf(true)

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled) return Offset.Zero
            val dy = consumed.y
            if (dy != 0f) {
                // A change of direction starts the count again.
                if (dy * travel < 0f) travel = 0f
                travel += dy
                if (travel < -collapseAt) minimized = true else if (travel > expandAt) minimized = false
            }
            // Dragged down with nothing left to scroll: the top of the page.
            if (available.y > 0f) expand()
            return Offset.Zero
        }
    }

    fun collapse() {
        if (!enabled) return
        minimized = true
        travel = 0f
    }

    fun expand() {
        minimized = false
        travel = 0f
    }
}

@Composable
fun rememberBarMinimize(): BarMinimizeState {
    val density = LocalDensity.current
    return remember { BarMinimizeState(with(density) { 40.dp.toPx() }, with(density) { 16.dp.toPx() }) }
}

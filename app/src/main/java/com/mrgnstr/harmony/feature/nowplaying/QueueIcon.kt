package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.icons.SymbolPartsIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tapping the queue icon: the three lines slide left one after another from the top, as if the list is moving up, and the note
 * bobs down in step; each springs back (call [play] from the click).
 */
@Stable
internal class QueueKick(private val scope: CoroutineScope) {
    /** Top line, middle line, short line, note: 0 = at rest, 1 = furthest out. */
    val parts = List(4) { Animatable(0f) }

    fun play() {
        parts.forEachIndexed { i, a ->
            scope.launch {
                delay(i * 45L)
                a.animateTo(1f, tween(70))
                a.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 420f, visibilityThreshold = 0.001f))
            }
        }
    }
}

@Composable
internal fun rememberQueueKick(): QueueKick {
    val scope = rememberCoroutineScope()
    return remember(scope) { QueueKick(scope) }
}

// The glyph's contours (Material "queue_music"; the same at every fill), as the parts above are numbered: top line (3), middle line (2), short line (1), note (0).
private val Parts = listOf(intArrayOf(3), intArrayOf(2), intArrayOf(1), intArrayOf(0))

@Composable
internal fun QueueIcon(blend: BlendMode, tint: Color, kick: QueueKick, modifier: Modifier = Modifier) {
    SymbolPartsIcon(Symbols.QueueMusic, "Queue", Parts, modifier) { part, path ->
        val t = kick.parts[part].value
        // Lines go left, the note down.
        if (part < 3) translate(left = -2.dp.toPx() * t) { drawPath(path, tint, blendMode = blend) }
        else translate(top = 1.5.dp.toPx() * t) { drawPath(path, tint, blendMode = blend) }
    }
}

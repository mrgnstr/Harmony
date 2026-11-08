package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.icons.SymbolPartsIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tapping the lyrics icon: the bubble squashes and the note bounces out of its corner; both spring back (call [play] from the click). */
@Stable
internal class LyricsKick(private val scope: CoroutineScope) {
    /** 0 = at rest, 1 = the bubble squashed and the note furthest out; the spring overshoots past 0 on the way back (a stretch). */
    val pop = Animatable(0f)
    fun play() {
        scope.launch {
            pop.animateTo(1f, tween(80))
            pop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 420f, visibilityThreshold = 0.001f))
        }
    }
}

@Composable
internal fun rememberLyricsKick(): LyricsKick {
    val scope = rememberCoroutineScope()
    return remember(scope) { LyricsKick(scope) }
}

// The glyph's contours (Material "lyrics"): the note, the bubble and its inner shape (which grows into the fill), the three text
// lines from the top. In font units, 960 per em, y up.
private val Note = intArrayOf(1)
private val Bubble = intArrayOf(4, 5)
private val Line1 = intArrayOf(3)
private val Line2 = intArrayOf(2)
private val Line3 = intArrayOf(0)
private val Parts = listOf(Bubble, Line1, Line2, Line3, Note)

/**
 * The lyrics button's icon: a speech bubble with text lines and a note. [filled] fills the bubble in (the lines turn into holes in
 * it: they are drawn as ink while it is empty and subtracted from it when it is full, crossing over as it fills), and a tap
 * ([kick]) pops it. The parts are drawn into one layer, which is then laid over the button, so with [BlendMode.Clear] (the on
 * state) the whole icon is cut out of the container.
 */
@Composable
internal fun LyricsIcon(blend: BlendMode, tint: Color, filled: Boolean, kick: LyricsKick, modifier: Modifier = Modifier) {
    val fill = animateFloatAsState(if (filled) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "lyrics-fill")
    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    SymbolPartsIcon(
        Symbols.Lyrics, "Lyrics", Parts,
        modifier = modifier.drawWithContent {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply { blendMode = if (blend == BlendMode.Clear) BlendMode.DstOut else BlendMode.SrcOver }
                // Room around the icon, so a part that moves out of its box is not cut off.
                canvas.saveLayer(Rect(Offset(-margin, -margin), androidx.compose.ui.geometry.Size(size.width + 2 * margin, size.height + 2 * margin)), paint)
                drawContent()
                canvas.restore()
            }
        },
        fill = { fill.value },
    ) { part, path ->
        val p = kick.pop.value
        val px = size.minDimension
        when (part) {
            4 -> { // the note: out of its corner, tilted
                translate(2.dp.toPx() * p, -2.5.dp.toPx() * p) {
                    rotate(12f * p, Offset(0.833f * px, 0.27f * px)) { drawPath(path, tint) }
                }
            }
            else -> {
                // Bubble and lines squash and stretch together, about the bubble's centre.
                scale(1f + 0.10f * p, 1f - 0.10f * p, Offset(0.396f * px, 0.47f * px)) {
                    if (part == 0) drawPath(path, tint)
                    else {
                        val f = fill.value
                        if (f < 1f) drawPath(path, tint, alpha = 1f - f)
                        if (f > 0f) drawPath(path, Color.Black, alpha = f, blendMode = BlendMode.DstOut)
                    }
                }
            }
        }
    }
}

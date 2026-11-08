package com.mrgnstr.harmony.core.designsystem.icons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Each bar wanders on its own: it eases to a new random height, then picks another. The range and the size of one move are limited and
// there is no bounce, so it reads as lively rather than jittery.
private val Start = floatArrayOf(0.42f, 0.66f, 0.9f, 0.66f, 0.42f)
private const val Bars = 5
private const val MinHeight = 0.24f
private const val MaxHeight = 0.92f
private const val MinMove = 0.18f
private const val MaxMove = 0.45f
private const val MinMs = 480
private const val MaxMs = 860

/**
 * The mark for the song that is playing (album track list, queue): five bars drawn like the equaliser glyph it replaces, each moving
 * to random heights at its own pace. The heights are read while drawing, so the animation never recomposes anything.
 */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
    val heights = remember { List(Bars) { Animatable(Start[it]) } }
    LaunchedEffect(Unit) {
        heights.forEachIndexed { i, bar ->
            launch {
                delay(i * 110L)
                while (true) {
                    var target: Float? = null
                    var tries = 0
                    while (target == null && tries++ < 6) {
                        val t = Random.nextFloat() * (MaxHeight - MinHeight) + MinHeight
                        if (abs(t - bar.value) in MinMove..MaxMove) target = t
                    }
                    val to = target ?: if (bar.value > 0.58f) bar.value - MinMove else bar.value + MinMove
                    bar.animateTo(to.coerceIn(MinHeight, MaxHeight), tween(Random.nextInt(MinMs, MaxMs), easing = FastOutSlowInEasing))
                }
            }
        }
    }
    Canvas(modifier.size(size).semantics { contentDescription = "Playing" }) {
        val margin = this.size.width * 0.14f
        val thickness = this.size.width * 0.1f
        val step = (this.size.width - 2 * margin) / (Bars - 1)
        val middle = this.size.height / 2f
        heights.forEachIndexed { i, h ->
            val x = margin + step * i
            val half = this.size.height * h.value / 2f - thickness / 2f
            drawLine(tint, Offset(x, middle - half), Offset(x, middle + half), thickness, StrokeCap.Round)
        }
    }
}

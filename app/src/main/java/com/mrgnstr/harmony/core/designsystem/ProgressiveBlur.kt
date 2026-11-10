package com.mrgnstr.harmony.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

private fun smoothstep(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** An alpha mask from [from] to [to] (fractions of the height) along a smoothstep curve; [up] = transparent to opaque. */
fun smoothRamp(from: Float, to: Float, up: Boolean, horizontal: Boolean = false): Brush {
    val steps = 10
    val stops = ArrayList<Pair<Float, Color>>(steps + 3)
    fun alpha(t: Float) = smoothstep(t).let { if (up) it else 1f - it }
    stops += 0f to Color.Black.copy(alpha = alpha(0f))
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        stops += (from + (to - from) * t) to Color.Black.copy(alpha = alpha(t))
    }
    stops += 1f to Color.Black.copy(alpha = alpha(1f))
    return if (horizontal) Brush.horizontalGradient(colorStops = stops.toTypedArray())
    else Brush.verticalGradient(colorStops = stops.toTypedArray())
}

/** A blurred copy of its content, shown through [mask] so the blur grows progressively. Edges are clamped, not faded. */
fun Modifier.progressiveBlur(radius: Dp, mask: Brush): Modifier = progressiveBlur(radius, listOf(mask))

/** [progressiveBlur] through several masks at once; their alphas multiply (a corner where two edges meet). */
fun Modifier.progressiveBlur(radius: Dp, masks: List<Brush>): Modifier = this
    .graphicsLayer {
        val r = radius.toPx()
        renderEffect = BlurEffect(r, r, TileMode.Clamp)
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        masks.forEach { drawRect(it, blendMode = BlendMode.DstIn) }
    }

/** Applies [effect] to the content, then keeps it only where [mask] is opaque (the mask is applied after the effect, so the layer is exactly opaque where the mask says 1). */
fun Modifier.effectThenMask(effect: RenderEffect, mask: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(mask, blendMode = BlendMode.DstIn)
    }
    .graphicsLayer { renderEffect = effect }

/** Keeps the content (as one layer) only where [mask] is opaque. */
fun Modifier.alphaMask(mask: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(mask, blendMode = BlendMode.DstIn)
    }

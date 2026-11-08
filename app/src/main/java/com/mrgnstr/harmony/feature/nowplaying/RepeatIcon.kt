package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import com.mrgnstr.harmony.core.designsystem.icons.SymbolPartsIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.playback.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tapping repeat on: the two arrows swing half a turn around the icon's centre and trade places (call [play] from the click). The
 * glyph looks the same at every multiple of 180 degrees, so the angle is never reset: each tap adds a half turn to where the last
 * one is heading (a quick second tap spins on instead of restarting the same turn), and the arrows never sit in one slot while the
 * other is empty, which a reset after each arrow's own spin did. The top arrow leads a beat.
 */
@Stable
internal class RepeatKick(private val scope: CoroutineScope) {
    /** Bottom and top arrow: degrees turned. */
    val angles = List(2) { Animatable(0f) }
    private var target = 0f

    fun play() {
        target += 180f
        angles.forEachIndexed { i, a ->
            scope.launch {
                delay(if (i == 1) 0L else 50L)
                a.animateTo(target, spring(dampingRatio = 0.65f, stiffness = 260f, visibilityThreshold = 0.05f))
            }
        }
    }
}

@Composable
internal fun rememberRepeatKick(): RepeatKick {
    val scope = rememberCoroutineScope()
    return remember(scope) { RepeatKick(scope) }
}

// Contours of Material "repeat" (bottom arrow, top arrow) and "repeat_one" (the 1, bottom arrow, top arrow).
private val RepeatParts = listOf(intArrayOf(0), intArrayOf(1))
private val RepeatOneParts = listOf(intArrayOf(1), intArrayOf(2), intArrayOf(0))

/** The repeat button's icon: two arrows, and with repeat-one a "1" that pops in. */
@Composable
internal fun RepeatIcon(blend: BlendMode, tint: Color, weight: Int, mode: RepeatMode, kick: RepeatKick, modifier: Modifier = Modifier) {
    val one = mode == RepeatMode.One
    // Rests at 0 while the "1" is not there, so its first frame is not a full-size flash; already in repeat-one when the player opens, it is just there.
    val pop = remember { Animatable(if (one) 1f else 0f) }
    LaunchedEffect(one) {
        if (one) pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f, visibilityThreshold = 0.001f))
        else pop.snapTo(0f)
    }
    SymbolPartsIcon(
        if (one) Symbols.RepeatOne else Symbols.Repeat, if (one) "Repeat one" else "Repeat", if (one) RepeatOneParts else RepeatParts,
        modifier, weight = weight,
    ) { part, path ->
        val centre = Offset(size.width / 2f, size.height / 2f)
        when (part) {
            0, 1 -> rotate(kick.angles[part].value, centre) { drawPath(path, tint, blendMode = blend) }
            else -> scale(pop.value, Offset(size.width * 0.479f, size.height * 0.5f)) { drawPath(path, tint, blendMode = blend) }
        }
    }
}

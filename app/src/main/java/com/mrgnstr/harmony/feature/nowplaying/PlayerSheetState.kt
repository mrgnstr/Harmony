package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import com.mrgnstr.harmony.core.device.VisualTier
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The one number behind the mini player ⇄ Now Playing morph: 0 = mini player, 1 = full screen. */
@Stable
class PlayerSheetState internal constructor(
    private val scope: CoroutineScope,
    initial: Float,
    private val lowTier: Boolean,
) {
    // Only capped above. A hard downward fling can drive the spring far below 0; a hard lower bound stopped it there, and the pill
    // froze above the tab bar. The spring is left free to come back, and only what is drawn is limited ([visual]).
    val progress = Animatable(initial).apply { updateBounds(Float.NEGATIVE_INFINITY, 1f) }

    /** [progress] as drawn: unchanged from 0 up, but below 0 (the pill sagging past its resting place) it saturates smoothly at -[SagLimit]. */
    val visual: Float get() = progress.value.let { if (it >= 0f) it else -SagLimit * tanh(-it / SagLimit) }

    val isExpanded: Boolean get() = progress.targetValue > 0.5f

    fun expand() { scope.launch { progress.animateTo(1f, SheetSpring) } }
    fun collapse() { scope.launch { progress.animateTo(0f, SheetSpring) } }

    /** Follows the finger; [travelPx] is the distance between the resting mini player and the top of the screen. */
    suspend fun dragBy(deltaYPx: Float, travelPx: Float) {
        if (travelPx > 0f) progress.snapTo((progress.value - deltaYPx / travelPx).coerceIn(0f, 1f))
    }

    /** Settles with the finger's speed: a flick decides, otherwise the nearer end. */
    fun settle(velocityYPx: Float, travelPx: Float) {
        val target = when {
            velocityYPx < -Fling -> 1f
            velocityYPx > Fling -> 0f
            progress.value > 0.5f -> 1f
            else -> 0f
        }
        val v = if (travelPx > 0f) -velocityYPx / travelPx else 0f
        scope.launch {
            if (lowTier) progress.animateTo(target, androidx.compose.animation.core.tween(200))
            else progress.animateTo(target, SheetSpring, initialVelocity = v)
        }
    }

    private companion object {
        const val Fling = 900f
        /** The most the pill's drawn sag reaches below rest, as a share of the travel to the top. */
        const val SagLimit = 0.1f
        /** One spring for opening, closing and every release: a hint of give at the end, and it carries the finger's speed. */
        val SheetSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 320f, visibilityThreshold = 0.0004f)
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    val low = LocalVisualTier.current == VisualTier.LOW
    return rememberSaveable(low, saver = Saver(save = { it.isExpanded }, restore = { PlayerSheetState(scope, if (it) 1f else 0f, low) })) {
        PlayerSheetState(scope, 0f, low)
    }
}

package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import com.mrgnstr.harmony.core.device.VisualTier
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The one number behind the mini player ⇄ Now Playing morph: 0 = mini player, 1 = full screen. */
@Stable
class PlayerSheetState internal constructor(
    private val scope: CoroutineScope,
    initial: Float,
    private val lowTier: Boolean,
    /** The sheet spring in px, for the swipe-away. */
    private val swipeSpec: AnimationSpec<Float>,
) {
    // Only capped above. A hard downward fling can drive the spring far below 0; a hard lower bound stopped it there, and the pill
    // froze above the tab bar. The spring is left free to come back, and only what is drawn is limited ([visual]).
    val progress = Animatable(initial).apply { updateBounds(Float.NEGATIVE_INFINITY, 1f) }

    /** [progress] as drawn: unchanged from 0 up, but below 0 (the pill sagging past its resting place) it saturates smoothly at -[SagLimit]. */
    val visual: Float get() = progress.value.let { if (it >= 0f) it else -SagLimit * tanh(-it / SagLimit) }

    val isExpanded: Boolean get() = progress.targetValue > 0.5f

    /** The mini player thrown down by a hard swipe: its downward travel in px (0 = where it rests). Separate from [progress], which stays 0. */
    val swipeAway = Animatable(0f)
    var swipingAway by mutableStateOf(false)
        private set

    /** The pill has faded out completely: no longer drawn, no longer takes touches (it still lies over the tab bar). */
    var swipeGone by mutableStateOf(false)
        private set

    /** How far the thrown pill has pushed the tab bar down (px, before the cap the scaffold puts on it). */
    var barPush by mutableFloatStateOf(0f)
        private set

    /** Resting as the mini player: not open, not moving, not being swiped away. Only then can a hard downward swipe dismiss it. */
    val isResting: Boolean get() = !swipingAway && !progress.isRunning && progress.value <= RestEps

    /**
     * Throws the pill down at the swipe's speed ([velocityYPx]) on a spring, towards a target that is further the harder the swipe
     * (so a harder swipe is still moving fast when it gets there). It pushes the tab bar from the moment it touches it ([gapPx]
     * down) and has faded out [goneAtPx] down; the bar then carries on with the pill's speed on the same spring and settles. Only
     * then is [onGone] called, so nothing heavy (the sheet leaving the screen) happens in the middle of the motion.
     */
    fun swipeAway(velocityYPx: Float, gapPx: Float, goneAtPx: Float, onGone: () -> Unit) {
        if (swipingAway) return
        swipingAway = true
        scope.launch {
            var gone = false
            val release = Job()
            // Within a pixel: a spring only settles at its target, so a travel that is just reached would not count.
            fun check(value: Float, speed: Float) {
                if (!gone) barPush = (value - gapPx).coerceAtLeast(0f)
                if (!gone && value >= goneAtPx - 1f) {
                    gone = true
                    swipeGone = true
                    scope.launch {
                        if (lowTier) animate(barPush, 0f, 0f, tween(200, easing = FastOutSlowInEasing)) { v, _ -> barPush = v }
                        else animate(barPush, 0f, speed, swipeSpec) { v, _ -> barPush = v }
                        release.complete()
                    }
                }
            }
            if (lowTier) swipeAway.animateTo(goneAtPx, tween(160, easing = FastOutSlowInEasing)) { check(value, 0f) }
            else swipeAway.animateTo(goneAtPx + velocityYPx * ThrowReachS, swipeSpec, initialVelocity = velocityYPx) { check(value, velocity) }
            if (!gone) {
                // Never got far enough after all: it bounced back and is the mini player again.
                swipingAway = false
                return@launch
            }
            release.join()
            onGone()
        }
    }

    /** Back to rest once nothing is loaded any more, ready for the next song. */
    suspend fun finishSwipeAway() {
        swipeAway.snapTo(0f)
        swipeGone = false
        swipingAway = false
    }

    fun expand() { if (!swipingAway) scope.launch { progress.animateTo(1f, SheetSpring) } }
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
        const val RestEps = 0.001f
        /** The swipe-away's target lies this many seconds of the swipe's speed beyond the point where the pill has faded out. */
        const val ThrowReachS = 0.05f
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
    val swipeSpec: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 320f, visibilityThreshold = 0.5f)
    return rememberSaveable(low, saver = Saver(save = { it.isExpanded }, restore = { PlayerSheetState(scope, if (it) 1f else 0f, low, swipeSpec) })) {
        PlayerSheetState(scope, 0f, low, swipeSpec)
    }
}

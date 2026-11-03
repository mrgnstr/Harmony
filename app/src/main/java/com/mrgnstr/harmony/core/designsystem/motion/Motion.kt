package com.mrgnstr.harmony.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh
import kotlinx.coroutines.launch

/**
 * The app's springs. Bounce is chosen per motion: things that *land* (pages, sheets, indicators coming to rest) settle
 * without overshoot; things that are *thrown* or *pressed* may overshoot a little. Start every settle with the gesture's
 * velocity (`Animatable.animateTo(target, spec, initialVelocity = v)`), so a fling carries into the animation.
 */
object Springs {
    /** Settles exactly, no overshoot: page moves, sheets, the tab indicator, colours. */
    fun <T> land(stiffness: Float = Spring.StiffnessMediumLow, visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = stiffness, visibilityThreshold = visibilityThreshold)

    /** A released or thrown object: carries the finger's speed and overshoots slightly. */
    fun <T> toss(stiffness: Float = Spring.StiffnessMediumLow, visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.75f, stiffness = stiffness, visibilityThreshold = visibilityThreshold)

    /** Pressed down: fast and firm. */
    fun <T> press(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 1400f, visibilityThreshold = visibilityThreshold)

    /** Let go: springs back with a visible bounce. */
    fun <T> release(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.45f, stiffness = 600f, visibilityThreshold = visibilityThreshold)
}

/**
 * The element squishes while pressed and springs back with a bounce on release. [pressedScale] is how far it gives.
 * Reads [interactionSource], so it reacts to the same presses as the clickable using it.
 */
fun Modifier.pressResponse(interactionSource: InteractionSource, pressedScale: Float = 0.94f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch { scale.animateTo(pressedScale, Springs.press(0.001f)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch { scale.animateTo(1f, Springs.release(0.001f)) }
            }
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Rubber-banding: how far something follows a finger dragged [overshoot] px past its limit. Follows almost 1:1 at first and
 * never exceeds [reach] px (tanh), like a stretched band.
 */
fun rubberBand(overshoot: Float, reach: Float, grip: Float = 0.55f): Float {
    if (reach <= 0f || overshoot == 0f) return 0f
    return sign(overshoot) * reach * tanh(abs(overshoot) * grip / reach)
}

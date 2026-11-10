package com.mrgnstr.harmony.core.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mrgnstr.harmony.BuildConfig

/**
 * Every debug switch in one place. [enabled] comes from the build (always in debug builds; in release only when built with
 * `-PdebugOptions=true`), and while it is off every [DebugFlag] reads as its default, so a release build behaves as if no
 * switch existed and R8 can drop the debug UI. The values are not saved: a forgotten switch never outlives the app.
 *
 * To add a switch: add a flag here (it joins [all], so Settings and the cover overlay list it) and read `flag.on` where it matters.
 */
object DebugOptions {
    const val enabled: Boolean = BuildConfig.DEBUG_OPTIONS

    private val registry = ArrayList<DebugFlag>()
    private fun flag(label: String, default: Boolean = true) = DebugFlag(label, default).also { registry += it }

    /** The Now Playing cover's switches panel (swipe sideways on the cover to hide it). */
    var overlayVisible by mutableStateOf(true)

    // The blur around the Now Playing cover. Off = what is left without that layer (all off: the raw stretched edge rows).
    val shader = flag("Shader blur")
    val smoothing = flag("Pre/post smoothing")
    val far = flag("Far gaussian")
    val curveAbove = flag("Far curve, top")
    val curveBelow = flag("Far curve, bottom")
    val scrim = flag("Darkening")

    /** About one level of noise over the blurred background (see Modifier.dither), against banding on dark covers. */
    val dither = flag("Dither")

    /** Greys out what can't be played offline (see OfflineDim) without touching the network, so it can be checked over wireless adb. */
    val offline = flag("Pretend to be offline", default = false)

    val all: List<DebugFlag> get() = registry
}

class DebugFlag internal constructor(val label: String, private val default: Boolean) {
    private var state by mutableStateOf(default)

    /** The value to act on: the switch while debug options are enabled, the default otherwise. */
    var on: Boolean
        get() = if (DebugOptions.enabled) state else default
        set(value) { state = value }
}

package com.mrgnstr.harmony.core.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** The device's own tick haptic (the system's, so it feels like the rest of the phone), as a plain callback. */
class TickHaptics internal constructor(private val view: View) {
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
}

@Composable
fun rememberTickHaptics(): TickHaptics {
    val view = LocalView.current
    return remember(view) { TickHaptics(view) }
}

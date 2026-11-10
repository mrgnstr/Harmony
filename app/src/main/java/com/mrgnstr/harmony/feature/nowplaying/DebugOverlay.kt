package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.debug.DebugOptions
import kotlin.math.abs

/**
 * The debug switches ([DebugOptions.all]), drawn over the cover (only composed when debug options are enabled). A swipe sideways anywhere on the
 * cover hides or shows them (it watches without consuming, so the other gestures still work).
 */
@Composable
internal fun DebugOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier.pointerInput(Unit) {
            val reach = 72.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    // Sideways only: the sheet's own drag is vertical.
                    if (abs(dx) >= reach && abs(dx) > 1.5f * abs(dy)) {
                        DebugOptions.overlayVisible = !DebugOptions.overlayVisible
                        // Once per swipe: wait for the finger to lift.
                        do {
                            val next = awaitPointerEvent(PointerEventPass.Initial)
                        } while (next.changes.any { it.pressed })
                        break
                    }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (DebugOptions.overlayVisible) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.7f), contentColor = Color.White) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Debug options")
                    DebugOptions.all.forEach { flag -> DebugSwitch(flag.label, flag.on) { flag.on = it } }
                }
            }
        }
    }
}

@Composable
private fun DebugSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.width(240.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label)
        Switch(checked, onChange)
    }
}

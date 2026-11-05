package com.mrgnstr.harmony.core.designsystem.blur

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.LocalChromeBlurMode
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.device.VisualTier
import com.mrgnstr.harmony.core.model.BlurMode
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/**
 * The floating chrome (tab bar, search button, mini player) blurs what scrolls under it. Backed by Haze; screens only see
 * [ChromeBlurState], [chromeSource] and [ChromeSurface]. One source per screen; a blur inside its own source crashes Haze, so
 * anything floating inside the source uses [ChromeSurface] with a null state (a flat tint).
 */
@Stable
class ChromeBlurState internal constructor(internal val haze: HazeState)

@Composable
fun rememberChromeBlurState(): ChromeBlurState = remember { ChromeBlurState(HazeState()) }

/** Provided by the scaffold: the pages' shared source. */
val LocalChromeBlurState = staticCompositionLocalOf<ChromeBlurState?> { null }

/** Marks content the chrome blurs. Higher [zIndex] sources draw over lower ones in the blur. */
fun Modifier.chromeSource(state: ChromeBlurState, zIndex: Float = 0f): Modifier = hazeSource(state.haze, zIndex = zIndex)

/** Live blur needs API 31; Auto keeps it to the HIGH tier (on MID phones it cost whole frames in the old app). */
@Composable
fun liveChromeBlur(state: ChromeBlurState?): Boolean =
    state != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && when (LocalChromeBlurMode.current) {
        BlurMode.Auto -> LocalVisualTier.current == VisualTier.HIGH
        BlurMode.On -> true
        BlurMode.Off -> false
    }

// Tint over the live blur, and the opacity of the flat fallback.
private const val BlurTintAlpha = 0.72f
private const val FlatAlpha = 0.94f

/**
 * A surface of the floating chrome: [container] (default: the scheme's surfaceContainer) over a live blur of [state]'s
 * sources, or nearly opaque [container] where live blur is off or unavailable.
 */
@Composable
fun ChromeSurface(
    state: ChromeBlurState?,
    shape: Shape,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit = {},
) {
    val base = modifier.clip(shape)
    val opaque = MaterialTheme.colorScheme.surface
    val surface = if (liveChromeBlur(state)) {
        base.hazeBlur(
            input = HazeInput.Sources(state!!.haze, selection = HazeSourceSelection.All),
            style = HazeBlurStyle {
                blurEnabled(true)
                // The captured source is mostly transparent; without an opaque base the sharp content would show through.
                backgroundColor(opaque)
                blurRadius(28.dp)
                noiseFactor(0f)
                colorEffects(listOf(HazeColorEffect.tint(container.copy(alpha = BlurTintAlpha))))
            },
            performanceMode = HazePerformanceMode.Performance,
        )
    } else {
        base.background(container.copy(alpha = FlatAlpha))
    }
    Box(surface) { content() }
}

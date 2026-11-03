package com.mrgnstr.harmony.core.designsystem

import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.device.VisualTier
import com.mrgnstr.harmony.core.model.BlurMode

/** Every expensive effect reads this; provided once by [com.mrgnstr.harmony.core.designsystem.theme.HarmonyTheme]. */
val LocalVisualTier = compositionLocalOf { VisualTier.MID }

/** The user's override for the live blur behind the floating tab bar, search button and mini player. */
val LocalChromeBlurMode = compositionLocalOf { BlurMode.Auto }

/** The user's override for the live blur around the Now Playing cover. */
val LocalCoverBlurMode = compositionLocalOf { BlurMode.Auto }

/**
 * The "Real-Time Progressive Blur" setting as it applies here: the cover's surroundings (Now Playing, the artist hero) are blurred live per frame
 * (API 33+, shaders and blur effects); otherwise from copies prerendered once on the CPU.
 */
@Composable
fun realTimeBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && when (LocalCoverBlurMode.current) {
    BlurMode.Auto -> LocalVisualTier.current != VisualTier.LOW
    BlurMode.On -> true
    BlurMode.Off -> false
}

/** Space the floating tab bar / mini player cover at the bottom, so scrolling content can pad for it. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * Landscape: where the resting mini player's pill sits, as its left edge from the display's left and its bottom edge from the display's
 * bottom ([shown]: a song is loaded and the pill is there), so a page can put controls beside it. Null in portrait, where the tab bar is under it.
 */
@Immutable
data class MiniPlayerSlot(val left: Dp, val bottom: Dp, val shown: Boolean)

val LocalMiniPlayerSlot = compositionLocalOf<MiniPlayerSlot?> { null }

/**
 * What content keeps clear of at the left and right (landscape: the tab rail, the cutout, a side navigation bar). Pages still fill
 * the display: lists pad their rows by it, and horizontal shelves scroll out to the display edges. Absolute sides, not start/end.
 */
@Immutable
data class SideInsets(val left: Dp = 0.dp, val right: Dp = 0.dp) {
    /** A list's content padding: [horizontal] beyond the insets at both sides. */
    fun padding(horizontal: Dp = 0.dp, top: Dp = 0.dp, bottom: Dp = 0.dp) = PaddingValues.Absolute(left + horizontal, top, right + horizontal, bottom)
}

val LocalSideInsets = compositionLocalOf { SideInsets() }

/**
 * A horizontal shelf in a list padded by [insets]: widened back out to the display edges, so its items scroll right to the edge
 * instead of being cut off at the padding. Its own content padding adds the insets back, so it rests where it did.
 */
fun Modifier.bleedSides(insets: SideInsets): Modifier = if (insets.left == 0.dp && insets.right == 0.dp) this else layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val left = insets.left.roundToPx()
    val wide = constraints.maxWidth + left + insets.right.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-left, 0) }
}

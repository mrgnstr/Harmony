package com.mrgnstr.harmony.core.designsystem.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.device.VisualTier

// Ported as-is from Harmony old (DetailPage.kt), approved there: the numbers are tuned together.
/** Where the cover's top sits below the status bar. */
val CoverTop = 72.dp
/** Gap between the cover's bottom and the title (the header's top padding). */
val HeaderGap = 28.dp
// Cover scroll speed relative to the content: CoverTop / (CoverTop + HeaderGap), so top-hits-status-bar and title-touches-cover coincide.
private const val CoverSpeed = 72f / 100f
/** The cover is [CoverWidthFraction] of the page's width. */
const val CoverWidthFraction = 0.65f
// Past that point the title is one cover-height below the status bar. The blur reaches full strength and the fade finishes (as fractions of
// the cover height scrolled past the parallax) as the title nears the status bar.
private const val CoverBlurEnd = 0.9f
private const val CoverFadeStart = 0f
private const val CoverFadeEnd = 0.95f
// The cover starts zooming at 50% of the full blur and zooms faster as it fades out, up to this scale.
private const val CoverZoomStartBlur = 0.5f
private const val CoverZoomMax = 1.3f

/**
 * How far the cover has scrolled past its parallax, in cover heights (0 until the title touches it): the same measure that drives
 * the blur and fade, for anything that should follow the cover going away (the top bar's background and title).
 */
fun LazyListState.coverPast(density: Float, pageWidthPx: Int): Float {
    val scrolled = if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset.toFloat() else 1e6f
    val coverHeight = (pageWidthPx * CoverWidthFraction).coerceAtLeast(1f)
    return ((scrolled - (CoverTop + HeaderGap).value * density) / coverHeight).coerceAtLeast(0f)
}

/** Big cover that parallaxes, then blurs, zooms and fades as the page scrolls under the floating bar. [content] draws the artwork. */
@Composable
fun CoverFrame(listState: LazyListState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocalVisualTier.current != VisualTier.LOW
    Box(modifier.fillMaxWidth().padding(top = statusTop + CoverTop), contentAlignment = Alignment.TopCenter) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .fillMaxWidth(CoverWidthFraction)
                .aspectRatio(1f)
                .graphicsLayer {
                    val dp = density
                    val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 1e6f
                    // Parallax: the cover always climbs slower than the content (speed CoverSpeed). Its top reaches the status bar just as
                    // the title touches its bottom (the speed is chosen to make that coincide); only from there does it blur and fade,
                    // while the title slides over it.
                    val parallaxEnd = (CoverTop + HeaderGap).value * dp
                    translationY = scrolled * (1f - CoverSpeed)
                    val past = ((scrolled - parallaxEnd) / size.height).coerceAtLeast(0f) // in cover heights
                    if (canBlur && past > 0f) {
                        val r = 32.dp.toPx() * (past / CoverBlurEnd).coerceAtMost(1f)
                        renderEffect = BlurEffect(r, r, TileMode.Decal)
                    }
                    val zoomStart = CoverZoomStartBlur * CoverBlurEnd
                    val zoom = ((past - zoomStart) / (CoverFadeEnd - zoomStart)).coerceIn(0f, 1f)
                    scaleX = 1f + (CoverZoomMax - 1f) * zoom * zoom
                    scaleY = scaleX
                    alpha = 1f - ((past - CoverFadeStart) / (CoverFadeEnd - CoverFadeStart)).coerceIn(0f, 1f)
                }
                .clip(shape),
        ) {
            content()
        }
    }
}

package com.mrgnstr.harmony.core.designsystem.flow

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.mrgnstr.harmony.core.designsystem.dither
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Width of the small bitmap the background is drawn at (height follows the aspect); it is upscaled, so the blur costs almost nothing. */
private const val RenderWidth = 128

/**
 * The animated Apple-Music-style background of [cover] (see [FlowRenderer]). Changing [cover] crossfades over 500 ms like Apple's own.
 * Drawn on a background thread at [fps]; the cover should be at most ~300 px on its long side.
 */
@Composable
fun FlowBackground(
    cover: Bitmap?,
    modifier: Modifier = Modifier,
    params: FlowParams = FlowParams(),
    fps: Int = 30,
    /** Shown until the first frame is ready (and behind a transparent cover). */
    background: Color = Color.Transparent,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { size = it }.background(background)) {
        if (size != IntSize.Zero) {
            Crossfade(cover, animationSpec = tween(500), label = "flow cover") { art ->
                if (art != null) FlowLayer(art, size, params, fps)
            }
        }
    }
}

@Composable
private fun FlowLayer(art: Bitmap, size: IntSize, params: FlowParams, fps: Int) {
    val context = LocalContext.current
    val current by rememberUpdatedState(params)
    val renderer = remember(art, size) {
        val h = (RenderWidth * size.height.toFloat() / size.width).toInt().coerceAtLeast(16)
        FlowRenderer(FlowMeshData.get(context), art, RenderWidth, h, size.width, size.height)
    }
    var tick by remember(renderer) { mutableIntStateOf(0) }
    LaunchedEffect(renderer, fps) {
        val frameNs = 1_000_000_000L / fps
        var last = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            if (now - last < frameNs) continue
            last = now
            // One clock for every layer (a crossfade keeps the motion going), in seconds.
            val seconds = System.nanoTime() / 1e9
            val p = current
            withContext(Dispatchers.Default) { renderer.render(seconds, p) }
            tick++
        }
    }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize().dither()) {
        tick // read: invalidates the draw when a frame is ready
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(renderer.front, null, Rect(0, 0, this.size.width.toInt(), this.size.height.toInt()), paint)
        }
    }
}

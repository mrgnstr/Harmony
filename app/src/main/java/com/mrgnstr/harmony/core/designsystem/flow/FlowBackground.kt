package com.mrgnstr.harmony.core.designsystem.flow

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
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
 * The animated Apple-Music-style background of [cover] (see [FlowRenderer]). A new [cover] fades in over the old one in 500 ms like Apple's own;
 * the old one stays fully shown until then (also while [cover] is null, i.e. while the next one loads), so the background is never see-through.
 * Drawn on a background thread at [fps] (0: a single frame); the cover should be at most ~300 px on its long side.
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
    // [shown] is fully opaque; [incoming] fades in over it, then takes its place.
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    var incoming by remember { mutableStateOf<Bitmap?>(null) }
    val fadeIn = remember { Animatable(1f) }
    LaunchedEffect(cover) {
        val next = cover ?: return@LaunchedEffect
        if (next === shown) return@LaunchedEffect
        if (shown == null) { shown = next; return@LaunchedEffect }
        incoming = next
        fadeIn.snapTo(0f)
        fadeIn.animateTo(1f, tween(500))
        shown = next
        incoming = null
    }
    Box(modifier.onSizeChanged { size = it }.background(background)) {
        if (size != IntSize.Zero) {
            // Keyed by the bitmap, so the incoming layer keeps its renderer when it becomes the shown one.
            for (art in listOfNotNull(shown, incoming)) {
                key(art) { FlowLayer(art, size, params, fps, if (art === incoming) ({ fadeIn.value }) else null) }
            }
        }
    }
}

@Composable
private fun FlowLayer(art: Bitmap, size: IntSize, params: FlowParams, fps: Int, fade: (() -> Float)?) {
    val context = LocalContext.current
    val current by rememberUpdatedState(params)
    val renderer = remember(art, size) {
        val h = (RenderWidth * size.height.toFloat() / size.width).toInt().coerceAtLeast(16)
        FlowRenderer(FlowMeshData.get(context), art, RenderWidth, h, size.width, size.height)
    }
    var tick by remember(renderer) { mutableIntStateOf(0) }
    LaunchedEffect(renderer, fps) {
        if (fps <= 0) {
            // Idle: one frame, so there is something to show when it is needed, and no redraws of the window until then.
            val p = current
            withContext(Dispatchers.Default) { renderer.render(System.nanoTime() / 1e9, p) }
            tick++
            return@LaunchedEffect
        }
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
    Canvas(Modifier.fillMaxSize().then(if (fade != null) Modifier.graphicsLayer { alpha = fade() } else Modifier).dither()) {
        tick // read: invalidates the draw when a frame is ready
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(renderer.front, null, Rect(0, 0, this.size.width.toInt(), this.size.height.toInt()), paint)
        }
    }
}

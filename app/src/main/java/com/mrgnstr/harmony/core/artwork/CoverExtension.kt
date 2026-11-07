package com.mrgnstr.harmony.core.artwork

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.CoverFadeMs
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.designsystem.effectThenMask
import com.mrgnstr.harmony.core.designsystem.realTimeBlur
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.device.VisualTier
import com.mrgnstr.harmony.core.model.BlurMode
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Blur around the full-screen cover. Starting radius at the cover's edge (a gaussian sigma), per side.
private val TopEdgeBlur = 5.dp
private val BottomEdgeBlur = 5.dp

/** The blurred layers reach this far into the cover, fading in over it, so the picture melts into the blur. */
private val FadeIntoCover = 15.dp

/**
 * How fast the blur radius grows with distance from the cover's edge (radius per distance, both in px), the same on both
 * sides. The radius at the screen's top and bottom edge follows from the room there.
 */
private const val BlurSlope = 0.75f

/** The growing blur starts this far from the cover's edge. */
private val RampDelay = 0.dp

/** Radius growth shape: radius = max * t^RadiusCurve. 1 = linear. */
private const val RadiusCurve = 1.0f

private val CoverBitmapCache = LruCache<String, ImageBitmap>(6)

internal class LoadedCover(val key: String, val bitmap: ImageBitmap?)

/**
 * Holds the active cover and, while a song change is crossfading, the previous cover [from] and the shared [alpha]
 * (0..1 over [CoverFadeMs]). Both the sharp cover in the player sheet and [SceneTiles] in [CoverExtension] draw from
 * this state so they start and finish fading on the exact same frame.
 */
@Stable
class CoverTransition {
    internal var current by mutableStateOf<LoadedCover?>(null)
    internal var from by mutableStateOf<LoadedCover?>(null)
    internal val alpha = Animatable(1f)
}

@Composable
internal fun rememberCoverTransition(songId: Long, albumId: Long, sizePx: Int): CoverTransition {
    val context = LocalContext.current
    val state = remember { CoverTransition() }
    val key = remember(songId, albumId) { ArtworkKey(songId, albumId) }
    val targetPx = sizePx.coerceIn(256, 2048)
    val cacheId = "${key.cacheKey}|$targetPx"
    LaunchedEffect(cacheId) {
        if (state.current?.key == key.cacheKey) return@LaunchedEffect
        val bitmap = CoverBitmapCache.get(cacheId) ?: withContext(Dispatchers.IO) {
            ArtworkDecoder.decode(context, key.songId, key.albumId, targetPx)?.asImageBitmap()
                ?.also { CoverBitmapCache.put(cacheId, it) }
        }
        val loaded = LoadedCover(key.cacheKey, bitmap)
        val prev = state.current
        if (prev == null) {
            state.current = loaded
        } else {
            state.from = prev
            state.current = loaded
            state.alpha.snapTo(0f)
            try {
                state.alpha.animateTo(1f, tween(CoverFadeMs))
            } finally {
                if (state.current === loaded) state.from = null
            }
        }
    }
    return state
}

/** Draws [state]'s cover, crossfading from the previous cover when [CoverTransition.from] is set. */
@Composable
internal fun CoverBox(state: CoverTransition, modifier: Modifier = Modifier) {
    Box(modifier) {
        val prev = state.from
        val curr = state.current
        if (prev != null) {
            SingleCover(prev.bitmap, Modifier.fillMaxSize())
        }
        if (curr != null) {
            SingleCover(
                curr.bitmap,
                Modifier.fillMaxSize().then(if (prev != null) Modifier.graphicsLayer { alpha = state.alpha.value } else Modifier),
            )
        } else {
            SingleCover(null, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SingleCover(bitmap: ImageBitmap?, modifier: Modifier) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            SymbolIcon(Symbols.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), size = 72.dp)
        }
    }
}

/**
 * The cover's surroundings, drawn over the sharp cover.
 *
 * Android 13+ (not LOW tier): a live shader gives every pixel its own blur radius, growing linearly with distance from
 * the cover's edge. Everything else (Android 12 and older, low-end devices): the same look precomputed once per cover
 * on a background thread by [load] as two bitmaps, so nothing is blurred per frame.
 */
@Composable
fun CoverExtension(
    songId: Long,
    albumId: Long,
    cover: CoverTransition,
    coverTopPx: Float,
    coverSizePx: Float,
    load: suspend (CoverStripSpec) -> CoverStrips?,
    modifier: Modifier = Modifier,
    slopeOverride: Float? = null,
) {
    val density = LocalDensity.current
    val liveShader = realTimeBlur()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val coverBottom = coverTopPx + coverSizePx
        val slope = slopeOverride ?: BlurSlope

        if (liveShader) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                LiveShaderExtension(cover, coverTopPx, coverSizePx, coverBottom, w, h, slope)
            }
        } else {
            PrecomputedExtension(songId, albumId, coverTopPx, coverBottom, w, h, slope, load)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LiveShaderExtension(
    cover: CoverTransition,
    coverTopPx: Float,
    coverSizePx: Float,
    coverBottom: Float,
    w: Float,
    h: Float,
    slope: Float,
) {
    val density = LocalDensity.current
    val fadePx = with(density) { FadeIntoCover.toPx() }

    // Below the cover: the cover's last row stretched downwards, blurred with a growing radius, fading out into the cover.
    if (coverBottom < h - 1f) {
        val sigma = with(density) { BottomEdgeBlur.toPx() }
        val y0 = (coverBottom - fadePx - 3f * sigma).coerceAtLeast(0f)
        val edgeLocal = coverBottom - y0
        val dist = h - coverBottom
        val mask = remember(h, y0, edgeLocal, fadePx) {
            val start = ((edgeLocal - fadePx) / (h - y0)).coerceIn(0f, 1f)
            val end = (edgeLocal / (h - y0)).coerceIn(start, 1f)
            val steps = 8
            val stops = (0..steps).map { i ->
                val t = i / steps.toFloat()
                (start + (end - start) * t) to Color.Black.copy(alpha = smoothstep(t))
            }
            Brush.verticalGradient(colorStops = (listOf(0f to Color.Transparent) + stops + listOf(1f to Color.Black)).toTypedArray())
        }
        VariableEdgeBlur(cover, coverTopPx, coverSizePx, w, y0, h, edgeLocal, below = true, sigmaPx = sigma,
            startPx = with(density) { RampDelay.toPx() }, distPx = dist,
            maxRadiusDp = Dp(slope * dist / density.density), mask = mask)
    }

    // Above the cover: the cover's first row stretched upwards, same treatment.
    if (coverTopPx > 1f) {
        val sigma = with(density) { TopEdgeBlur.toPx() }
        val boxHeight = (coverTopPx + fadePx + 3f * sigma).coerceAtMost(h)
        Box(Modifier.size(with(density) { w.toDp() }, with(density) { coverTopPx.toDp() }).clipToBounds()) {
            SceneTiles(cover, coverTopPx, coverSizePx, 0f, coverTopPx, w, stretchAbove = true)
        }
        // Full strength up to the cover's edge, then fading out over [FadeIntoCover] inside the cover itself.
        val mask = remember(boxHeight, coverTopPx, fadePx) {
            val edge = (coverTopPx / boxHeight).coerceIn(0f, 1f)
            val end = ((coverTopPx + fadePx) / boxHeight).coerceIn(edge, 1f)
            val steps = 8
            val stops = (0..steps).map { i ->
                val t = i / steps.toFloat()
                (edge + (end - edge) * t) to Color.Black.copy(alpha = 1f - smoothstep(t))
            }
            Brush.verticalGradient(colorStops = (listOf(0f to Color.Black) + stops + listOf(1f to Color.Transparent)).toTypedArray())
        }
        VariableEdgeBlur(cover, coverTopPx, coverSizePx, w, 0f, boxHeight, coverTopPx, below = false, sigmaPx = sigma,
            startPx = with(density) { RampDelay.toPx() }, distPx = coverTopPx,
            maxRadiusDp = Dp(slope * coverTopPx / density.density), mask = mask)
    }
}

/** The two pre-blurred bitmaps built by [load]; used where the live shader isn't available. */
@Composable
private fun PrecomputedExtension(
    songId: Long,
    albumId: Long,
    coverTopPx: Float,
    coverBottom: Float,
    w: Float,
    h: Float,
    slope: Float,
    load: suspend (CoverStripSpec) -> CoverStrips?,
) {
    val density = LocalDensity.current
    val top = coverTopPx.roundToInt()
    val bottom = coverBottom.roundToInt()
    val width = w.roundToInt()
    val height = h.roundToInt()
    val fadePx = with(density) { FadeIntoCover.roundToPx() }
    val spec = remember(songId, albumId, width, height, top, bottom, density.density) {
        CoverStripSpec(
            songId, albumId, width,
            aboveCoverPx = top.coerceAtLeast(0), belowCoverPx = (height - bottom).coerceAtLeast(0),
            fadePx = fadePx,
            topSigmaPx = with(density) { TopEdgeBlur.toPx() }, bottomSigmaPx = with(density) { BottomEdgeBlur.toPx() },
            slope = slope,
        )
    }
    var strips by remember { mutableStateOf<CoverStrips?>(null) }
    LaunchedEffect(spec) {
        strips = load(spec)
    }

    // The old strips stay until the new ones are ready, then fade across, so a song change never shows a gap or a mismatch.
    Crossfade(targetState = strips, animationSpec = tween(CoverFadeMs), label = "cover-strips") { st ->
        if (st == null) return@Crossfade
        Box(Modifier.fillMaxSize()) {
            st.top?.let { bmp ->
                val image = remember(bmp) { bmp.asImageBitmap() }
                Image(
                    bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium,
                    modifier = Modifier.size(with(density) { width.toDp() }, with(density) { (top + fadePx).toDp() }),
                )
            }
            st.bottom?.let { bmp ->
                val image = remember(bmp) { bmp.asImageBitmap() }
                val y = bottom - fadePx
                Image(
                    bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium,
                    modifier = Modifier.offset { IntOffset(0, y) }
                        .size(with(density) { width.toDp() }, with(density) { (height - y).toDp() }),
                )
            }
        }
    }
}

/**
 * The sharp scene between [y0] and [y1]: the real cover, plus the cover's own edge rows stretched away from it.
 * With [stretchAbove] the space above the cover is the cover's top rows stretched upwards; with [stretchBelow] the space
 * below is its bottom rows stretched downwards. Otherwise flipped copies of the cover alternate outwards (a mirror).
 */
@Composable
private fun SceneTiles(
    cover: CoverTransition,
    coverTopPx: Float,
    coverSizePx: Float,
    y0: Float,
    y1: Float,
    widthPx: Float,
    stretchAbove: Boolean = false,
    stretchBelow: Boolean = false,
    stretchRows: Float = StretchRows,
) {
    val density = LocalDensity.current
    val tileW = with(density) { widthPx.toDp() }
    // 2 px taller than needed (and shifted up 1 px) so neighbouring tiles overlap: rounding must never leave a seam.
    val tileH = with(density) { (coverSizePx + 2f).toDp() }
    val kMin = floor((y0 - coverTopPx) / coverSizePx).toInt()
    val kMax = floor((y1 - coverTopPx) / coverSizePx).toInt()
    for (k in kMin..kMax) {
        if ((stretchAbove && k < 0) || (stretchBelow && k > 0)) continue
        val tileTop = coverTopPx + k * coverSizePx
        CoverBox(
            state = cover,
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(tileW, tileH)
                .offset { IntOffset(0, (tileTop - y0).roundToInt() - 1) }
                // k = 0 is the real cover; its neighbours are flipped so the picture continues across the edge.
                .graphicsLayer { scaleY = if (k % 2 != 0) -1f else 1f },
        )
    }
    if (stretchAbove && y0 < coverTopPx) {
        // The cover's first StretchRows rows, scaled (and flipped) upwards from its top edge to fill the space above it.
        val space = coverTopPx - y0
        CoverBox(
            state = cover,
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(tileW, tileH)
                .offset { IntOffset(0, (coverTopPx - y0).roundToInt()) }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleY = -(space / stretchRows)
                },
        )
    }
    if (stretchBelow && y1 > coverTopPx + coverSizePx) {
        // The cover's last StretchRows rows, flipped about the cover's bottom edge, scaled down to fill the space below it.
        val space = y1 - (coverTopPx + coverSizePx)
        CoverBox(
            state = cover,
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(tileW, tileH)
                // The cover tile reaches 1 px past its bottom edge (overlap), so the stretched copy starts there, not 2 px lower.
                .offset { IntOffset(0, (coverTopPx - y0).roundToInt() - 1) }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = -(space / stretchRows)
                },
        )
    }
}

/** How many of the cover's edge rows (px) are stretched to fill the space above or below it. */
private const val StretchRows = 3f

/**
 * A blur whose radius grows with distance from the cover's edge (API 33+): a shader gives every pixel its own blur
 * radius. The scene is first blurred by [sigmaPx] (so the shader's sparse samples read smooth data), then each pixel
 * averages samples on a disc whose radius grows from 0 at [startPx] from the edge to [maxRadiusDp] at [distPx].
 * [edgeY] is the cover's edge in this box's own coordinates; the box spans screen y [y0] to [y1].
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun VariableEdgeBlur(
    cover: CoverTransition,
    coverTopPx: Float,
    coverSizePx: Float,
    widthPx: Float,
    y0: Float,
    y1: Float,
    edgeY: Float,
    below: Boolean,
    sigmaPx: Float,
    startPx: Float,
    distPx: Float,
    maxRadiusDp: Dp,
    mask: Brush,
) {
    val density = LocalDensity.current
    val q = LiveBlurDownscale
    val boxHeightPx = y1 - y0
    val maxRadius = maxRadiusDp.value * density.density
    // The whole effect runs on a layer 1/q the size in each direction (1/q^2 the pixels) and is scaled up afterwards.
    // The blur is soft by construction, so the only visible cost is a little extra softness (about q full-res pixels).
    val lowW = widthPx / q
    val lowH = boxHeightPx / q
    val effect = remember(widthPx, boxHeightPx, edgeY, below, sigmaPx, startPx, distPx, maxRadius, q) {
        val shader = RuntimeShader(VariableBlurAgsl).apply {
            setFloatUniform("res", lowW, lowH)
            setFloatUniform("edgeY", edgeY / q)
            setFloatUniform("dir", if (below) 1f else -1f)
            setFloatUniform("startPx", startPx / q)
            setFloatUniform("distPx", distPx / q)
            setFloatUniform("maxR", maxRadius / q)
            setFloatUniform("gamma", RadiusCurve)
        }
        val sigmaLow = (sigmaPx / q).coerceAtLeast(0.5f)
        // Pre-blur, then the variable-radius shader, then a light blur: the shader's randomised samples leave a fine grain
        // that, drawn four times larger, shows as pixelation where the radius is big. The last blur smooths it away.
        val shaded = android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content"),
            android.graphics.RenderEffect.createBlurEffect(sigmaLow, sigmaLow, Shader.TileMode.CLAMP),
        )
        android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createBlurEffect(GrainSmoothing, GrainSmoothing, Shader.TileMode.CLAMP),
            shaded,
        ).asComposeRenderEffect()
    }
    Box(
        Modifier
            .offset { IntOffset(0, y0.roundToInt()) }
            .size(with(density) { widthPx.toDp() }, with(density) { boxHeightPx.toDp() })
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(with(density) { lowW.toDp() }, with(density) { lowH.toDp() })
                .graphicsLayer {
                    scaleX = q; scaleY = q
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .effectThenMask(effect, mask),
        ) {
            SceneTiles(
                cover,
                coverTopPx / q, coverSizePx / q, y0 / q, y1 / q, lowW,
                stretchAbove = !below, stretchBelow = below, stretchRows = StretchRows / q,
            )
        }
    }
}

/** The live blur is computed at 1/this of the screen resolution in each direction. */
private const val LiveBlurDownscale = 4f

/** Gaussian sigma, in the shader's low-resolution pixels, of the blur that smooths the sampling grain. */
private const val GrainSmoothing = 1.2f

private fun smoothstep(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

// Per-pixel variable-radius blur: radius grows from 0 at startPx from the cover's edge to maxR at distPx.
private const val VariableBlurAgsl = """
uniform shader content;
uniform float2 res;
uniform float edgeY;
uniform float dir;
uniform float startPx;
uniform float distPx;
uniform float maxR;
uniform float gamma;

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

half4 main(float2 p) {
    float t = clamp(((p.y - edgeY) * dir - startPx) / max(distPx - startPx, 1.0), 0.0, 1.0);
    float r = maxR * pow(t, gamma);
    if (r < 0.5) { return content.eval(p); }
    half4 acc = half4(0.0);
    float rot = hash(p) * 6.2831853;
    const int N = 64;
    for (int i = 0; i < N; i++) {
        float fi = (float(i) + 0.5) / float(N);
        float a = float(i) * 2.3999632 + rot;
        float2 q = p + float2(cos(a), sin(a)) * (sqrt(fi) * r);
        q = clamp(q, float2(0.5), res - float2(0.5));
        acc += content.eval(q);
    }
    return acc / float(N);
}
"""

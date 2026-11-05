@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.mrgnstr.harmony.feature.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.artwork.Gaussian
import com.mrgnstr.harmony.core.artwork.ArtworkDecoder
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.progressiveBlur
import com.mrgnstr.harmony.core.designsystem.smoothRamp
import com.mrgnstr.harmony.core.designsystem.realTimeBlur
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The hero climbs at this share of the content's speed (it lags behind, so the name slides up over it).
private const val HeroSpeed = 0.45f
// Blur and fade ranges, as fractions of the hero height scrolled.
private const val HeroBlurStart = 0.46f
private const val HeroBlurEnd = 0.75f
private const val HeroFadeStart = 0.55f
private const val HeroFadeEnd = 1f
// The image is taller than the hero's layout box (the name, buttons and the top of the next card sit over its lower part), as
// in Apple Music: it stays sharp down to the shoulders, then blurs gently and dissolves into the page colour behind the buttons.
internal const val HeroImageRatio = 1.22f
// The image's edge rows stretched to extend it (as under Now Playing's cover).
internal const val HeroStretchRows = 3f
// The lower part of the hero dissolves into the page colour along a smoothstep curve (no visible start or end edge).
internal const val HeroFadeFrom = 0.61f
// Progressive blur: two blurred copies of the image fade in over the sharp one, the further down the blurrier (radius, from, to).
// Both finish (fully opaque) before the image's bottom edge at 0.82 of the zone, so the edge never shows.
internal val HeroBlurLayers = listOf(Triple(6.dp, 0.55f, 0.77f), Triple(26.dp, 0.61f, 0.83f))
@Composable
internal fun ArtistHero(state: ArtistUiState, listState: LazyListState, leftAligned: Boolean = false) {
    val live = realTimeBlur()
    val context = LocalContext.current
    val density = LocalDensity.current
    // The image is shown at its natural shape, full width, top-aligned (a cover is square; a custom photo is at least square and
    // at most 3:2 portrait). The blur zone below it is [zone] wide: where the image ends, its last rows are stretched so the
    // blur keeps going (an extension, not a mirror).
    val natural = (if (state.imagePath != null) state.imageRatio else 1f).coerceIn(1f, 1.5f)
    val zone = maxOf(HeroImageRatio, natural)
    val pageBg = MaterialTheme.colorScheme.surface
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val imageHeight = maxWidth * natural
        val extension = maxWidth * (zone - natural)
        // Without the real-time blur: the image, its extension and the blurred copies are prerendered once on the CPU.
        val widthPx = constraints.maxWidth
        val imageHeightPx = (widthPx * natural).roundToInt()
        val zoneHeightPx = (widthPx * zone).roundToInt()
        val cacheKey = "${state.imagePath}|${state.hero?.artSongId}|${state.hero?.id}|$widthPx|$natural|$zone|${pageBg.toArgb()}"
        val pre by produceState<PortraitHero?>(if (live) null else PortraitHeroCache.get(cacheKey), state.imagePath, state.hero, widthPx, natural, zone, live, pageBg) {
            if (live) { value = null; return@produceState }
            if (value != null && PortraitHeroCache.get(cacheKey) === value) return@produceState
            value = withContext(Dispatchers.IO) {
                heroSource(context, state, widthPx)?.let {
                    portraitHero(it, pageBg.toArgb(), widthPx, imageHeightPx, zoneHeightPx, state.imagePath != null, density.density)
                }?.also { PortraitHeroCache.put(cacheKey, it) }
            }
        }
        Box(
            Modifier
                // Laid out as a square, drawn taller: the overflow runs under the items below.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, (constraints.maxWidth * zone).roundToInt()))
                    layout(constraints.maxWidth, constraints.maxWidth) { placeable.place(0, 0) }
                }
                .background(pageBg)
                .graphicsLayer {
                    val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 1e6f
                    val past = scrolled / (size.height / zone)
                    translationY = scrolled * (1f - HeroSpeed)
                    val blur = scrollBlur(past)
                    if (live && blur > 0f) {
                        val r = 40.dp.toPx() * blur
                        // Clamp keeps the top and side edges solid (the image runs off-screen there).
                        renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    }
                    alpha = 1f - ((past - HeroFadeStart) / (HeroFadeEnd - HeroFadeStart)).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(HeroFadeOut, blendMode = BlendMode.DstIn)
                },
        ) {
            val custom = state.imagePath
            val image: @Composable () -> Unit = {
                Box(Modifier.fillMaxWidth().height(imageHeight).background(pageBg)) {
                    if (custom != null) {
                        AsyncImage(File(custom), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                    } else {
                        state.hero?.let { ArtworkImage(it.artSongId, it.id, Modifier.fillMaxSize()) }
                    }
                }
            }
            image()
            if (!live) {
                pre?.let { p ->
                    val canvas = remember(p) { p.canvas.asImageBitmap() }
                    val blur6 = remember(p) { p.blur6.asImageBitmap() }
                    val blur26 = remember(p) { p.blur26.asImageBitmap() }
                    val blur40 = remember(p) { p.blur40.asImageBitmap() }
                    Image(canvas, null, Modifier.fillMaxSize())
                    Image(blur6, null, Modifier.fillMaxSize().maskedBy(listOf(smoothRamp(HeroBlurLayers.first().second, HeroBlurLayers.first().third, up = true))))
                    Image(blur26, null, Modifier.fillMaxSize().maskedBy(listOf(smoothRamp(HeroBlurLayers.last().second, HeroBlurLayers.last().third, up = true))))
                    // The scroll blur: a fully blurred copy faded in as the page scrolls.
                    Image(blur40, null, Modifier.fillMaxSize().graphicsLayer {
                        val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 1e6f
                        alpha = scrollBlur(scrolled / (size.height / zone))
                    })
                }
            }
            if (live) {
                HeroBlurLayers.forEach { (radius, from, to) ->
                    Box(Modifier.fillMaxSize().progressiveBlur(radius, smoothRamp(from, to, up = true))) {
                        image()
                        if (extension > 0.dp) {
                            // The image's last rows stretched down over the space below it (an extension, not a mirror).
                            Box(Modifier.offset(y = imageHeight).fillMaxWidth().height(with(LocalDensity.current) { HeroStretchRows.toDp() }).graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                scaleY = extension.toPx() / HeroStretchRows
                            }.clipToBounds()) {
                                // Bottom-aligned and allowed to overflow upward: a requiredHeight box would be centred, and show the middle.
                                Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Bottom, unbounded = true)) { image() }
                            }
                        }
                    }
                }
            }
        }
        val logo = state.logoPath
        if (logo != null && !leftAligned) {
            AsyncImage(
                model = File(logo),
                contentDescription = state.name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 52.dp, end = 52.dp, bottom = 16.dp)
                    .widthIn(max = 288.dp)
                    .heightIn(max = 96.dp),
            )
        } else {
            Text(
                state.name,
                Modifier.align(if (leftAligned) Alignment.BottomStart else Alignment.BottomCenter)
                    .padding(start = if (leftAligned) 20.dp else 24.dp, end = 24.dp, bottom = 20.dp),
                style = MaterialTheme.typography.displaySmallEmphasized.merge(heroNameStyle(rememberHeroInk(state))), textAlign = if (leftAligned) TextAlign.Start else TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What the artist name, Play button and the Info / Favourite glyphs are drawn in: a near-white tinted with the picture's colour over a
 * dark picture, a near-black tinted with it over a light one, judged from the band of the image behind them. The shadow is black under
 * the light ink and a slate grey under the dark one. Until the picture is read it is plain white or black by theme.
 */
@Immutable
internal class HeroInk(val color: Color, val shadow: Color)

@Composable
internal fun rememberHeroInk(state: ArtistUiState): HeroInk {
    val context = LocalContext.current
    val bg = MaterialTheme.colorScheme.surface
    // The same band and backdrop in both orientations, so turning the phone never changes the colour: the bottom rows of the picture,
    // and the page colour it fades into.
    val from = 0.65f
    val to = 1f
    val averageKey = "${state.imagePath}|${state.hero?.artSongId}|${state.hero?.id}|${bg.toArgb()}"
    val average by produceState<Color?>(HeroInkColorCache.get(averageKey), state.imagePath, state.hero, from, to) {
        HeroInkColorCache.get(averageKey)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) { heroSource(context, state, 64)?.let { bandAverage(it, from, to, bg.toArgb()) } }?.also { HeroInkColorCache.put(averageKey, it) }
    }
    // With a custom title logo the ink is the logo's own dominant colour (the name isn't shown then).
    val logoColor by produceState<Color?>(state.logoPath?.let { HeroInkColorCache.get("logo|$it") }, state.logoPath) {
        value = state.logoPath?.let { path -> withContext(Dispatchers.IO) { logoDominantColor(path) }?.also { HeroInkColorCache.put("logo|$path", it) } }
    }
    val avg = average
    // The name sits at about 92% of the square hero, inside the taller image's fade zone.
    // Use that fade here too: a fixed 50% backdrop made light-mode text disappear over dark covers.
    val natural = (if (state.imagePath != null) state.imageRatio else 1f).coerceIn(1f, 1.5f)
    val nameInZone = 0.92f / maxOf(HeroImageRatio, natural)
    val fade = ((nameInZone - HeroFadeFrom) / (1f - HeroFadeFrom)).coerceIn(0f, 1f)
    val backdrop = avg?.let { lerp(it, bg, fade * fade * (3f - 2f * fade)) } ?: bg
    // In dark mode the glass behind the glyphs and the page around the hero are dark: the ink stays light there (the shadow carries a
    // bright picture); only light mode may use a dark ink.
    val themeDark = MaterialTheme.colorScheme.background.red < 0.5f
    val darkInk = !themeDark && contrast(Color.Black, backdrop) > contrast(Color.White, backdrop)
    val logo = if (state.logoPath != null) logoColor else null
    val target = if (logo != null) (if (themeDark) lifted(logo, 0.8f) else notBlack(logo)) else if (avg == null) (if (darkInk) HeroInkDark else Color.White) else {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(avg.toArgb(), hsv)
        // Keep the picture's hue: a pastel of white over dark backdrops, a deep shade of black over light ones.
        val tinted = if (darkInk) Color.hsv(hsv[0], hsv[1].coerceAtMost(0.8f), HeroInkDarkest)
        else Color.hsv(hsv[0], (hsv[1] * 0.5f).coerceAtMost(0.4f), 1f)
        // Never let the tint cost legibility: fall back to plain white / black when it would blend in.
        if (contrast(tinted, backdrop) >= 4.5f) tinted else if (darkInk) HeroInkDark else Color.White
    }
    val lightImage = if (logo != null) !themeDark && contrast(Color.Black, logo) < contrast(Color.White, logo) else darkInk
    val color by animateColorAsState(target, label = "hero-ink")
    val shadow by animateColorAsState((if (lightImage) Color(0xFF6B7280) else Color.Black).copy(alpha = 0.3f), label = "hero-shadow")
    return HeroInk(color, shadow)
}

/** The commonest colour among the opaque pixels of the logo at [path]: 4-bit buckets, averaged inside the winning one. */
private fun logoDominantColor(path: String): Color? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 96) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val counts = HashMap<Int, IntArray>()
    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
        val px = bitmap.getPixel(x, y)
        if ((px ushr 24) < 160) continue
        val key = ((px shr 20) and 0xF shl 8) or ((px shr 12) and 0xF shl 4) or ((px shr 4) and 0xF)
        val acc = counts.getOrPut(key) { IntArray(4) }
        acc[0]++; acc[1] += (px shr 16) and 0xFF; acc[2] += (px shr 8) and 0xFF; acc[3] += px and 0xFF
    }
    val best = counts.values.maxByOrNull { it[0] } ?: return null
    return Color(best[1] / best[0], best[2] / best[0], best[3] / best[0])
}

/** The darkest the ink may get (HSV value): a deep shade, never plain black. */
private const val HeroInkDarkest = 0.28f
private val HeroInkDark = Color.hsv(0f, 0f, HeroInkDarkest)

/** [c] with its HSV brightness raised to at least [minValue], keeping hue and saturation. */
private fun lifted(c: Color, minValue: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    return if (hsv[2] >= minValue) c else Color.hsv(hsv[0], hsv[1], minValue)
}

/** [c] lifted to at least [HeroInkDarkest] brightness, keeping its hue and saturation. */
private fun notBlack(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    return if (hsv[2] >= HeroInkDarkest) c else Color.hsv(hsv[0], hsv[1], HeroInkDarkest)
}

private fun relativeLuminance(c: Color): Float {
    fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
}

/** WCAG contrast ratio, 1..21. */
private fun contrast(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Mean colour of rows [from]..[to] (fractions of the height) of [bitmap], transparent pixels counted as [background]. */
private fun bandAverage(bitmap: Bitmap, from: Float, to: Float, background: Int): Color {
    val y0 = (bitmap.height * from).toInt().coerceIn(0, bitmap.height - 1)
    val y1 = (bitmap.height * to).toInt().coerceIn(y0 + 1, bitmap.height)
    val bgColor = Color(background)
    var r = 0f; var g = 0f; var b = 0f
    var n = 0
    for (y in y0 until y1) for (x in 0 until bitmap.width) {
        val c = Color(bitmap.getPixel(x, y))
        val a = c.alpha
        r += c.red * a + bgColor.red * (1f - a)
        g += c.green * a + bgColor.green * (1f - a)
        b += c.blue * a + bgColor.blue * (1f - a)
        n++
    }
    return if (n == 0) bgColor else Color(r / n, g / n, b / n)
}

/** The artist name over the hero in [ink]'s colour, with its shadow. */
internal fun heroNameStyle(ink: HeroInk): TextStyle = TextStyle(color = ink.color, shadow = Shadow(ink.shadow, Offset(0f, 2f), 10f))

private val HeroInkColorCache = android.util.LruCache<String, Color>(16)

private val HeroFadeOut = smoothRamp(HeroFadeFrom, 1f, up = false)

/** 0..1 scroll blur of the hero; [past] is how far the page has scrolled, in hero heights. */
private fun scrollBlur(past: Float) = ((past - HeroBlurStart) / (HeroBlurEnd - HeroBlurStart)).coerceIn(0f, 1f)

private val PortraitHeroCache = android.util.LruCache<String, PortraitHero>(3)

private class PortraitHero(val canvas: Bitmap, val blur6: Bitmap, val blur26: Bitmap, val blur40: Bitmap)

/** The artist's picture (a custom photo or the newest cover) decoded to about [widthPx]. */
private suspend fun heroSource(context: Context, state: ArtistUiState, widthPx: Int): Bitmap? = state.imagePath?.let { path ->
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= widthPx) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
} ?: state.hero?.let { ArtworkDecoder.decode(context, it.artSongId, it.id, widthPx) }

/**
 * The hero as one canvas [widthPx] x [zoneHeightPx]: the picture cropped to its natural shape (custom photos top-aligned), its last
 * rows stretched down over the extension, plus the 6 / 26 / 40 dp blurred copies the live layers would have drawn.
 */
private fun portraitHero(source: Bitmap, background: Int, widthPx: Int, imageHeightPx: Int, zoneHeightPx: Int, topAligned: Boolean, density: Float): PortraitHero {
    val canvas = Bitmap.createBitmap(widthPx, zoneHeightPx, Bitmap.Config.ARGB_8888)
    Canvas(canvas).apply {
        drawColor(background)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val wanted = widthPx / imageHeightPx.toFloat()
        val have = source.width / source.height.toFloat()
        val crop = if (have > wanted) {
            val w = (source.height * wanted).toInt()
            val x = (source.width - w) / 2
            Rect(x, 0, x + w, source.height)
        } else {
            val h = (source.width / wanted).toInt()
            val y = if (topAligned) 0 else (source.height - h) / 2
            Rect(0, y, source.width, y + h)
        }
        drawBitmap(source, crop, Rect(0, 0, widthPx, imageHeightPx), paint)
        if (zoneHeightPx > imageHeightPx) {
            val last = Bitmap.createBitmap(canvas, 0, imageHeightPx - 3, widthPx, 3)
            drawBitmap(last, null, Rect(0, imageHeightPx, widthPx, zoneHeightPx), paint)
        }
    }
    return PortraitHero(
        canvas,
        blurredCopy(canvas, HeroBlurLayers.first().first.value * density),
        blurredCopy(canvas, HeroBlurLayers.last().first.value * density),
        blurredCopy(canvas, 40f * density),
    )
}
internal fun Modifier.maskedBy(masks: List<Brush>): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent { drawContent(); masks.forEach { drawRect(it, blendMode = BlendMode.DstIn) } }

/** [source] blurred by a gaussian [sigmaPx] on the CPU, at 1/4 size; the picture is treated as opaque. */
internal fun blurredCopy(source: Bitmap, sigmaPx: Float): Bitmap {
    val q = 4
    val w = (source.width / q).coerceAtLeast(1)
    val h = (source.height / q).coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(source, w, h, true)
    val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
    val tmp = IntArray(maxOf(w, h))
    for (r in 0 until h) Gaussian.line(px, r * w, 1, w, sigmaPx / q, tmp)
    for (c in 0 until w) Gaussian.line(px, c, w, h, sigmaPx / q, tmp)
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

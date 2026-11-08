@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val Directions = 240

/**
 * A star-shaped outline as its radius in [Directions] evenly spaced directions from the centre (1 = half the shape's width).
 * Blending two of these is a plain per-direction lerp, which is how the button morphs between shapes smoothly even when a morph
 * is interrupted halfway (the blend starts from whatever is on screen, not from a shape).
 */
private fun radialProfile(polygon: RoundedPolygon): FloatArray {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    for (cubic in polygon.cubics) for (i in 0 until 10) {
        val t = i / 10f
        val u = 1f - t
        // Cubic Bézier through its anchors and controls.
        xs += u * u * u * cubic.anchor0X + 3 * u * u * t * cubic.control0X + 3 * u * t * t * cubic.control1X + t * t * t * cubic.anchor1X
        ys += u * u * u * cubic.anchor0Y + 3 * u * u * t * cubic.control0Y + 3 * u * t * t * cubic.control1Y + t * t * t * cubic.anchor1Y
    }
    val cx = (xs.min() + xs.max()) / 2f
    val cy = (ys.min() + ys.max()) / 2f
    val half = max(xs.max() - xs.min(), ys.max() - ys.min()) / 2f
    val n = xs.size
    val out = FloatArray(Directions)
    for (k in 0 until Directions) {
        val dx = cos(2.0 * PI * k / Directions).toFloat()
        val dy = sin(2.0 * PI * k / Directions).toFloat()
        var best = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            // Ray from the centre along (dx, dy) against the segment i -> j.
            val ax = xs[i] - cx; val ay = ys[i] - cy
            val ex = xs[j] - xs[i]; val ey = ys[j] - ys[i]
            val den = dx * ey - dy * ex
            if (abs(den) < 1e-9f) continue
            val t = (ax * ey - ay * ex) / den
            val u = (ax * dy - ay * dx) / den
            if (t > 0f && u in 0f..1f) best = max(best, t)
        }
        out[k] = best / half
    }
    // A ray that grazes a vertex can miss every segment; take the neighbours' radius there.
    for (k in 0 until Directions) if (out[k] <= 0f) {
        var a = (k + Directions - 1) % Directions
        while (out[a] <= 0f && a != k) a = (a + Directions - 1) % Directions
        var b = (k + 1) % Directions
        while (out[b] <= 0f && b != k) b = (b + 1) % Directions
        out[k] = (out[a] + out[b]) / 2f
    }
    return out
}

private val CircleProfile by lazy { radialProfile(MaterialShapes.Circle) }
private val CookieProfile by lazy { radialProfile(MaterialShapes.Cookie9Sided) }

/** A snapshot of a morph ([radii] is copied): a new instance is set on the layer every frame, as Compose caches a shape's outline. */
@Stable
private class MorphingShape(radii: FloatArray) : Shape {
    private val radii = radii.copyOf()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = radii
        val half = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        for (k in 0 until Directions) {
            val a = 2.0 * PI * k / Directions
            val x = cx + cos(a).toFloat() * r[k] * half
            val y = cy + sin(a).toFloat() * r[k] * half
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Now Playing's big play/pause button, in the cover's colours; the glyph is cut out so the cover shows through. */
@Composable
fun PlayButton(playing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, buffering: Boolean = false) {
    val c = LocalCoverColors.current
    MorphingPlayButton(playing, onToggle, 96.dp, 44.dp, c.accent, c.onAccent, cutOut = true, modifier = modifier, buffering = buffering)
}

/**
 * A play/pause button: a circle while paused, the cookie while playing, and a rounded square while held. The change
 * between them uses the standard icon-button effects animation and blends from whatever shape is showing. With [cutOut] the glyph is cut out of the container
 * (an offscreen layer lets it clear the container), so what is behind shows through; it morphs between play and pause.
 */
@Composable
fun MorphingPlayButton(
    playing: Boolean,
    onToggle: () -> Unit,
    size: Dp,
    glyph: Dp,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    cutOut: Boolean = false,
    buffering: Boolean = false,
) {
    val density = LocalDensity.current
    val pressedCorners = IconButtonDefaults.toggleableShapes().pressedShape as CornerBasedShape
    val pressedRadius = with(density) {
        val side = size.toPx()
        (pressedCorners.topStart.toPx(Size(side, side), density) + 2.dp.toPx()) / (side / 2f)
    }
    val pressedProfile = remember(pressedRadius) {
        radialProfile(RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(pressedRadius)))
    }
    val shapeMotion = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = when {
        pressed -> pressedProfile
        playing -> CookieProfile
        else -> CircleProfile
    }
    val blend = remember { Animatable(1f) }
    // Born in the shape that fits the state it is created in. (It used to start as a circle and be corrected afterwards by plain
    // array writes, which redraw nothing: reopening the player while playing left a circle on screen until something else
    // invalidated the button.)
    val from = remember { target.copyOf() }
    val to = remember { target.copyOf() }
    val shown = remember { target.copyOf() }
    LaunchedEffect(target) {
        if (to.contentEquals(target)) return@LaunchedEffect // already there: the state it was created in
        shown.copyInto(from) // start from what is on screen now, so an interrupted morph never jumps
        target.copyInto(to)
        blend.snapTo(0f)
        blend.animateTo(1f, shapeMotion)
    }
    FilledIconToggleButton(
        checked = playing,
        onCheckedChange = { onToggle() },
        // The container is a plain rectangle; the layer below clips it to the morphing shape (which also clips the ripple and the touches).
        shape = RectangleShape,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = containerColor, contentColor = contentColor, checkedContainerColor = containerColor, checkedContentColor = contentColor,
        ),
        interactionSource = interaction,
        modifier = modifier.size(size).graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            val t = blend.value
            for (k in shown.indices) shown[k] = max(0f, from[k] + (to[k] - from[k]) * t)
            shape = MorphingShape(shown)
            clip = true
        },
    ) {
        // Buffering shows after a beat, so the usual moment between songs does not flash it.
        var waiting by remember { mutableStateOf(false) }
        LaunchedEffect(buffering) {
            if (buffering) { delay(BufferingDelayMs); waiting = true } else waiting = false
        }
        // 0 = the glyph, 1 = the indicator: the glyph's two pieces gather into one round blob (as the play morphs into the pause),
        // and the indicator takes over from the blob, so nothing snaps in or out.
        val gather = animateFloatAsState(if (waiting) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 260f), label = "buffering").value.coerceIn(0f, 1f)
        val handoff = ((gather - 0.75f) / 0.25f).coerceIn(0f, 1f)
        Box(contentAlignment = Alignment.Center) {
            PlayPauseIcon(playing, glyph, blendMode = if (cutOut) BlendMode.Clear else BlendMode.SrcOver, loading = gather)
            if (gather > 0.6f) {
                LoadingIndicator(
                    Modifier
                        // The indicator draws its shape at about 0.8 of its box; the box is sized so the shape matches the disc the glyph gathers into.
                        .size(glyph * 1.3f)
                        .then(if (cutOut) Modifier.cutOutOf { handoff } else Modifier.graphicsLayer { alpha = handoff }),
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * Draws this node as a cut-out of what is under it (the button's container), like the play glyph: the node is drawn in a layer that
 * is subtracted (DstOut) at [alpha]. (A graphicsLayer alpha cannot do this: it would put the node in a layer of its own, which
 * has nothing under it to cut.)
 */
private fun Modifier.cutOutOf(alpha: () -> Float): Modifier = drawWithContent {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply { blendMode = BlendMode.DstOut; this.alpha = alpha() }
        canvas.saveLayer(Rect(Offset.Zero, size), paint)
        drawContent()
        canvas.restore()
    }
}

private const val BufferingDelayMs = 300L

/**
 * Play and pause as one glyph that morphs: the triangle is split in two pieces that become the two bars (each piece has four
 * corners that travel to the matching corners of its bar). Corners are rounded by stroking the pieces with a round join.
 */
@Composable
fun PlayPauseIcon(
    playing: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    blendMode: BlendMode = BlendMode.SrcOver,
    /** 0..1: the two pieces gather into one round blob (the buffering indicator takes over from it) and fade out at the end. */
    loading: Float = 0f,
) {
    val morph = animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 260f), label = "play-pause")
    val path = remember { Path() }
    Canvas(modifier.size(size).semantics { contentDescription = if (playing) "Pause" else "Play"; role = Role.Image }) {
        val t = morph.value
        val rounding = (0.055f + (BlobRounding - 0.055f) * loading) * this.size.minDimension
        // The pieces are drawn opaque into one layer, which is composited at the fade (and, for a cut-out, subtracted: Clear ignores
        // alpha), so the fill and the stroke of a piece, and the two pieces, never add up where they overlap.
        val fade = 1f - ((loading - 0.75f) / 0.25f).coerceIn(0f, 1f)
        fun piece(play: FloatArray, pause: FloatArray) {
            path.reset()
            for (i in 0 until 4) {
                val gx = BlobCorners[2 * i]
                val gy = BlobCorners[2 * i + 1]
                val x = ((play[2 * i] + (pause[2 * i] - play[2 * i]) * t) * (1f - loading) + gx * loading) * this.size.width
                val y = ((play[2 * i + 1] + (pause[2 * i + 1] - play[2 * i + 1]) * t) * (1f - loading) + gy * loading) * this.size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, tint, style = Fill)
            drawPath(path, tint, style = Stroke(rounding * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        }
        if (fade > 0f) drawIntoCanvas { canvas ->
            val paint = Paint().apply { this.blendMode = if (blendMode == BlendMode.Clear) BlendMode.DstOut else blendMode; alpha = fade }
            canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, this.size), paint)
            piece(PlayLeft, PauseLeft)
            piece(PlayRight, PauseRight)
            canvas.restore()
        }
    }
}

/** Where the four corners of each piece meet when gathered (a tiny square at the centre, rounded into a disc by [BlobRounding]). */
private val BlobCorners = floatArrayOf(0.47f, 0.47f, 0.53f, 0.47f, 0.53f, 0.53f, 0.47f, 0.53f)
private const val BlobRounding = 0.21f

// Four corners per piece (top-left, top-right, bottom-right, bottom-left), in 0..1 of the icon. Inset by the corner rounding,
// which the stroke adds back.
private val PlayLeft = floatArrayOf(0.31f, 0.23f, 0.52f, 0.36f, 0.52f, 0.64f, 0.31f, 0.77f)
private val PlayRight = floatArrayOf(0.52f, 0.36f, 0.74f, 0.495f, 0.74f, 0.505f, 0.52f, 0.64f)
private val PauseLeft = floatArrayOf(0.30f, 0.24f, 0.42f, 0.24f, 0.42f, 0.76f, 0.30f, 0.76f)
private val PauseRight = floatArrayOf(0.58f, 0.24f, 0.70f, 0.24f, 0.70f, 0.76f, 0.58f, 0.76f)

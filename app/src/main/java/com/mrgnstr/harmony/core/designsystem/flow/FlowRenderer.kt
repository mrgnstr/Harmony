package com.mrgnstr.harmony.core.designsystem.flow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.mrgnstr.harmony.R
import com.mrgnstr.harmony.core.artwork.StackBlur
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The "flow" background: a CPU port, at about 1/8 resolution, of the pipeline described by a Metal reconstruction of Apple Music's
 * animated artwork background (see docs/NOTES.md "Flow background"). Per frame:
 *  1. three copies of the cover, each stretched over the whole target, spun on their own clocks (rotation both before and after a
 *     per-instance offset, so they orbit as well as turn), later ones overwriting earlier ones, each saturated;
 *  2. a gaussian blur whose sigma is 4.5% of the target diagonal;
 *  3. a 41x41 mesh that morphs between two shapes (smoothstep ping-pong) drawn with the blurred picture as its texture, sampled over
 *     the middle 80% only;
 *  4. a grade fitted to Apple Music for Android (see the constants): saturation, scale and offset per mode, clamped to [0.07, 0.97].
 * Everything is drawn small and upscaled with bilinear filtering by the caller, which is what makes the blur cheap.
 */
data class FlowParams(
    val dark: Boolean = true,
    /** Which of the five morph meshes (0..4). */
    val mesh: Int = 0,
    /** Apple's "animation interval": rotation periods are 120/90/70 times it (seconds), the morph cycle 7 times it. 0.5 by default. */
    val interval: Float = 0.5f,
    /**
     * Size of the colour patches: scales the mesh, the blur and (inversely) the part of the blurred picture the mesh shows. 1 is the
     * Metal reconstruction's own size; Apple Music for Android measured about 1.25x bigger patches (autocorrelation length 0.256 vs 0.205 of
     * the screen width), which costs some contrast, so the grade below is fitted at 1.25.
     */
    val scale: Float = 1.25f,
    /**
     * How fast the mesh wanders between the four distinct meshes (0 = off: only the built-in morph, which moves vertices by just 0.05 of a
     * screen half-width on average, so the sharp spots where the mesh squeezes the picture stay put). 1 = one hand-over every 20 s at the
     * default interval. This is an addition: nothing in the reconstruction does it.
     */
    val drift: Float = 1f,
)

/** The packed meshes (res/raw/flow_meshes.bin, made by tools/gen_flow_meshes.py): shared topology, per mesh two clip-space shapes. */
internal class FlowMeshData private constructor(
    val vertexCount: Int,
    val uv: FloatArray,
    val indices: ShortArray,
    val from: List<FloatArray>,
    val to: List<FloatArray>,
) {
    companion object {
        @Volatile private var cached: FlowMeshData? = null

        fun get(context: Context): FlowMeshData = cached ?: synchronized(this) {
            cached ?: load(context).also { cached = it }
        }

        private fun load(context: Context): FlowMeshData {
            val bytes = context.resources.openRawResource(R.raw.flow_meshes).use { it.readBytes() }
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(b.int == 0x574F4C46) { "not a flow mesh file" }
            val meshCount = b.int
            val n = b.int
            val indexCount = b.int
            fun floats(count: Int) = FloatArray(count).also { b.asFloatBuffer().get(it); b.position(b.position() + count * 4) }
            val uv = floats(n * 2)
            val indices = ShortArray(indexCount).also { b.asShortBuffer().get(it); b.position(b.position() + indexCount * 2) }
            val from = ArrayList<FloatArray>()
            val to = ArrayList<FloatArray>()
            repeat(meshCount) { from += floats(n * 2); to += floats(n * 2) }
            return FlowMeshData(n, uv, indices, from, to)
        }
    }
}

/**
 * One background for one cover at one target size ([width] x [height] is the small render size; [fullWidth] x [fullHeight] the size
 * it will be shown at, which only sets the blur). [render] is synchronized (renders never overlap); [front] is what to draw.
 */
internal class FlowRenderer(
    private val meshes: FlowMeshData,
    private val art: Bitmap,
    val width: Int,
    val height: Int,
    fullWidth: Int,
    fullHeight: Int,
) {
    private val rotation = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val rotationCanvas = Canvas(rotation)
    private val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(width * height)
    // Private to the render thread: the mesh is drawn here and graded. What is shown ([front]) is always a NEW bitmap made from the result:
    // the screen draws a bitmap a frame or two after it was handed over (the render thread records it, the GPU reads it later), so reusing
    // two bitmaps let the next frame be cleared and half drawn while the GPU still read the last one, which flashed black and partial frames.
    private val work = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /** The last finished frame. */
    @Volatile var front: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        private set

    private val artPaint = Paint().apply { isFilterBitmap = false }
    private val darkCopies = saturation(DarkCopySaturation)
    private val lightCopies = saturation(LightCopySaturation)
    private val meshPaint = Paint().apply { isFilterBitmap = true }
    private val matrix = Matrix()
    private val clipLinear = Matrix()
    private val clipToPixel = Matrix().apply { setScale(width / 2f, -height / 2f); postTranslate(width / 2f, height / 2f) }
    private val imageToClip = Matrix().apply { setScale(2f / art.width, -2f / art.height); postTranslate(-1f, 1f) }
    private val values = FloatArray(9)

    private val diagonalSigma = floor(hypot(fullWidth.toDouble(), fullHeight.toDouble()) * SigmaPerDiagonal) * width / fullWidth
    private var blurRadius = 1
    private val verts = FloatArray(meshes.vertexCount * 2)
    // Texture coordinates in pixels of the blurred bitmap: the middle 0.8 / scale of it (set by [applyScale]).
    private val texs = FloatArray(meshes.vertexCount * 2)
    private var appliedScale = Float.NaN

    private fun applyScale(scale: Float) {
        if (scale == appliedScale) return
        appliedScale = scale
        // Three box passes of width n have variance (n^2 - 1) / 4.
        val sigma = max(0.01, diagonalSigma * scale)
        blurRadius = max(1, ((sqrt(4 * sigma * sigma + 1) - 1) / 2).roundToInt())
        val squeeze = 0.8f / scale
        for (i in texs.indices) texs[i] = ((meshes.uv[i] - 0.5f) * squeeze + 0.5f) * (if (i % 2 == 0) width else height)
    }

    /** Draws the frame for [time] seconds and publishes it as [front] (a new bitmap each time). */
    // Synchronized: a cancelled loop's render keeps running to its end while the next loop (a new fps, say) starts another, and two renders
    // on one set of bitmaps and arrays garble each other.
    @Synchronized
    fun render(time: Double, params: FlowParams) {
        applyScale(params.scale)
        spin(time, params)
        rotation.getPixels(pixels, 0, width, 0, 0, width, height)
        StackBlur.blur(pixels, width, height, blurRadius)
        blurred.setPixels(pixels, 0, width, 0, 0, width, height)

        warp(work, time, params)
        grade(work, params)
        front = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun spin(time: Double, params: FlowParams) {
        val interval = params.interval
        artPaint.colorFilter = if (params.dark) darkCopies else lightCopies
        rotationCanvas.drawColor(Color.BLACK)
        for (i in 0 until 3) {
            val period = interval * Periods[i]
            val theta = (2 * PI * ((time / period) % 1.0)).toFloat()
            val c = cos(theta)
            val s = sin(theta)
            // R * T * R, with R the clip-space rotation: the linear part is R twice, the offset is R * t.
            val c2 = cos(2 * theta)
            val s2 = sin(2 * theta)
            val tx = Offsets[i * 2]
            val ty = Offsets[i * 2 + 1]
            values.fill(0f)
            values[0] = c2; values[1] = s2; values[2] = c * tx + s * ty
            values[3] = -s2; values[4] = c2; values[5] = -s * tx + c * ty
            values[8] = 1f
            clipLinear.setValues(values)
            matrix.set(imageToClip)
            matrix.postConcat(clipLinear)
            matrix.postConcat(clipToPixel)
            rotationCanvas.drawBitmap(art, matrix, artPaint)
        }
    }

    private fun warp(out: Bitmap, time: Double, params: FlowParams) {
        val count = meshes.from.size
        val start = params.mesh.coerceIn(0, count - 1)
        // Mesh 3 is a copy of mesh 2 in the data, so it is skipped when wandering.
        var a = start
        var b = start
        var s = 0f
        if (params.drift > 0f) {
            val step = time / (params.interval * DriftSegment / params.drift)
            val k = floor(step).toInt()
            val f = (step - floor(step)).toFloat()
            a = Wander[Math.floorMod(Wander.indexOf(start.takeIf { it in Wander } ?: 0) + k, Wander.size)]
            b = Wander[Math.floorMod(Wander.indexOf(start.takeIf { it in Wander } ?: 0) + k + 1, Wander.size)]
            s = f * f * (3 - 2 * f)
        }
        val period = params.interval * MeshWarpPeriod // Apple's meshWarpTimeScale; the full ping-pong is twice it
        val angle = acos(sin(PI * time / period).coerceIn(-1.0, 1.0))
        val q = angle / PI
        val w = (q * q * (3 - 2 * q)).toFloat()
        val fromA = meshes.from[a]; val toA = meshes.to[a]
        val fromB = meshes.from[b]; val toB = meshes.to[b]
        val halfW = width / 2f
        val halfH = height / 2f
        for (i in 0 until meshes.vertexCount * 2) {
            val pa = fromA[i] + (toA[i] - fromA[i]) * w
            val pb = if (s == 0f) pa else fromB[i] + (toB[i] - fromB[i]) * w
            val c = (pa + (pb - pa) * s) * params.scale
            verts[i] = if (i % 2 == 0) (c + 1f) * halfW else (1f - c) * halfH
        }
        // A new shader each frame: the bitmap behind it was just rewritten.
        meshPaint.shader = BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, texs, 0, null, 0,
            meshes.indices, 0, meshes.indices.size, meshPaint,
        )
    }

    private fun grade(out: Bitmap, params: FlowParams) {
        out.getPixels(pixels, 0, width, 0, 0, width, height)
        val scale = if (params.dark) DarkScale else LightScale
        val offset = if (params.dark) DarkOffset else LightOffset
        val saturation = if (params.dark) DarkGradeSaturation else LightGradeSaturation
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val l = 0.30f * r + 0.59f * g + 0.11f * b
            pixels[i] = Color.rgb(
                (((l + saturation * (r - l)) * scale + offset).coerceIn(Floor, Top) * 255f + 0.5f).toInt(),
                (((l + saturation * (g - l)) * scale + offset).coerceIn(Floor, Top) * 255f + 0.5f).toInt(),
                (((l + saturation * (b - l)) * scale + offset).coerceIn(Floor, Top) * 255f + 0.5f).toInt(),
            )
        }
    }

    private companion object {
        /** Gaussian sigma as a fraction of the target's diagonal, in full-size pixels. */
        const val SigmaPerDiagonal = 0.04539470697716646
        /** Seconds per revolution at interval 1 (Apple: interval x 120 / 90 / 70). */
        val Periods = floatArrayOf(120f, 90f, 70f)
        /** Clip-space offsets of the three instances. */
        val Offsets = floatArrayOf(0f, 0f, -0.5f, 0.7f, -0.95f, -0.7f)
        /** Apple's meshWarpTimeScale = interval x 3.5. */
        const val MeshWarpPeriod = 3.5f
        /** Seconds per hand-over to the next mesh at interval 1 and drift 1 (so 20 s at the default 0.5). */
        const val DriftSegment = 40f
        /** The distinct meshes, in the order they are visited. */
        val Wander = intArrayOf(0, 1, 2, 4)
        // The grade is fitted to Apple Music for Android's lyrics background on an 11T over 8 different covers, dark and light, at patch scale 1.25
        // (docs/NOTES.md "Flow background"; the Metal reconstruction's own scrims were far too bright). Saturation of the copies before the blur,
        // then after it, then scale and offset, then Apple's [0.07, 0.97] clamp (its darkest channel never goes below ~18/255). Light mode is
        // not a white scrim over dark: it is lifted more and a little more saturated.
        const val DarkCopySaturation = 2.0f
        const val LightCopySaturation = 2.0f
        const val DarkGradeSaturation = 1.0f
        const val LightGradeSaturation = 1.25f
        const val DarkScale = 0.60f
        const val DarkOffset = 0.0f
        const val LightScale = 0.60f
        const val LightOffset = 0.10f
        const val Floor = 0.07f
        const val Top = 0.97f

        /** Saturation about the 0.30 / 0.59 / 0.11 luma (not the Rec.709 weights ColorMatrix.setSaturation uses). */
        fun saturation(s: Float): ColorMatrixColorFilter {
            val m = 1 - s
            return ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        m * 0.30f + s, m * 0.59f, m * 0.11f, 0f, 0f,
                        m * 0.30f, m * 0.59f + s, m * 0.11f, 0f, 0f,
                        m * 0.30f, m * 0.59f, m * 0.11f + s, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }
}

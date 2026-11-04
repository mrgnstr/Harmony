package com.mrgnstr.harmony.core.designsystem.icons

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.R
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One Material Symbol (see the generated [Symbols]). */
@Immutable
@JvmInline
value class Symbol(val char: Char)

/**
 * A glyph's outline at six instances of the variable font (fill 0/1 × weight 100/400/700). The instances share their points,
 * so any fill and weight in between is a per-point interpolation, which is what the font's own axes do.
 */
private class Glyph(val ends: IntArray, val onCurve: BooleanArray, val instances: Array<ShortArray>) {
    private val xs = FloatArray(onCurve.size)
    private val ys = FloatArray(onCurve.size)

    /** Fills [path] with the outline at [fill] (0…1) and [weight] (100…700), scaled so the 960-unit em becomes [sizePx]. */
    fun outline(path: Path, fill: Float, weight: Float, sizePx: Float) {
        val f = fill.coerceIn(0f, 1f)
        val w = weight.coerceIn(100f, 700f)
        // Weight is piecewise linear between the 100, 400 and 700 masters.
        val (lo, t) = if (w <= 400f) 0 to (w - 100f) / 300f else 1 to (w - 400f) / 300f
        val outLo = instances[lo]; val outHi = instances[lo + 1]
        val inLo = instances[3 + lo]; val inHi = instances[3 + lo + 1]
        val scale = sizePx / UnitsPerEm
        for (i in onCurve.indices) {
            val x0 = outLo[2 * i] + (outHi[2 * i] - outLo[2 * i]) * t
            val y0 = outLo[2 * i + 1] + (outHi[2 * i + 1] - outLo[2 * i + 1]) * t
            val x1 = inLo[2 * i] + (inHi[2 * i] - inLo[2 * i]) * t
            val y1 = inLo[2 * i + 1] + (inHi[2 * i + 1] - inLo[2 * i + 1]) * t
            xs[i] = (x0 + (x1 - x0) * f) * scale
            ys[i] = (UnitsPerEm - (y0 + (y1 - y0) * f)) * scale // font y points up
        }
        path.reset()
        var start = 0
        for (end in ends) {
            contour(path, start, end)
            start = end + 1
        }
    }

    /** TrueType quadratic contour: two off-curve points in a row imply an on-curve point halfway between them. */
    private fun contour(path: Path, start: Int, end: Int) {
        val n = end - start + 1
        if (n < 2) return
        var first = (start..end).firstOrNull { onCurve[it] }
        val startX: Float
        val startY: Float
        if (first == null) {
            startX = (xs[start] + xs[end]) / 2; startY = (ys[start] + ys[end]) / 2
            first = end // walk all points as controls
        } else {
            startX = xs[first]; startY = ys[first]
        }
        path.moveTo(startX, startY)
        var hasControl = false
        var cx = 0f
        var cy = 0f
        for (k in 1..n) {
            val i = start + (first - start + k) % n
            val x = xs[i]
            val y = ys[i]
            if (onCurve[i]) {
                if (hasControl) path.quadraticTo(cx, cy, x, y) else path.lineTo(x, y)
                hasControl = false
            } else {
                if (hasControl) path.quadraticTo(cx, cy, (cx + x) / 2, (cy + y) / 2)
                cx = x; cy = y
                hasControl = true
            }
        }
        if (hasControl) path.quadraticTo(cx, cy, startX, startY)
        path.close()
    }
}

private const val UnitsPerEm = 960f

/** The outlines from res/raw/symbols.bin (written by tools/gen_symbols.py), read once per process. */
private object GlyphStore {
    @Volatile private var glyphs: Map<Int, Glyph>? = null

    fun get(context: Context): Map<Int, Glyph> = glyphs ?: synchronized(this) {
        glyphs ?: load(context).also { glyphs = it }
    }

    private fun load(context: Context): Map<Int, Glyph> {
        val bytes = context.resources.openRawResource(R.raw.symbols).use { DataInputStream(it).readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(buf.get() == 'S'.code.toByte() && buf.get() == 'Y'.code.toByte() && buf.get() == 'M'.code.toByte() && buf.get() == '1'.code.toByte())
        val count = buf.short.toInt() and 0xFFFF
        val out = HashMap<Int, Glyph>(count * 2)
        repeat(count) {
            val cp = buf.short.toInt() and 0xFFFF
            val points = buf.short.toInt() and 0xFFFF
            val contours = buf.short.toInt() and 0xFFFF
            val ends = IntArray(contours) { buf.short.toInt() and 0xFFFF }
            val onCurve = BooleanArray(points) { buf.get().toInt() != 0 }
            val instances = Array(6) { ShortArray(points * 2) { buf.short } }
            out[cp] = Glyph(ends, onCurve, instances)
        }
        return out
    }
}

/**
 * A Material Symbol at [size], drawn from its outline (not from a font, so no system font replacement can touch it).
 * [fill] 0 = outline … 1 = filled and [weight] 100…700 interpolate the font's own masters on every API level.
 */
@Composable
fun SymbolIcon(
    symbol: Symbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    fill: Float = 0f,
    weight: Int = 400,
) {
    val fillState = rememberUpdatedState(fill)
    val weightState = rememberUpdatedState(weight.toFloat())
    SymbolCanvas(symbol, contentDescription, modifier, size, tint, fillState, weightState)
}

/** [SymbolIcon] whose fill springs between outline and filled when [filled] changes (redraws only, no recomposition). */
@Composable
fun AnimatedSymbolIcon(
    symbol: Symbol,
    contentDescription: String?,
    filled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    weight: Int = 400,
) {
    val fill = animateFloatAsState(if (filled) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "fill")
    val weightState = animateFloatAsState(weight.toFloat(), MaterialTheme.motionScheme.fastEffectsSpec(), label = "weight")
    SymbolCanvas(symbol, contentDescription, modifier, size, tint, fill, weightState)
}

@Composable
private fun SymbolCanvas(
    symbol: Symbol,
    contentDescription: String?,
    modifier: Modifier,
    size: Dp,
    tint: Color,
    fill: State<Float>,
    weight: State<Float>,
) {
    val glyph = GlyphStore.get(LocalContext.current)[symbol.char.code]
    val tintState = rememberUpdatedState(tint)
    val path = remember { Path() }
    val described = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }
    } else modifier
    Spacer(
        described.size(size).drawWithCache {
            onDrawBehind {
                if (glyph == null) return@onDrawBehind
                glyph.outline(path, fill.value, weight.value, this.size.minDimension)
                drawPath(path, tintState.value)
            }
        },
    )
}

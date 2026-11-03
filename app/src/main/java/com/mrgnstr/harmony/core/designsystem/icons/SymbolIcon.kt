package com.mrgnstr.harmony.core.designsystem.icons

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.R
import kotlin.math.roundToInt

/** One Material Symbols glyph (see the generated [Symbols]). */
@Immutable
@JvmInline
value class Symbol(val char: Char)

/** Variable font axes apply from API 26; below that the static outline / filled cuts are crossfaded. */
private val Variable = Build.VERSION.SDK_INT >= 26

private val OutlineFamily = FontFamily(Font(R.font.symbols_rounded_outline))
private val FilledFamily = FontFamily(Font(R.font.symbols_rounded_filled))

// Every distinct axis setting is its own Typeface; steps keep an animation to a few dozen cached ones.
private const val FillSteps = 20
private const val WeightStep = 25
private val families = HashMap<Long, FontFamily>()

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(fill: Float, weight: Int, grade: Int, opsz: Int): FontFamily {
    val f = (fill.coerceIn(0f, 1f) * FillSteps).roundToInt()
    val w = ((weight.coerceIn(100, 700) + WeightStep / 2) / WeightStep) * WeightStep
    val g = grade.coerceIn(-50, 200)
    val o = opsz.coerceIn(20, 48)
    val key = (f.toLong() shl 48) or (w.toLong() shl 32) or ((g + 100).toLong() shl 16) or o.toLong()
    return families.getOrPut(key) {
        FontFamily(
            Font(
                R.font.symbols_rounded,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(
                    FontVariation.Setting("FILL", f / FillSteps.toFloat()),
                    FontVariation.weight(w),
                    FontVariation.Setting("GRAD", g.toFloat()),
                    FontVariation.Setting("opsz", o.toFloat()),
                ),
            ),
        )
    }
}

/**
 * A Material Symbol at [size]. [fill] 0 = outline … 1 = filled, [weight] 100…700, [grade] -50…200: all three can be animated
 * values. Below API 26 [weight] and [grade] are ignored and [fill] crossfades the two static cuts.
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
    grade: Int = 0,
) {
    val fontSize = with(LocalDensity.current) { (size.toPx() / fontScale).toSp() }
    val style = TextStyle(
        color = tint,
        fontSize = fontSize,
        lineHeight = fontSize,
        textAlign = TextAlign.Center,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    val text = symbol.char.toString()
    val described = if (contentDescription != null) modifier.semantics { this.contentDescription = contentDescription } else modifier
    Box(described.size(size), contentAlignment = Alignment.Center) {
        if (Variable) {
            BasicText(text, style = style.copy(fontFamily = variableFamily(fill, weight, grade, size.value.roundToInt())))
        } else {
            val f = fill.coerceIn(0f, 1f)
            if (f < 1f) BasicText(text, Modifier.graphicsLayer { alpha = 1f - f }, style = style.copy(fontFamily = OutlineFamily))
            if (f > 0f) {
                BasicText(
                    text,
                    // the filled glyph grows in slightly, standing in for the axis morph
                    Modifier.graphicsLayer { alpha = f; scaleX = 0.92f + 0.08f * f; scaleY = scaleX },
                    style = style.copy(fontFamily = FilledFamily),
                )
            }
        }
    }
}

/** [SymbolIcon] whose fill springs between outline and filled when [filled] changes. */
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
    val fill by animateFloatAsState(if (filled) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "fill")
    SymbolIcon(symbol, contentDescription, modifier, size, tint, fill, weight)
}

package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct

/**
 * The colours of Now Playing's controls, taken from the cover. They sit on the blurred cover (darkened below it), so they are
 * light tints of the cover's most common colour: far lighter than anything behind them, which is what keeps them from
 * blending into it. A cover with no colour (grey, black and white) keeps plain white.
 */
@Immutable
class CoverColors(
    /** Filled elements: the play button, the progress bar, the handle, a switched-on toggle. */
    val accent: Color,
    /** What sits on [accent]. */
    val onAccent: Color,
    val text: Color,
    val textSoft: Color,
    /** Translucent containers: the skip buttons, a switched-off toggle, the track. */
    val tonal: Color,
    /** How much black goes under the controls (0 on a bright cover, whose controls are dark instead). */
    val scrim: Float,
    /** The cover's top edge is bright (what the status bar sits on): the status bar icons turn dark. */
    val lightTop: Boolean = false,
)

val DefaultCoverColors = CoverColors(
    accent = Color.White, onAccent = Color.Black, text = Color.White,
    textSoft = Color.White.copy(alpha = 0.72f), tonal = Color.White.copy(alpha = 0.2f), scrim = 0.45f,
)

val LocalCoverColors = compositionLocalOf { DefaultCoverColors }

/** Above this brightness of the cover's bottom edge the controls turn dark instead of light. */
private const val BrightCover = 0.58f

/** Above this brightness of the cover's top edge the status bar icons turn dark. */
private const val BrightTop = 0.55f

/**
 * The colours for a cover whose most common colour is [dominant] (ARGB, null if none) and whose bottom edge, the stuff the blurred
 * area behind the controls is made of, has mean brightness [bottomLuminance] (0..1).
 * - A bright cover gets dark controls (deep tints of the cover's hue) on its own bright blur, with no darkening.
 * - Any other gets light tints of the hue, with black under them in proportion to how bright the cover is, so even a mid-tone
 *   blur is dark enough for them.
 * A cover whose most common colour is grey, black or white keeps plain white (or plain black on a bright one).
 */
fun coverColorsOf(dominant: Int?, bottomLuminance: Float, topLuminance: Float = 0f): CoverColors {
    val base = dominant?.let { Hct.fromInt(it) }
    val coloured = base != null && base.chroma >= 6.0
    val hue = base?.hue ?: 0.0
    val chroma = (base?.chroma ?: 0.0).coerceIn(20.0, 48.0)
    fun tint(c: Double, tone: Double) = Color(Hct.from(hue, if (coloured) c else 0.0, tone).toInt())
    val lightTop = topLuminance > BrightTop
    return if (bottomLuminance >= BrightCover) {
        CoverColors(
            accent = tint(chroma, 22.0), onAccent = tint(20.0, 96.0), text = tint(10.0, 10.0),
            textSoft = tint(24.0, 28.0).copy(alpha = 0.85f), tonal = tint(chroma, 30.0).copy(alpha = 0.18f), scrim = 0f, lightTop = lightTop,
        )
    } else {
        // Darken enough that the blur ends up around 30% bright, within a sensible range.
        val scrim = (1f - 0.30f / bottomLuminance.coerceAtLeast(0.01f)).coerceIn(0.3f, 0.5f)
        CoverColors(
            accent = tint(chroma, 86.0), onAccent = tint(30.0, 12.0), text = tint(6.0, 97.0),
            textSoft = tint(18.0, 88.0).copy(alpha = 0.8f), tonal = tint(chroma, 82.0).copy(alpha = 0.26f), scrim = scrim, lightTop = lightTop,
        )
    }
}

/** [target] with every colour eased over, so a song change with a different cover recolours the controls smoothly. */
@Composable
fun animateCoverColors(target: CoverColors): CoverColors {
    val spec = tween<Color>(500)
    return CoverColors(
        accent = animateColorAsState(target.accent, spec, label = "accent").value,
        onAccent = animateColorAsState(target.onAccent, spec, label = "on-accent").value,
        text = animateColorAsState(target.text, spec, label = "text").value,
        textSoft = animateColorAsState(target.textSoft, spec, label = "text-soft").value,
        tonal = animateColorAsState(target.tonal, spec, label = "tonal").value,
        scrim = animateFloatAsState(target.scrim, tween(500), label = "scrim").value,
        lightTop = target.lightTop,
    )
}

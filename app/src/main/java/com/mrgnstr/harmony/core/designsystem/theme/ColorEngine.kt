package com.mrgnstr.harmony.core.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.roundToInt
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

/** Where the app's colours come from right now. */
@Immutable
sealed interface ColorSource {
    /** Material You: the system's wallpaper colours (API 31+), else [FallbackSeed]. */
    data object Wallpaper : ColorSource

    /** A colour picked from artwork (album cover, artist image). */
    data class Seed(val argb: Int) : ColorSource
}

/** Used when the system has no wallpaper colours (below API 31). Material's baseline purple until there is a setting. */
val FallbackSeed = Color(0xFF6750A4)

/**
 * Builds and caches colour schemes. Wallpaper schemes come from the system; seeds go through MaterialKolor (the same
 * dynamic-colour algorithm, 2025 spec) so an album page looks like the system would theme that colour.
 */
class SchemeFactory(private val context: Context) {
    private val cache = object : LinkedHashMap<Triple<ColorSource, Boolean, PaletteStyle>, ColorScheme>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<ColorSource, Boolean, PaletteStyle>, ColorScheme>) = size > 24
    }

    fun scheme(source: ColorSource, dark: Boolean, style: PaletteStyle = PaletteStyle.TonalSpot): ColorScheme =
        cache.getOrPut(Triple(source, dark, style)) {
            when (source) {
                ColorSource.Wallpaper ->
                    if (Build.VERSION.SDK_INT >= 31) {
                        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    } else fromSeed(FallbackSeed, dark, style)
                is ColorSource.Seed -> fromSeed(Color(source.argb), dark, style)
            }
        }

    /** The system's wallpaper colours changed (the activity is recreated for that, but be safe). */
    fun invalidateWallpaper() {
        cache.keys.removeAll { it.first == ColorSource.Wallpaper }
    }

    private fun fromSeed(seed: Color, dark: Boolean, style: PaletteStyle) =
        dynamicColorScheme(seedColor = seed, isDark = dark, style = style, specVersion = ColorSpec.SpecVersion.SPEC_2025)
}

/** Every role of [a] moved towards [b] by [t]. */
fun lerp(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme {
    if (t <= 0f) return a
    if (t >= 1f) return b
    fun l(x: Color, y: Color) = lerp(x, y, t)
    return a.copy(
        primary = l(a.primary, b.primary),
        onPrimary = l(a.onPrimary, b.onPrimary),
        primaryContainer = l(a.primaryContainer, b.primaryContainer),
        onPrimaryContainer = l(a.onPrimaryContainer, b.onPrimaryContainer),
        inversePrimary = l(a.inversePrimary, b.inversePrimary),
        secondary = l(a.secondary, b.secondary),
        onSecondary = l(a.onSecondary, b.onSecondary),
        secondaryContainer = l(a.secondaryContainer, b.secondaryContainer),
        onSecondaryContainer = l(a.onSecondaryContainer, b.onSecondaryContainer),
        tertiary = l(a.tertiary, b.tertiary),
        onTertiary = l(a.onTertiary, b.onTertiary),
        tertiaryContainer = l(a.tertiaryContainer, b.tertiaryContainer),
        onTertiaryContainer = l(a.onTertiaryContainer, b.onTertiaryContainer),
        background = l(a.background, b.background),
        onBackground = l(a.onBackground, b.onBackground),
        surface = l(a.surface, b.surface),
        onSurface = l(a.onSurface, b.onSurface),
        surfaceVariant = l(a.surfaceVariant, b.surfaceVariant),
        onSurfaceVariant = l(a.onSurfaceVariant, b.onSurfaceVariant),
        surfaceTint = l(a.surfaceTint, b.surfaceTint),
        inverseSurface = l(a.inverseSurface, b.inverseSurface),
        inverseOnSurface = l(a.inverseOnSurface, b.inverseOnSurface),
        error = l(a.error, b.error),
        onError = l(a.onError, b.onError),
        errorContainer = l(a.errorContainer, b.errorContainer),
        onErrorContainer = l(a.onErrorContainer, b.onErrorContainer),
        outline = l(a.outline, b.outline),
        outlineVariant = l(a.outlineVariant, b.outlineVariant),
        scrim = l(a.scrim, b.scrim),
        surfaceBright = l(a.surfaceBright, b.surfaceBright),
        surfaceDim = l(a.surfaceDim, b.surfaceDim),
        surfaceContainer = l(a.surfaceContainer, b.surfaceContainer),
        surfaceContainerHigh = l(a.surfaceContainerHigh, b.surfaceContainerHigh),
        surfaceContainerHighest = l(a.surfaceContainerHighest, b.surfaceContainerHighest),
        surfaceContainerLow = l(a.surfaceContainerLow, b.surfaceContainerLow),
        surfaceContainerLowest = l(a.surfaceContainerLowest, b.surfaceContainerLowest),
        primaryFixed = l(a.primaryFixed, b.primaryFixed),
        primaryFixedDim = l(a.primaryFixedDim, b.primaryFixedDim),
        onPrimaryFixed = l(a.onPrimaryFixed, b.onPrimaryFixed),
        onPrimaryFixedVariant = l(a.onPrimaryFixedVariant, b.onPrimaryFixedVariant),
        secondaryFixed = l(a.secondaryFixed, b.secondaryFixed),
        secondaryFixedDim = l(a.secondaryFixedDim, b.secondaryFixedDim),
        onSecondaryFixed = l(a.onSecondaryFixed, b.onSecondaryFixed),
        onSecondaryFixedVariant = l(a.onSecondaryFixedVariant, b.onSecondaryFixedVariant),
        tertiaryFixed = l(a.tertiaryFixed, b.tertiaryFixed),
        tertiaryFixedDim = l(a.tertiaryFixedDim, b.tertiaryFixedDim),
        onTertiaryFixed = l(a.onTertiaryFixed, b.onTertiaryFixed),
        onTertiaryFixedVariant = l(a.onTertiaryFixedVariant, b.onTertiaryFixedVariant),
    )
}

/** Colour changes settle without overshoot (a colour bouncing past its target reads as a flicker). */
private val ColorSpring = spring<Float>(dampingRatio = 1f, stiffness = 200f, visibilityThreshold = 0.002f)
private val LowTierColorTween = tween<Float>(250)

private const val BlendSteps = 20

private class SchemeBlend(initial: ColorScheme) {
    var from = initial
    var to = initial
    val progress = Animatable(1f)

    // Read in composition: a colour change recomposes everything that reads a colour (a page full of text and icons, both pages of a
    // transition), so the blend moves in BlendSteps notches, not every frame. 30 Hz colour is invisible; 60+ recompositions are not.
    private val step = derivedStateOf { (progress.value * BlendSteps).roundToInt() }

    val current: ColorScheme get() = step.value.let { if (it >= BlendSteps) to else lerp(from, to, it / BlendSteps.toFloat()) }

    suspend fun animateTo(target: ColorScheme, lowTier: Boolean) {
        if (target === to) return
        from = current
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, if (lowTier) LowTierColorTween else ColorSpring)
    }

    /** Shows [target] as is; the next [animateTo] starts from it. */
    fun hold(target: ColorScheme) {
        from = target
        to = target
    }
}

/**
 * The scheme shown on screen, following [target]: every change blends from whatever is showing (even mid-blend) to the new
 * target on one spring. With [snap] it follows [target] frame by frame instead (a predictive back gesture drives the blend).
 */
@Composable
fun animateColorScheme(target: ColorScheme, snap: Boolean, lowTier: Boolean): ColorScheme {
    val blend = remember { SchemeBlend(target) }
    LaunchedEffect(target, snap) { if (!snap) blend.animateTo(target, lowTier) }
    if (snap) {
        blend.hold(target)
        return target
    }
    return blend.current
}

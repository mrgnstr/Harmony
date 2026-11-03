package com.mrgnstr.harmony.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.mrgnstr.harmony.core.designsystem.LocalChromeBlurMode
import com.mrgnstr.harmony.core.designsystem.LocalCoverBlurMode
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.device.VisualTier
import com.mrgnstr.harmony.core.model.BlurMode

/**
 * M3 Expressive theme around [colorScheme] (already resolved and animated by the caller, see [animateColorScheme]), with the
 * expressive motion scheme and the app's device locals.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HarmonyTheme(
    colorScheme: ColorScheme,
    visualTier: VisualTier,
    chromeBlur: BlurMode = BlurMode.Auto,
    coverBlur: BlurMode = BlurMode.Auto,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVisualTier provides visualTier,
        LocalChromeBlurMode provides chromeBlur,
        LocalCoverBlurMode provides coverBlur,
    ) {
        MaterialExpressiveTheme(colorScheme = colorScheme, motionScheme = MotionScheme.expressive(), content = content)
    }
}

/**
 * Which colour source each navigation entry asked for. Pages claim one with [PageColorSource]; the scaffold reads the top
 * entry's (and, during a back gesture, the one below) and themes the whole app with it, tab bar and mini player included.
 */
@Stable
class ColorSourceRegistry {
    private val claims = mutableStateMapOf<Any, ColorSource>()

    fun sourceOf(entryKey: Any?): ColorSource = entryKey?.let { claims[it] } ?: ColorSource.Wallpaper

    internal fun claim(entryKey: Any, source: ColorSource) { claims[entryKey] = source }

    internal fun release(entryKey: Any) { claims.remove(entryKey) }
}

val LocalColorSourceRegistry = staticCompositionLocalOf<ColorSourceRegistry?> { null }

/** Provided by the scaffold around each navigation entry, so a page's claim knows which entry it belongs to. */
val LocalEntryKey = staticCompositionLocalOf<Any?> { null }

/** Themes the app from [source] while this page's entry is on top; null (e.g. art not loaded yet) keeps the wallpaper. */
@Composable
fun PageColorSource(source: ColorSource?) {
    val registry = LocalColorSourceRegistry.current ?: return
    val key = LocalEntryKey.current ?: return
    SideEffect { if (source != null) registry.claim(key, source) else registry.release(key) }
    DisposableEffect(registry, key) { onDispose { registry.release(key) } }
}

@Composable
fun rememberColorSourceRegistry(): ColorSourceRegistry = remember { ColorSourceRegistry() }

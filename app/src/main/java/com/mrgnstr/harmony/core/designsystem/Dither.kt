package com.mrgnstr.harmony.core.designsystem

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.mrgnstr.harmony.core.debug.DebugOptions

// Triangular noise of +-DitherLevels (8 bit levels) on the finished pixels, scaled by alpha because the pixels are premultiplied.
// Two independent hashes make it triangular: a shifted copy of one pattern is correlated with it and gives far less noise.
private const val DitherLevels = 1.5f

private const val DitherAgsl = """
uniform shader content;
uniform float levels;

float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 p) {
    half4 c = content.eval(p);
    float n = (hash(p) + hash(p + float2(17.0, 31.0)) - 1.0) * levels;
    half3 rgb = c.rgb + half3(n * c.a / 255.0);
    return half4(clamp(rgb, half3(0.0), half3(c.a)), c.a);
}
"""

@get:RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val ditherEffect by lazy(LazyThreadSafetyMode.NONE) {
    AndroidRenderEffect.createRuntimeShaderEffect(
        RuntimeShader(DitherAgsl).apply { setFloatUniform("levels", DitherLevels) },
        "content",
    ).asComposeRenderEffect()
}

/**
 * Dithers what is drawn inside, against the banding that wide, smooth blurs of dark pictures show in 8 bits: about a level and a half of
 * noise breaks the edges between levels up, so they read as a smooth gradient. Android 13+ (it is a shader); costs one pass
 * over the layer, so it goes on blurred backgrounds only. The debug switch turns it off.
 */
fun Modifier.dither(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        graphicsLayer { renderEffect = if (DebugOptions.dither.on) ditherEffect else null }
    } else {
        this
    }

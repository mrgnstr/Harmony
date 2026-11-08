package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tapping the nightcore icon: the moon and the note lean away from each other and spring back (call [play] from the click). */
@Stable
internal class NightcoreKick(private val scope: CoroutineScope) {
    /** 0 = at rest, 1 = furthest apart. */
    val apart = Animatable(0f)
    fun play() {
        scope.launch {
            apart.animateTo(1f, tween(90))
            apart.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 420f, visibilityThreshold = 0.001f))
        }
    }
}

@Composable
internal fun rememberNightcoreKick(): NightcoreKick {
    val scope = rememberCoroutineScope()
    return remember(scope) { NightcoreKick(scope) }
}

/**
 * Nightcore's icon: an upright outline moon with a note beside it, the note's round head level with the moon's centre and a thin
 * gap cut where they meet. Positions are those of the approved design (a 40 dp box, the moon at its usual 24 dp, the note 17.6 dp).
 * Both glyphs are drawn into one layer that is then laid over the button, so with [BlendMode.Clear] (the on state) the pair is
 * cut out of the container, gap included. [filled] (on) fills the moon in.
 */
@Composable
internal fun NightcoreIcon(blend: BlendMode, tint: Color, weight: Int, filled: Boolean, kick: NightcoreKick, modifier: Modifier = Modifier) {
    // The moon fills in when nightcore goes on (the note is solid either way), on the same spring as the other toggles' fills.
    val fill = animateFloatAsState(if (filled) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "moon-fill").value
    Box(
        modifier.size(40.dp).drawWithContent {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply { blendMode = if (blend == BlendMode.Clear) BlendMode.DstOut else BlendMode.SrcOver }
                canvas.saveLayer(Rect(Offset.Zero, size), paint)
                drawContent()
                canvas.restore()
            }
        },
    ) {
        SymbolIcon(
            Symbols.Nightlight, "Nightcore", tint = tint, weight = weight, fill = fill, size = 24.dp,
            modifier = Modifier
                .offset(5.35.dp, 8.91.dp)
                .graphicsLayer {
                    val t = kick.apart.value
                    transformOrigin = TransformOrigin(0.459f, 0.5f)
                    translationX = -1.5.dp.toPx() * t
                    rotationZ = -9f * t
                },
        )
        SymbolIcon(
            Symbols.MusicNote, null, tint = tint, weight = weight, fill = 1f, size = 17.58.dp, gap = 1.4.dp,
            modifier = Modifier
                .offset(19.17.dp, 8.6.dp)
                .graphicsLayer {
                    val t = kick.apart.value
                    translationX = 1.8.dp.toPx() * t
                    translationY = -0.6.dp.toPx() * t
                    rotationZ = 10f * t
                },
        )
    }
}

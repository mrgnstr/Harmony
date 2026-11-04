package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.icons.Symbol
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A skip glyph that jumps a few dp in its direction when pressed and springs back (call [kick] from the click). */
@Stable
internal class SkipKick(private val scope: CoroutineScope, private val direction: Float, private val reach: Float) {
    val offset = Animatable(0f)
    fun kick() {
        scope.launch {
            offset.animateTo(direction * reach, tween(70))
            offset.animateTo(0f, Springs.toss(450f))
        }
    }
}

@Composable
internal fun rememberSkipKick(next: Boolean, reach: Dp = 10.dp): SkipKick {
    val scope = rememberCoroutineScope()
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { reach.toPx() }
    return remember(scope, next, px) { SkipKick(scope, if (next) 1f else -1f, px) }
}

@Composable
internal fun SkipIcon(symbol: Symbol, description: String, kick: SkipKick, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
    SymbolIcon(symbol, description, Modifier.graphicsLayer { translationX = kick.offset.value }, size = size, tint = tint, fill = 1f)
}

/** A one-shot spin/pop for toggles: [play] restarts it from 0 and springs to 1; read [value] in a graphicsLayer. */
@Stable
internal class IconFlourish(private val scope: CoroutineScope, private val bounce: Float) {
    val value = Animatable(1f)
    fun play() {
        scope.launch {
            value.snapTo(0f)
            value.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = bounce, stiffness = 260f, visibilityThreshold = 0.001f))
        }
    }
}

/** [bounce] is the spring's damping ratio: 1 settles without overshoot, lower overshoots. */
@Composable
internal fun rememberFlourish(bounce: Float): IconFlourish {
    val scope = rememberCoroutineScope()
    return remember(scope, bounce) { IconFlourish(scope, bounce) }
}

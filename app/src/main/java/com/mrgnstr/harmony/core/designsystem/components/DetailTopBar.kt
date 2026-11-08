package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols

private val BarRow = 64.dp

/** Transparent over artwork; the blurred surface and centered page title appear as it scrolls away. */
@Composable
fun DetailTopBar(
    title: String,
    fade: () -> Float,
    blur: com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState,
    statusTop: Dp,
    onBack: () -> Unit,
    overArtwork: Boolean = false,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    // Finish the surface in the first third of the title fade. Sharing the title's slower alpha lets the sharp
    // scrolling header show through the blur layer, producing two overlapping titles (also on the flat fallback).
    val surfaceFade = { (fade() * 3f).coerceIn(0f, 1f) }
    Box(Modifier.fillMaxWidth().height(statusTop + BarRow)) {
        ChromeSurface(blur, RectangleShape, Modifier.matchParentSize().graphicsLayer { alpha = surfaceFade() }, container = MaterialTheme.colorScheme.surface)
        Box(Modifier.padding(top = statusTop).height(BarRow).fillMaxWidth().padding(horizontal = 64.dp).graphicsLayer { alpha = fade() }, contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val overImage by remember { derivedStateOf { surfaceFade() < 0.5f } }
        CompositionLocalProvider(LocalContentColor provides if (overArtwork && overImage) Color.White else MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.padding(top = statusTop).height(BarRow).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { SymbolIcon(Symbols.ArrowBack, "Back") }
                Box(Modifier.weight(1f))
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton({ open = true }) { SymbolIcon(Symbols.MoreVert, "More") }
                    DropdownMenu(open, { open = false }) { menu { open = false } }
                }
            }
        }
    }
}

/** False while this page's own enter transition is running: pages hold back their heavy content until it has settled. */
@Composable
fun rememberTransitionSettled(): Boolean {
    val transition = androidx.navigation3.ui.LocalNavAnimatedContentScope.current.transition
    return transition.currentState == androidx.compose.animation.EnterExitState.Visible && transition.targetState == androidx.compose.animation.EnterExitState.Visible
}

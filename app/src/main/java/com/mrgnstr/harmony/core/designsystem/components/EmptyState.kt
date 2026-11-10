package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.LocalTopBarCollapse
import com.mrgnstr.harmony.core.designsystem.LocalTopBarInset
import com.mrgnstr.harmony.core.designsystem.icons.Symbol
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import kotlinx.coroutines.delay

/** How long a page waits before showing anything: a library that is already in Room fills in well before this. */
private const val ShowDelayMs = 300L

/**
 * What a page shows in place of an empty list: a spinner while the library is still being read ([loading]), otherwise an icon, a
 * title and a line of help. Centred in what the top bar and the floating chrome leave free.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    loading: Boolean,
    symbol: Symbol,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    // Both wait a moment: an empty list can just be the first frame before Room's rows arrive.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(ShowDelayMs); shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "emptyAlpha")
    // The page is as tall as the bar's collapse beyond the screen (see LocalTopBarCollapse), so that is not free space.
    Column(
        modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }.padding(
            top = LocalTopBarInset.current,
            bottom = LocalBottomBarInset.current + LocalTopBarCollapse.current,
            start = 32.dp, end = 32.dp,
        ).padding(start = LocalSideInsets.current.left, end = LocalSideInsets.current.right),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            LoadingIndicator()
        } else {
            SymbolIcon(symbol, null, size = 48.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

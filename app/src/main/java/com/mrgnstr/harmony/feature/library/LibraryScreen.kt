package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.components.MediaRow
import com.mrgnstr.harmony.core.designsystem.components.PageScaffold
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols

/** Library tab. Placeholder until M5 (tab row + pager with grids). */
@Composable
fun LibraryRoute(onOpenSettings: () -> Unit, onOpenGallery: () -> Unit) {
    PageScaffold(
        title = "Library",
        actions = { IconButton(onClick = onOpenSettings) { SymbolIcon(Symbols.Settings, "Settings") } },
    ) {
        item {
            Text(
                "Recently Added, Albums, Songs, Artists, Playlists and Genres arrive in M5.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { MediaRow("Design gallery", "Pushed page: in-tab navigation, predictive back, page colour", onClick = onOpenGallery) }
        items(30) { i -> MediaRow("Scroll filler ${i + 1}", null, onClick = {}) }
    }
}

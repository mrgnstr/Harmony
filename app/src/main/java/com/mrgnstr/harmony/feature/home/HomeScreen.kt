package com.mrgnstr.harmony.feature.home

import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.mrgnstr.harmony.core.designsystem.components.MediaRow
import com.mrgnstr.harmony.core.designsystem.components.PageScaffold
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols

/** Home tab. Placeholder until M8 (shelves like the old Home). */
@Composable
fun HomeRoute(onOpenSettings: () -> Unit, onOpenGallery: () -> Unit) {
    PageScaffold(
        title = "Home",
        actions = { IconButton(onClick = onOpenSettings) { SymbolIcon(Symbols.Settings, "Settings") } },
    ) {
        item {
            Text(
                "Recently played, favourite songs and favourite albums arrive in M8.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { MediaRow("Design gallery", "Colour engine, motion and components (M2)", onClick = onOpenGallery) }
        items(30) { i -> MediaRow("Scroll filler ${i + 1}", "Content passes under the blurred bar", onClick = {}) }
    }
}

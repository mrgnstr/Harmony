package com.mrgnstr.harmony.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.components.MediaRow
import com.mrgnstr.harmony.core.designsystem.components.PageScaffold

/** Settings. Placeholder until M10. */
@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenGallery: (() -> Unit)? = null) {
    PageScaffold(title = "Settings", onBack = onBack) {
        item {
            Text(
                "Appearance, playback and library settings arrive in M10.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onOpenGallery != null) {
            item {
                MediaRow("Design gallery", "Colour engine, motion and components (M2)", onClick = onOpenGallery)
            }
        }
    }
}

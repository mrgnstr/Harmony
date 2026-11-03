package com.mrgnstr.harmony.feature.search

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.components.PageScaffold

/** Search. Placeholder until M8 (FTS search from the old app, expressive search field). */
@Composable
fun SearchRoute() {
    PageScaffold(title = "Search") {
        item {
            Text(
                "Search arrives in M8.",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

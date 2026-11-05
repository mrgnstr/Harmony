package com.mrgnstr.harmony.core.designsystem

import com.mrgnstr.harmony.core.artwork.ArtworkKey
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade

/** How long the cover (and the blur around it) takes to fade into the next song's cover. */
const val CoverFadeMs = 300

/**
 * Square artwork with a note-icon placeholder behind it (shown while loading and when there is no art).
 * [placeholderOf] is the cover that was showing before this one: while the new art loads, that cover stays on screen
 * (read straight from Coil's memory cache) instead of flashing the note placeholder, and with [crossfadeMs] > 0 the
 * old cover fades into the new one.
 */
@Composable
fun ArtworkImage(
    songId: Long,
    albumId: Long,
    modifier: Modifier = Modifier,
    placeholderOf: ArtworkKey? = null,
    crossfadeMs: Int = 0,
) {
    val key = remember(songId, albumId) { ArtworkKey(songId, albumId) }
    val context = LocalPlatformContext.current
    val request = remember(key, placeholderOf, crossfadeMs, context) {
        ImageRequest.Builder(context).data(key).apply {
            // The same cover at another size (a tile opening into the album page) is in the memory cache under the same key: show it
            // while the new size loads, instead of the note placeholder.
            placeholderMemoryCacheKey(MemoryCache.Key((placeholderOf ?: key).cacheKey))
            if (crossfadeMs > 0) crossfade(crossfadeMs)
        }.build()
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        SymbolIcon(Symbols.MusicNote, null, size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

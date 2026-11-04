package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.Playlist
import java.io.File

/**
 * A playlist's cover: a star for Favourites, the custom image when there is one, otherwise the first four different album covers as
 * a 2x2 mosaic (a single cover when it has fewer than four), otherwise a placeholder. The caller sizes and clips it.
 */
@Composable
fun PlaylistCover(playlist: Playlist, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        val art = playlist.art
        when {
            playlist.isFavourites -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SymbolIcon(Symbols.Star, null, size = maxWidth * 0.5f, tint = MaterialTheme.colorScheme.primary, fill = 1f)
            }
            playlist.coverPath != null -> AsyncImage(File(playlist.coverPath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            art.size >= 4 -> Column(Modifier.fillMaxSize()) {
                for (row in 0..1) {
                    Row(Modifier.weight(1f)) {
                        for (col in 0..1) {
                            val ref = art[row * 2 + col]
                            ArtworkImage(ref.songId, ref.albumId, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            art.isNotEmpty() -> ArtworkImage(art.first().songId, art.first().albumId, Modifier.fillMaxSize())
            else -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SymbolIcon(Symbols.QueueMusic, null, size = maxWidth * 0.4f, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

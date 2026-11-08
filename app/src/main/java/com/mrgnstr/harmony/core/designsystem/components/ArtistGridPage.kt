package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.Artist
import java.io.File

/** Width of an artist tile in every horizontal artist shelf. */
val ArtistShelfTileWidth = 120.dp

/** One tile of an artist shelf or grid. [artist] is the library entry when there is one (photo or cover, menu); remote-only artists have none. */
data class ArtistShelfItem(val name: String, val subtitle: String? = null, val artist: Artist? = null)

/** A circular artist tile: the library artist's photo or cover with its menu, or a plain icon for an artist the library does not have. */
@Composable
fun ArtistShelfTile(item: ArtistShelfItem, onClick: () -> Unit, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    var menuInitialized by remember { mutableStateOf(false) }
    val artist = item.artist
    ArtworkTile(
        item.name, item.subtitle, onClick, modifier,
        circular = true,
        onLongClick = if (artist != null) ({ menuInitialized = true; menuOpen = true }) else null,
        menu = { if (menuInitialized) ArtistContextMenu(item.name, menuOpen, { menuOpen = false }) },
    ) { cover ->
        val custom = artist?.imagePath
        when {
            custom != null -> AsyncImage(File(custom), null, cover, contentScale = ContentScale.Crop)
            artist != null -> ArtworkImage(artist.artSongId, artist.artAlbumId, cover)
            else -> Box(cover.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                SymbolIcon(Symbols.Artist, null, size = 36.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

/** Full list reached from an artist shelf; the same grid, spacing and column sizing as Library's Artists tab. */
@Composable
fun ArtistGridPage(
    title: String,
    subtitle: String,
    artists: List<ArtistShelfItem>,
    loaded: Boolean,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    TopBarScaffold(title, subtitle = subtitle, onBack = onBack) {
        if (artists.isEmpty()) {
            EmptyState(!loaded, Symbols.Artist, "No artists", "There are no artists in this list.")
        } else {
            TileGrid(artists.size) { columns ->
                itemsIndexed(artists, key = { _, a -> "artist:${a.name}" }, contentType = { _, _ -> "artist" }) { i, item ->
                    ArtistShelfTile(item, { onOpenArtist(item.name) }, itemMotion().padding(gridCellPadding(i % columns, columns)))
                }
            }
        }
    }
}

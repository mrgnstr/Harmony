package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.LocalTopBarInset
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.Album

/**
 * A grid page: [columns] tiles across, headers spanning all of them, and a fast scroller over it. [labelOf] names an item index for
 * the scroller's bubble (null where the ordering is not alphabetical).
 */
@Composable
fun TileGrid(itemCount: Int, labelOf: (Int) -> String? = { null }, content: LazyGridScope.(columns: Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sides = LocalSideInsets.current
        val columns = gridColumns(maxWidth - sides.left - sides.right)
        val grid = rememberLazyGridState()
        LazyVerticalGrid(
            GridCells.Fixed(columns), Modifier.fillMaxSize(), grid, contentPadding = LocalSideInsets.current.padding(top = LocalTopBarInset.current + 16.dp, bottom = LocalBottomBarInset.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content(columns) }
        // The page box starts above the collapsed bar (it is slid up with it), so the track starts below the expanded height.
        LazyGridFastScroller(grid, itemCount, labelOf, topInset = LocalTopBarInset.current + 8.dp, bottomInset = LocalBottomBarInset.current + 8.dp)
    }
}

/** Full collection reached from a release shelf; the same tiles, spacing, column sizing and scroller as Library. */
@Composable
fun AlbumGridPage(
    title: String,
    subtitle: String,
    albums: List<Album>,
    loaded: Boolean,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    TopBarScaffold(title, subtitle = subtitle, onBack = onBack) {
        if (albums.isEmpty()) {
            EmptyState(!loaded, Symbols.Album, "No releases", "There are no releases in this collection.")
        } else {
            TileGrid(albums.size) { columns ->
                itemsIndexed(albums, key = { _, album -> "${album.id}:${album.title}" }, contentType = { _, _ -> "album" }) { i, album ->
                    AlbumTile(album, { onOpenAlbum(album) }, itemMotion().padding(gridCellPadding(i % columns, columns)))
                }
            }
        }
    }
}

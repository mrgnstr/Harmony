package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.components.AlbumTile
import com.mrgnstr.harmony.core.designsystem.components.ArtworkTile
import com.mrgnstr.harmony.core.designsystem.components.PlayShuffleButtons
import com.mrgnstr.harmony.core.designsystem.components.SongRow
import com.mrgnstr.harmony.core.designsystem.components.gridCellPadding
import com.mrgnstr.harmony.core.designsystem.components.gridColumns
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Artist
import java.io.File

/** Room under the list for the floating tab bar and the mini player. */
@Composable
internal fun listPadding() = PaddingValues(top = 16.dp, bottom = LocalBottomBarInset.current + 16.dp)

/** A grid page: [columns] tiles across, headers spanning all of them. */
@Composable
internal fun TileGrid(content: LazyGridScope.(columns: Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = gridColumns(maxWidth)
        LazyVerticalGrid(
            GridCells.Fixed(columns), Modifier.fillMaxSize(), contentPadding = listPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content(columns) }
    }
}

@Composable
fun RecentlyAddedTab(viewModel: LibraryViewModel, onOpenAlbum: (Album) -> Unit) {
    val albums by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    TileGrid { columns -> albumTiles(albums, columns, onOpenAlbum) }
}

@Composable
fun AlbumsTab(viewModel: LibraryViewModel, onOpenAlbum: (Album) -> Unit) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    TileGrid { columns -> albumTiles(albums, columns, onOpenAlbum) }
}

private fun LazyGridScope.albumTiles(albums: List<Album>, columns: Int, onOpenAlbum: (Album) -> Unit) {
    gridItemsIndexed(albums, key = { _, a -> "album:${a.id}:${a.title}" }, contentType = { _, _ -> "album" }) { i, album ->
        AlbumTile(album, { onOpenAlbum(album) }, itemMotion().padding(gridCellPadding(i % columns, columns)))
    }
}

@Composable
fun SongsTab(viewModel: LibraryViewModel) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding()) {
        item(key = "buttons", contentType = "buttons") {
            PlayShuffleButtons(onPlay = { viewModel.play(songs, 0) }, onShuffle = { viewModel.shuffle(songs) }, modifier = Modifier.padding(bottom = 8.dp))
        }
        itemsIndexed(songs, key = { _, song -> song.id }, contentType = { _, _ -> "song" }) { index, song ->
            SongRow(song, onClick = { viewModel.play(songs, index) }, modifier = itemMotion())
        }
    }
}

@Composable
fun ArtistsTab(viewModel: LibraryViewModel, onOpenArtist: (Artist) -> Unit) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    TileGrid { columns ->
        gridItemsIndexed(artists, key = { _, a -> "artist:${a.name}" }, contentType = { _, _ -> "artist" }) { i, artist ->
            ArtworkTile(
                artist.name, null, { onOpenArtist(artist) },
                itemMotion().padding(gridCellPadding(i % columns, columns)),
                circular = true,
            ) { cover ->
                // Local files carry no artist photos: the picture is their own image, or the cover of one of their albums.
                val custom = artist.imagePath
                if (custom != null) AsyncImage(File(custom), null, cover, contentScale = ContentScale.Crop)
                else ArtworkImage(artist.artSongId, artist.artAlbumId, cover)
            }
        }
    }
}

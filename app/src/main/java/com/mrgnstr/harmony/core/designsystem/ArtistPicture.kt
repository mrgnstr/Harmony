package com.mrgnstr.harmony.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.model.Artist
import java.io.File

/** An artist's picture as the library resolved it ([Artist.imagePath]: own photo or the server's), else a cover of one of their releases. */
@Composable
fun ArtistPicture(artist: Artist, modifier: Modifier = Modifier) {
    val image = artist.imagePath
    if (image != null) AsyncImage(File(image), null, modifier, contentScale = ContentScale.Crop)
    else ArtworkImage(artist.artSongId, artist.artAlbumId, modifier)
}

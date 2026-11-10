package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import com.mrgnstr.harmony.core.database.AlbumKey
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Song

/**
 * What can't be played without a connection. While offline, server songs that aren't downloaded and releases with no downloaded (or
 * local) track are greyed out; online (and for the default) nothing is, so nothing recomposes while the network is fine.
 */
@Immutable
class OfflineDim(private val offline: Boolean, private val playableAlbums: Set<AlbumKey>) {
    fun song(song: Song) = offline && song.isRemote && !song.isDownloaded
    fun album(album: Album) = offline && AlbumKey(album.id, album.title) !in playableAlbums

    companion object { val None = OfflineDim(false, emptySet()) }
}

val LocalOfflineDim = staticCompositionLocalOf { OfflineDim.None }

private const val DimmedAlpha = 0.45f

/**
 * Fades [on] content towards the page and drops its colour, so covers go grey and text goes soft in light and dark alike
 * (alpha over the surface, not a fixed grey). Eases in and out so a dropped connection doesn't flash the page.
 */
@Composable
fun Modifier.offlineDim(on: Boolean): Modifier {
    val alpha by animateFloatAsState(if (on) DimmedAlpha else 1f, tween(300), label = "offlineDim")
    val grey by animateFloatAsState(if (on) 0f else 1f, tween(300), label = "offlineGrey")
    if (!on && alpha == 1f) return this
    return this
        .graphicsLayer { this.alpha = alpha }
        .drawWithContent {
            val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(grey) }) }
            drawIntoCanvas {
                it.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                drawContent()
                it.restore()
            }
        }
}

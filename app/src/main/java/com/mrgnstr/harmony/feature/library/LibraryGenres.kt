package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.SortKey
import com.materialkolor.hct.Hct
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.components.TileCoverShape
import com.mrgnstr.harmony.core.designsystem.components.gridCellPadding
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.designsystem.motion.pressResponse

@Composable
fun GenresTab(viewModel: LibraryViewModel, onOpenGenre: (String) -> Unit) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val key = viewModel.sorts.collectAsStateWithLifecycle().value.getValue(LibraryTab.Genres).key
    if (genres.isEmpty()) return LibraryEmpty(viewModel, Symbols.Genres, "No genres", "Genres come from the tags in your music files.")
    TileGrid(genres.size, { i -> genres.getOrNull(i)?.takeIf { key == SortKey.Title }?.let { letterOf(it.name) } }) { columns ->
        itemsIndexed(genres, key = { _, g -> g.name }, contentType = { _, _ -> "genre" }) { i, genre ->
            GenreCardTile(genre, { onOpenGenre(genre.name) }, itemMotion().padding(gridCellPadding(i % columns, columns)))
        }
    }
}

/** A card tinted with a colour seeded from the genre's name, the name on top and a fan of its album covers at the bottom right. */
@Composable
private fun GenreCardTile(genre: GenreCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val hue = remember(genre.name) { (genre.name.lowercase().hashCode().toLong() and 0x7fffffff) % 360L }
    val container = remember(hue, dark) { Color(Hct.from(hue.toDouble(), 32.0, if (dark) 26.0 else 90.0).toInt()) }
    val content = remember(hue, dark) { Color(Hct.from(hue.toDouble(), 24.0, if (dark) 92.0 else 18.0).toInt()) }
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(1f).pressResponse(interaction, pressedScale = 0.95f)
            .clip(TileCoverShape).background(container).clickable(interaction, indication = null, onClick = onClick).clipToBounds(),
    ) {
        val cover = maxWidth * 0.44f
        genre.art.asReversed().forEachIndexed { reversed, ref ->
            val depth = genre.art.size - 1 - reversed // 0 = the front cover
            ArtworkImage(
                ref.songId, ref.albumId,
                Modifier.align(Alignment.BottomEnd).offset(x = cover * (0.1f - 0.5f * depth), y = cover * 0.1f).size(cover)
                    .border(BorderStroke(2.dp, container), RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text(genre.name, style = MaterialTheme.typography.titleMediumEmphasized, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${genre.songCount} ${if (genre.songCount == 1) "song" else "songs"}", style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.72f))
        }
    }
}

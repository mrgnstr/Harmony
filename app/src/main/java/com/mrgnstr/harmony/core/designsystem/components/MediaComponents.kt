package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.motion.pressResponse
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Song

/** Cover corners: expressive "large" rounding on tiles, smaller on row thumbnails. */
val TileCoverShape = RoundedCornerShape(20.dp)
val RowCoverShape = RoundedCornerShape(10.dp)

/** Album / playlist grids (sizes from the old app): tiles at least [GridMinTile] wide, [GridEdge] in, [GridGap] apart. */
val GridMinTile = 150.dp
val GridEdge = 16.dp
val GridGap = 16.dp

/** Width of a tile in a horizontal shelf. */
val ShelfTileWidth = 156.dp

/** How many grid columns fit in [width] (at least one): 2 on phones in portrait, more in wider space. */
fun gridColumns(width: Dp): Int = ((width - GridEdge * 2 + GridGap) / (GridMinTile + GridGap)).toInt().coerceAtLeast(1)

/** The padding of a tile in [column] of [columns] grid cells, so all tiles are equally wide, [GridEdge] in and [GridGap] apart. */
fun gridCellPadding(column: Int, columns: Int): PaddingValues {
    val share = (GridEdge * 2 + GridGap * (columns - 1)) / columns
    val start = GridEdge + (GridGap - share) * column
    return PaddingValues(start = start, end = share - start)
}

/** A square artwork tile with one or two lines under it; squishes when pressed. */
@Composable
fun ArtworkTile(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    artwork: @Composable (Modifier) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressResponse(interaction, pressedScale = 0.95f)
            .combinedClickable(interaction, indication = null, onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        artwork(Modifier.fillMaxWidth().aspectRatio(1f).clip(if (circular) CircleShape else TileCoverShape))
        Text(
            title,
            Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circular) TextAlign.Center else TextAlign.Start,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                Modifier.padding(horizontal = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** An album tile. Opening it lends its cover to the album page (see [sharedCover]). */
@Composable
fun AlbumTile(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = album.artist) {
    val share = LocalCoverShare.current
    val owner = remember { Any() }
    val key = albumCoverKey(album.id, album.title)
    ArtworkTile(album.title, subtitle, { share?.claim(key, owner); onClick() }, modifier) {
        ArtworkImage(album.artSongId, album.id, it.sharedCover(key, owner))
    }
}

/** A song row: thumbnail, title / subtitle, overflow button. [playing] tints the title. */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = song.artist,
    playing: Boolean = false,
    onMore: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = { ArtworkImage(song.id, song.albumId, Modifier.size(48.dp).clip(RowCoverShape)) },
) {
    MediaRow(
        title = song.title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        titleColor = if (playing) MaterialTheme.colorScheme.primary else Color.Unspecified,
        leading = leading,
        trailing = {
            if (onMore != null) {
                IconButton(onClick = onMore) { SymbolIcon(Symbols.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
    )
}

/** Row with optional leading art, one or two text lines and a trailing slot. */
@Composable
fun MediaRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/** A section title, with a chevron button when the section has a "see all" page. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, onMore: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onMore != null) {
            FilledTonalIconButton(onClick = onMore, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
                SymbolIcon(Symbols.ChevronRight, "See all", size = 20.dp)
            }
        }
    }
}

/** Play + Shuffle as an expressive button group: the pressed button grows and pushes its neighbour. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayShuffleButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    val playInteraction = remember { MutableInteractionSource() }
    val shuffleInteraction = remember { MutableInteractionSource() }
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        customItem(
            buttonGroupContent = {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f).animateWidth(playInteraction).heightIn(min = 56.dp),
                    interactionSource = playInteraction,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                ) {
                    SymbolIcon(Symbols.Play, null, fill = 1f)
                    Spacer(Modifier.width(8.dp))
                    Text("Play", style = MaterialTheme.typography.titleMedium)
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                FilledTonalButton(
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f).animateWidth(shuffleInteraction).heightIn(min = 56.dp),
                    interactionSource = shuffleInteraction,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                ) {
                    SymbolIcon(Symbols.Shuffle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle", style = MaterialTheme.typography.titleMedium)
                }
            },
            menuContent = {},
        )
    }
}

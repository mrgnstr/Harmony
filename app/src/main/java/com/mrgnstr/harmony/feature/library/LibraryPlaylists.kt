package com.mrgnstr.harmony.feature.library

import android.net.Uri
import com.mrgnstr.harmony.core.designsystem.components.TileGrid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mrgnstr.harmony.core.designsystem.components.ArtworkTile
import com.mrgnstr.harmony.core.designsystem.components.TileCoverShape
import com.mrgnstr.harmony.core.designsystem.components.gridCellPadding
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.SortKey
import com.mrgnstr.harmony.core.model.Playlist

private sealed interface Editing {
    data object New : Editing
    data class Edit(val playlist: Playlist) : Editing
}

@Composable
fun PlaylistsTab(viewModel: LibraryViewModel, onOpenPlaylist: (Long) -> Unit) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    val key = viewModel.sorts.collectAsStateWithLifecycle().value.getValue(LibraryTab.Playlists).key
    // Item 0 is the New Playlist tile and Favourites is pinned first; neither has a letter.
    TileGrid(playlists.size + 1, { i ->
        playlists.getOrNull(i - 1)?.takeIf { key == SortKey.Title && !it.isFavourites }?.let { letterOf(it.name) }
    }) { columns ->
        // The first cell: a tile that makes a new playlist.
        item(key = "new", contentType = "new") {
            ArtworkTile("New Playlist", null, { editing = Editing.New }, Modifier.padding(gridCellPadding(0, columns))) { cover ->
                Box(cover.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                    SymbolIcon(Symbols.Add, null, size = 48.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        itemsIndexed(playlists, key = { _, p -> p.id }, contentType = { _, _ -> "playlist" }) { i, playlist ->
            PlaylistTile(
                playlist,
                onClick = { onOpenPlaylist(playlist.id) },
                onPlay = { viewModel.playPlaylist(playlist, shuffled = false) },
                onShuffle = { viewModel.playPlaylist(playlist, shuffled = true) },
                onEdit = { editing = Editing.Edit(playlist) },
                onDelete = { deleting = playlist },
                modifier = itemMotion().padding(gridCellPadding((i + 1) % columns, columns)),
            )
        }
    }

    when (val e = editing) {
        null -> Unit
        Editing.New -> PlaylistEditor(null, { editing = null }) { name, cover, _ ->
            editing = null
            viewModel.createPlaylist(name, cover)
        }
        is Editing.Edit -> PlaylistEditor(e.playlist, { editing = null }) { name, cover, clear ->
            editing = null
            viewModel.editPlaylist(e.playlist, name, cover, clear)
        }
    }
    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("The songs stay in your library.") },
            confirmButton = { TextButton(onClick = { deleting = null; viewModel.deletePlaylist(playlist) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** A playlist tile: tap opens it, long press offers play, shuffle, edit and delete (Favourites can only be played). */
@Composable
private fun PlaylistTile(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier) {
        ArtworkTile(
            playlist.name, "${playlist.songCount} ${if (playlist.songCount == 1) "song" else "songs"}", onClick,
            onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menuOpen = true },
        ) { cover -> PlaylistCover(playlist, cover) }
        DropdownMenu(menuOpen, { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Play") }, leadingIcon = { SymbolIcon(Symbols.Play, null) }, onClick = { menuOpen = false; onPlay() })
            DropdownMenuItem(text = { Text("Shuffle") }, leadingIcon = { SymbolIcon(Symbols.Shuffle, null) }, onClick = { menuOpen = false; onShuffle() })
            if (!playlist.isFavourites) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { SymbolIcon(Symbols.Edit, null) }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { SymbolIcon(Symbols.Delete, null) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

/** New playlist / edit playlist: a cover to pick (tap it) and a name. [onSave] gets the picked image, if any, and whether to drop the custom cover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistEditor(initial: Playlist?, onDismiss: () -> Unit, onSave: (name: String, coverUri: String?, clearCover: Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) { picked = uri.toString(); reset = false }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (initial == null) "New Playlist" else "Edit Playlist", style = MaterialTheme.typography.titleLargeEmphasized)
            Box(
                Modifier.padding(top = 20.dp).size(168.dp).clip(TileCoverShape).clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    picked != null -> AsyncImage(picked, null, Modifier.size(168.dp), contentScale = ContentScale.Crop)
                    initial != null && !reset -> PlaylistCover(initial, Modifier.size(168.dp))
                    else -> Box(Modifier.size(168.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    SymbolIcon(Symbols.Image, "Choose cover", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
            if (picked != null || (initial?.coverPath != null && !reset)) {
                TextButton(onClick = { picked = null; reset = true }) { Text("Use default cover") }
            }
            OutlinedTextField(
                name, { name = it.take(100) },
                Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(name, picked, reset && picked == null) }, enabled = name.isNotBlank()) { Text("Save") }
            }
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.ArtworkImage
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.Song

/**
 * Picks songs from the library to put into a playlist: a search field over the whole library (minus [exclude], what the playlist
 * already holds), a check per song, and one button that adds everything ticked.
 */
@Composable
fun AddSongsSheet(songs: List<Song>, exclude: Set<Long>, onAdd: (List<Song>) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    val shown = remember(songs, exclude, query) {
        val q = query.trim()
        songs.filter { it.id !in exclude && (q.isEmpty() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Add Songs", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Button(onClick = { onAdd(songs.filter { it.id in picked }) }, enabled = picked.isNotEmpty()) {
                    Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}")
                }
            }
            OutlinedTextField(
                query, { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search your songs") },
                leadingIcon = { SymbolIcon(Symbols.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.id }, contentType = { "song" }) { song ->
                    val on = song.id in picked
                    val toggle = { picked = if (on) picked - song.id else picked + song.id }
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = toggle).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkImage(song.id, song.albumId, Modifier.size(48.dp).clip(RowCoverShape))
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(song.artist.takeIf { it.isNotBlank() }, song.album.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(on, { toggle() })
                    }
                }
            }
        }
    }
}

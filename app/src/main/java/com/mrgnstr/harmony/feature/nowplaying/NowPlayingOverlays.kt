@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.mrgnstr.harmony.feature.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.AudioBookmark
import com.mrgnstr.harmony.core.model.JukeboxState
import com.mrgnstr.harmony.core.model.Lyrics
import com.mrgnstr.harmony.core.playback.QueueState

/**
 * Audio Bookmarks & Navidrome Jukebox remote output control sheet.
 */
@Composable
fun BookmarksAndJukeboxSheet(
    bookmark: AudioBookmark?,
    positionMs: Long,
    jukebox: JukeboxState,
    hasJukeboxRole: Boolean,
    onSaveBookmark: (String) -> Unit,
    onJumpToBookmark: (Long) -> Unit,
    onDeleteBookmark: () -> Unit,
    onToggleJukebox: (Boolean) -> Unit,
    onJukeboxCommand: (String, Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    var note by rememberSaveable { mutableStateOf(bookmark?.comment.orEmpty()) }
    var gain by remember(jukebox.gain) { mutableStateOf(jukebox.gain) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Audio Bookmarks section
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbols.Bookmark, null, tint = MaterialTheme.colorScheme.primary, fill = if (bookmark != null) 1f else 0f)
                Spacer(Modifier.width(10.dp))
                Text("Audio Bookmark", style = MaterialTheme.typography.titleLargeEmphasized)
            }
            if (bookmark != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            val sec = (bookmark.positionMs / 1000L).toInt()
                            Text("Saved at %d:%02d".format(sec / 60, sec % 60), style = MaterialTheme.typography.titleMedium)
                            if (bookmark.comment.isNotBlank()) {
                                Text(bookmark.comment, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        FilledTonalButton(onClick = { onJumpToBookmark(bookmark.positionMs); onDismiss() }) {
                            Text("Resume")
                        }
                        IconButton(onClick = onDeleteBookmark) {
                            SymbolIcon(Symbols.Delete, "Delete bookmark")
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("Bookmark note (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onSaveBookmark(note); onDismiss() }) {
                    val sec = (positionMs / 1000L).toInt()
                    Text("Save %d:%02d".format(sec / 60, sec % 60))
                }
            }

            // Navidrome Jukebox remote audio output section
            if (hasJukeboxRole) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SymbolIcon(Symbols.Speaker, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Navidrome Jukebox", style = MaterialTheme.typography.titleMedium)
                                    Text("Play audio directly on the server's speaker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(checked = jukebox.active, onCheckedChange = onToggleJukebox)
                        }
                        if (jukebox.active) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilledTonalButton(onClick = { onJukeboxCommand("start", null) }, modifier = Modifier.weight(1f)) {
                                    SymbolIcon(Symbols.Play, null, fill = 1f)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Start")
                                }
                                FilledTonalButton(onClick = { onJukeboxCommand("stop", null) }, modifier = Modifier.weight(1f)) {
                                    SymbolIcon(Symbols.Pause, null, fill = 1f)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Stop")
                                }
                            }
                            Column {
                                Text("Server Gain: ${(gain * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = gain,
                                    onValueChange = { gain = it },
                                    onValueChangeFinished = { onJukeboxCommand("setGain", gain) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

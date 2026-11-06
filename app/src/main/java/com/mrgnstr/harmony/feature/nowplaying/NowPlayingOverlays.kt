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
 * M3 Expressive Lyrics sheet supporting OpenSubsonic `songLyrics` v2:
 * - Synced auto-scroll + tap-to-seek
 * - Word-by-word (`cue`) karaoke highlight
 * - Multi-vocal (`agentId`) alignment
 * - `translation` and `pronunciation` overlay toggles
 */
@Composable
fun LyricsModalSheet(
    lyrics: Lyrics?,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var showTranslation by rememberSaveable { mutableStateOf(true) }
    var showPronunciation by rememberSaveable { mutableStateOf(true) }
    val activeIdx = remember(lyrics, positionMs) { lyrics?.activeIndex(positionMs) ?: -1 }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0) {
            runCatching { listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0)) }
        }
    }

    val mainAgentIds = remember(lyrics) {
        lyrics?.agents?.filter { it.role.equals("main", ignoreCase = true) }?.map { it.id }?.toSet().orEmpty()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SymbolIcon(Symbols.Lyrics, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Lyrics", style = MaterialTheme.typography.titleLargeEmphasized)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lyrics?.hasPronunciations == true) {
                        FilterChip(
                            selected = showPronunciation,
                            onClick = { showPronunciation = !showPronunciation },
                            label = { Text("Pronunciation") },
                        )
                    }
                    if (lyrics?.hasTranslations == true) {
                        FilterChip(
                            selected = showTranslation,
                            onClick = { showTranslation = !showTranslation },
                            leadingIcon = { SymbolIcon(Symbols.Translate, null, size = 18.dp) },
                            label = { Text("Translation") },
                        )
                    }
                }
            }

            if (lyrics == null || lyrics.lines.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SymbolIcon(Symbols.Lyrics, null, size = 48.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("No lyrics available for this song", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(lyrics.lines) { idx, line ->
                        val isActive = !lyrics.synced || idx == activeIdx
                        val isSecondaryVocal = line.agentId != null && mainAgentIds.isNotEmpty() && line.agentId !in mainAgentIds
                        val align = if (isSecondaryVocal) TextAlign.End else TextAlign.Start
                        val activeColor = MaterialTheme.colorScheme.onSurface
                        val pastOrFutureColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (lyrics.synced && line.startMs >= 0) {
                                        Modifier.clickable { onSeek(line.startMs) }
                                    } else Modifier,
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = if (isSecondaryVocal) Alignment.End else Alignment.Start,
                        ) {
                            if (line.isBreak) {
                                Text("• • •", style = MaterialTheme.typography.titleMedium, color = if (isActive) MaterialTheme.colorScheme.primary else pastOrFutureColor)
                            } else if (isActive && line.cues.isNotEmpty()) {
                                val annotated = buildAnnotatedString {
                                    line.cues.forEachIndexed { cueIdx, cue ->
                                        val wordActive = positionMs >= cue.startMs
                                        withStyle(
                                            SpanStyle(
                                                color = if (wordActive) MaterialTheme.colorScheme.primary else activeColor.copy(alpha = 0.65f),
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        ) {
                                            append(cue.text)
                                        }
                                        if (cueIdx < line.cues.lastIndex && !cue.text.endsWith(" ")) append(" ")
                                    }
                                }
                                Text(
                                    annotated,
                                    style = MaterialTheme.typography.headlineSmallEmphasized,
                                    textAlign = align,
                                )
                            } else {
                                Text(
                                    line.text,
                                    style = if (isActive) MaterialTheme.typography.headlineSmallEmphasized.copy(fontWeight = FontWeight.Bold)
                                    else MaterialTheme.typography.titleMedium,
                                    color = if (isActive) activeColor else pastOrFutureColor,
                                    textAlign = align,
                                )
                            }

                            if (showPronunciation && !line.pronunciation.isNullOrBlank()) {
                                Text(
                                    line.pronunciation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.9f else 0.55f),
                                    textAlign = align,
                                )
                            }
                            if (showTranslation && !line.translation.isNullOrBlank()) {
                                Text(
                                    line.translation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isActive) 0.9f else 0.55f),
                                    textAlign = align,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Queue sheet with Up Next reorder/skip, Cloud Play Queue Sync (`getPlayQueueByIndex` / `savePlayQueueByIndex`),
 * and Sonic Journey (`findSonicPath`) bridge generation.
 */
@Composable
fun QueueModalSheet(
    queue: QueueState,
    hasNavidrome: Boolean,
    onSkipToIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onSonicJourneyTo: (String) -> Unit,
    onSaveCloudQueue: () -> Unit,
    onLoadCloudQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SymbolIcon(Symbols.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        queue.source?.let { "Playing from $it" } ?: "Play Queue",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (hasNavidrome) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onSaveCloudQueue,
                        modifier = Modifier.weight(1f),
                    ) {
                        SymbolIcon(Symbols.CloudSync, null, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Save to Server")
                    }
                    FilledTonalButton(
                        onClick = onLoadCloudQueue,
                        modifier = Modifier.weight(1f),
                    ) {
                        SymbolIcon(Symbols.CloudDone, null, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Load Server Queue")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(queue.items, key = { idx, item -> "$idx:${item.mediaId}" }) { idx, item ->
                    val isCurrent = idx == queue.currentIndex
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().clickable { onSkipToIndex(idx) },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isCurrent) {
                                SymbolIcon(Symbols.GraphicEq, "Playing", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.artist + (if (item.queued) " · Queued" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!isCurrent && hasNavidrome && (item.mediaId.toLongOrNull() ?: 0L) < 0L) {
                                IconButton(onClick = { onSonicJourneyTo(item.mediaId) }) {
                                    SymbolIcon(Symbols.AutoAwesome, "Insert Sonic Journey path to this song", size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (!isCurrent) {
                                IconButton(onClick = { onRemoveIndex(idx) }) {
                                    SymbolIcon(Symbols.Close, "Remove from queue", size = 20.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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

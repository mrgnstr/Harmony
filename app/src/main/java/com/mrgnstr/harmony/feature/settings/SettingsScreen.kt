@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.mrgnstr.harmony.feature.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.appicon.AppIcon
import com.mrgnstr.harmony.core.appicon.AppIconShape
import com.mrgnstr.harmony.core.data.NavidromeRepository
import com.mrgnstr.harmony.core.data.SyncState
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.debug.DebugOptions
import com.mrgnstr.harmony.core.designsystem.components.MediaRow
import com.mrgnstr.harmony.core.designsystem.components.NavidromeConnectSheet
import com.mrgnstr.harmony.core.designsystem.components.PageScaffold
import com.mrgnstr.harmony.core.designsystem.components.SectionHeading
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.theme.FallbackSeed
import com.mrgnstr.harmony.core.download.OfflineDownloadManager
import com.mrgnstr.harmony.core.model.AccentColor
import com.mrgnstr.harmony.core.model.BlurMode
import com.mrgnstr.harmony.core.model.TabBarSide
import com.mrgnstr.harmony.core.model.LibrarySourceMode
import com.mrgnstr.harmony.core.model.LyricsPriority
import com.mrgnstr.harmony.core.model.MissingFileItem
import com.mrgnstr.harmony.core.model.MusicFolder
import com.mrgnstr.harmony.core.model.ReplayGainMode
import com.mrgnstr.harmony.core.mediastore.MediaStoreScanner
import com.mrgnstr.harmony.core.model.LibraryFilters
import com.mrgnstr.harmony.core.model.ScanStatusInfo
import com.mrgnstr.harmony.core.model.ServerAccount
import com.mrgnstr.harmony.core.model.ShareLink
import com.mrgnstr.harmony.core.model.DownloadQuality
import com.mrgnstr.harmony.core.model.StreamQuality
import com.mrgnstr.harmony.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settings: SettingsStore,
    val navidrome: NavidromeRepository,
    val downloads: OfflineDownloadManager,
    private val scanner: MediaStoreScanner,
) : ViewModel() {
    val libraryFilters: StateFlow<LibraryFilters> = settings.libraryFilters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryFilters())
    val collapseBars: StateFlow<Boolean> = settings.collapseBarsOnScroll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setCollapseBars(on: Boolean) { viewModelScope.launch { settings.setCollapseBarsOnScroll(on) } }
    val showAudioInfo: StateFlow<Boolean> = settings.showAudioInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setShowAudioInfo(on: Boolean) { viewModelScope.launch { settings.setShowAudioInfo(on) } }
    val alwaysDark: StateFlow<Boolean> = settings.nowPlayingAlwaysDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setAlwaysDark(on: Boolean) { viewModelScope.launch { settings.setNowPlayingAlwaysDark(on) } }

    val classicNowPlaying: StateFlow<Boolean> = settings.nowPlayingClassic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val classicCoverRadius: StateFlow<Int> = settings.classicCoverRadius
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 15)
    fun setClassicCoverRadius(dp: Int) { viewModelScope.launch { settings.setClassicCoverRadius(dp) } }
    fun setClassicNowPlaying(on: Boolean) { viewModelScope.launch { settings.setNowPlayingClassic(on) } }
    val flowBackground: StateFlow<Boolean> = settings.flowBackground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setFlowBackground(on: Boolean) { viewModelScope.launch { settings.setFlowBackground(on) } }
    fun setMinimumDuration(ms: Long) { viewModelScope.launch { settings.setMinimumDuration(ms) } }
    fun setFolderExcluded(path: String, excluded: Boolean) { viewModelScope.launch { settings.setFolderExcluded(path, excluded) } }
    suspend fun localMusicFolders(): List<String> = runCatching { scanner.musicFolders() }.getOrDefault(emptyList())
    val accounts: StateFlow<List<ServerAccount>> = navidrome.accounts
    val activeAccount: StateFlow<ServerAccount?> = navidrome.activeAccount
    val syncState: StateFlow<SyncState> = navidrome.syncState
    val scanStatus: StateFlow<ScanStatusInfo> = navidrome.scanStatus
    val musicFolders: StateFlow<List<MusicFolder>> = navidrome.musicFolders

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)
    val glassBlur: StateFlow<BlurMode> = settings.glassBlur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlurMode.Auto)
    val coverBlur: StateFlow<BlurMode> = settings.coverBlur
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlurMode.Auto)
    val accent: StateFlow<AccentColor> = settings.accent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccentColor.Wallpaper)
    val tabBarSide: StateFlow<TabBarSide> = settings.tabBarSide
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabBarSide.Left)
    val lyricsPriority: StateFlow<LyricsPriority> = settings.lyricsPriority
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsPriority.FilesFirst)
    val sourceMode: StateFlow<LibrarySourceMode> = settings.librarySourceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySourceMode.All)
    val wifiQuality: StateFlow<StreamQuality> = settings.wifiStreamQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamQuality.Lossless)
    val mobileQuality: StateFlow<StreamQuality> = settings.mobileStreamQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamQuality.Balanced)
    val downloadQuality: StateFlow<DownloadQuality> = settings.downloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadQuality.Original)
    val transcodeFormats: StateFlow<Set<String>> = settings.transcodeAllowlist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), setOf("flac"))
    fun setDownloadQuality(quality: DownloadQuality) { viewModelScope.launch { settings.setDownloadQuality(quality) } }
    fun setTranscodeFormats(formats: Set<String>) { viewModelScope.launch { settings.setTranscodeAllowlist(formats) } }
    val allowlistEnabled: StateFlow<Boolean> = settings.transcodeAllowlistEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setAllowlistEnabled(on: Boolean) { viewModelScope.launch { settings.setTranscodeAllowlistEnabled(on) } }

    val replayGainMode: StateFlow<ReplayGainMode> = settings.replayGainMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReplayGainMode.Smart)
    val replayGainPreampDb: StateFlow<Float> = settings.replayGainPreampDb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)
    val scrobbleToServer: StateFlow<Boolean> = settings.scrobbleToServer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val lastFmInfo: StateFlow<Boolean> = settings.lastFmInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val lastFmApiKey: StateFlow<String> = settings.lastFmApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun syncServerNow() { viewModelScope.launch { navidrome.syncActiveServer() } }
    fun switchServer(id: Long) { viewModelScope.launch { navidrome.switchActiveAccount(id) } }
    fun removeServer(id: Long) { viewModelScope.launch { navidrome.removeAccount(id) } }

    fun toggleMusicFolder(folderId: Int) {
        val account = activeAccount.value ?: return
        val allIds = musicFolders.value.map { it.id }.toSet()
        val current = account.selectedMusicFolders.ifEmpty { allIds }
        val next = if (folderId in current) (current - folderId) else (current + folderId)
        viewModelScope.launch {
            navidrome.setSelectedMusicFolders(if (next.size == allIds.size) emptySet() else next)
        }
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setGlassBlur(mode: BlurMode) { viewModelScope.launch { settings.setGlassBlur(mode) } }
    fun setCoverBlur(mode: BlurMode) { viewModelScope.launch { settings.setCoverBlur(mode) } }
    fun setAccent(accent: AccentColor) { viewModelScope.launch { settings.setAccent(accent) } }
    fun setTabBarSide(side: TabBarSide) { viewModelScope.launch { settings.setTabBarSide(side) } }
    fun setLyricsPriority(priority: LyricsPriority) { viewModelScope.launch { settings.setLyricsPriority(priority) } }
    fun setSourceMode(mode: LibrarySourceMode) { viewModelScope.launch { settings.setLibrarySourceMode(mode) } }
    fun setWifiQuality(quality: StreamQuality) { viewModelScope.launch { settings.setWifiStreamQuality(quality) } }
    fun setMobileQuality(quality: StreamQuality) { viewModelScope.launch { settings.setMobileStreamQuality(quality) } }
    fun setReplayGainMode(mode: ReplayGainMode) { viewModelScope.launch { settings.setReplayGainMode(mode) } }
    fun setReplayGainPreamp(db: Float) { viewModelScope.launch { settings.setReplayGainPreampDb(db) } }
    fun setScrobbleToServer(enabled: Boolean) { viewModelScope.launch { settings.setScrobbleToServer(enabled) } }
    fun setLastFmInfo(enabled: Boolean) { viewModelScope.launch { settings.setLastFmInfo(enabled) } }
    fun setLastFmApiKey(key: String) { viewModelScope.launch { settings.setLastFmApiKey(key) } }

    fun triggerScan(fullScan: Boolean) { viewModelScope.launch { navidrome.triggerServerScan(fullScan) } }
    fun createRadioStation(name: String, url: String, homepage: String?) {
        viewModelScope.launch { navidrome.createRadioStation(name, url, homepage) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenGallery: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()
    val musicFolders by viewModel.musicFolders.collectAsStateWithLifecycle()
    val alwaysDark by viewModel.alwaysDark.collectAsStateWithLifecycle()
    val classicNowPlaying by viewModel.classicNowPlaying.collectAsStateWithLifecycle()
    val classicCoverRadius by viewModel.classicCoverRadius.collectAsStateWithLifecycle()
    val flowBackground by viewModel.flowBackground.collectAsStateWithLifecycle()
    val collapseBars by viewModel.collapseBars.collectAsStateWithLifecycle()
    val showAudioInfo by viewModel.showAudioInfo.collectAsStateWithLifecycle()
    val libraryFilters by viewModel.libraryFilters.collectAsStateWithLifecycle()
    var showLocalFoldersSheet by remember { mutableStateOf(false) }

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val glassBlur by viewModel.glassBlur.collectAsStateWithLifecycle()
    val coverBlur by viewModel.coverBlur.collectAsStateWithLifecycle()
    val tabBarSide by viewModel.tabBarSide.collectAsStateWithLifecycle()
    val accent by viewModel.accent.collectAsStateWithLifecycle()
    val lyricsPriority by viewModel.lyricsPriority.collectAsStateWithLifecycle()
    val sourceMode by viewModel.sourceMode.collectAsStateWithLifecycle()
    val wifiQuality by viewModel.wifiQuality.collectAsStateWithLifecycle()
    val mobileQuality by viewModel.mobileQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val transcodeFormats by viewModel.transcodeFormats.collectAsStateWithLifecycle()
    val allowlistEnabled by viewModel.allowlistEnabled.collectAsStateWithLifecycle()
    val downloadQueue by viewModel.downloads.queue.collectAsStateWithLifecycle()
    var showFormatsDialog by remember { mutableStateOf(false) }
    val rgMode by viewModel.replayGainMode.collectAsStateWithLifecycle()
    val rgPreamp by viewModel.replayGainPreampDb.collectAsStateWithLifecycle()
    val scrobble by viewModel.scrobbleToServer.collectAsStateWithLifecycle()
    val lastFmInfo by viewModel.lastFmInfo.collectAsStateWithLifecycle()
    val lastFmApiKey by viewModel.lastFmApiKey.collectAsStateWithLifecycle()

    var showConnectSheet by remember { mutableStateOf(false) }
    var showQuickConnectAuthorize by remember { mutableStateOf(false) }
    var showAddRadioDialog by remember { mutableStateOf(false) }
    var showSharesSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var appIcon by remember { mutableStateOf(AppIcon.current(context)) }
    var showAppIconSheet by remember { mutableStateOf(false) }
    var showMissingFilesSheet by remember { mutableStateOf(false) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeAccount) {
        downloadedBytes = viewModel.downloads.totalDownloadedBytes()
        if (activeAccount != null) {
            viewModel.navidrome.refreshScanStatus()
        }
    }

    PageScaffold(title = "Settings", onBack = onBack) {
        // 1. Navidrome Server Section
        item(key = "navidrome-heading") {
            SectionHeading("Navidrome & OpenSubsonic")
        }
        item(key = "navidrome-card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val account = activeAccount
                    if (account == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SymbolIcon(Symbols.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Not connected", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Connect a Navidrome or OpenSubsonic server to stream your music and sync libraries.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Button(
                            onClick = { showConnectSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SymbolIcon(Symbols.Cloud, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Connect to Navidrome")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SymbolIcon(Symbols.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.name, style = MaterialTheme.typography.titleMediumEmphasized)
                                Text(
                                    "${account.username} @ ${account.normalizedBaseUrl}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val ver = account.serverVersion.takeIf { it.isNotBlank() }?.let { "v$it · " }.orEmpty()
                                Text(
                                    "${ver}OpenSubsonic (${account.extensions.size} extensions)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (musicFolders.size > 1) {
                            Text("Music Libraries", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                musicFolders.forEach { folder ->
                                    val selected = account.selectedMusicFolders.isEmpty() || folder.id in account.selectedMusicFolders
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.toggleMusicFolder(folder.id) },
                                        label = { Text(folder.name) },
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = viewModel::syncServerNow,
                                enabled = !syncState.syncing,
                                modifier = Modifier.weight(1f),
                            ) {
                                SymbolIcon(Symbols.Refresh, null, size = 18.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(if (syncState.syncing) "Syncing…" else "Sync Library")
                            }
                            OutlinedButton(
                                onClick = { showConnectSheet = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                SymbolIcon(Symbols.Add, null, size = 18.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Add Server")
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = { showQuickConnectAuthorize = true }) {
                                SymbolIcon(Symbols.Key, null, size = 18.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Approve Quick Connect")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { viewModel.removeServer(account.id) }) {
                                Text("Disconnect", color = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (accounts.size > 1) {
                            HorizontalDivider()
                            Text("Saved Servers", style = MaterialTheme.typography.labelMedium)
                            accounts.forEach { acc ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.switchServer(acc.id) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(acc.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    if (acc.active) SymbolIcon(Symbols.Check, "Active", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local library filters (never touch Navidrome songs: LibrarySync only indexes serverId 0)
        item(key = "local-library-heading") {
            SectionHeading("Local Library")
        }
        item(key = "local-library-card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    MediaRow(
                        title = "Music Folders",
                        subtitle = if (libraryFilters.excludedFolders.isEmpty()) "All folders on this device"
                        else "${libraryFilters.excludedFolders.size} excluded",
                        onClick = { showLocalFoldersSheet = true },
                        trailing = { SymbolIcon(Symbols.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp)) },
                    )
                    // Commit on release: every change re-scans MediaStore.
                    var seconds by remember(libraryFilters.minimumDurationMs) {
                        mutableFloatStateOf((libraryFilters.minimumDurationMs / 1000L).toFloat().coerceIn(0f, 300f))
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            "Minimum Song Length: " + if (seconds.roundToInt() == 0) "Off" else formatMinimumLength(seconds.roundToInt()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = seconds,
                            onValueChange = { seconds = (it / 5f).roundToInt() * 5f },
                            onValueChangeFinished = { viewModel.setMinimumDuration(seconds.roundToInt() * 1000L) },
                            valueRange = 0f..300f,
                        )
                    }
                }
            }
        }

        // 2. Streaming, Transcoding & ReplayGain Section
        item(key = "streaming-heading") {
            SectionHeading("Streaming, Transcoding & ReplayGain")
        }
        item(key = "streaming-card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DropdownSettingRow(
                        title = "Library Source",
                        subtitle = sourceMode.label,
                        options = LibrarySourceMode.entries,
                        selected = sourceMode,
                        optionLabel = { it.label },
                        onSelect = viewModel::setSourceMode,
                    )
                    DropdownSettingRow(
                        title = "Wi-Fi Stream Quality",
                        subtitle = wifiQuality.label,
                        options = StreamQuality.entries,
                        selected = wifiQuality,
                        optionLabel = { it.label },
                        onSelect = viewModel::setWifiQuality,
                    )
                    DropdownSettingRow(
                        title = "Mobile Data Stream Quality",
                        subtitle = mobileQuality.label,
                        options = StreamQuality.entries,
                        selected = mobileQuality,
                        optionLabel = { it.label },
                        onSelect = viewModel::setMobileQuality,
                    )
                    DropdownSettingRow(
                        title = "Download Quality",
                        subtitle = downloadQuality.label,
                        options = DownloadQuality.entries,
                        selected = downloadQuality,
                        optionLabel = { it.label },
                        onSelect = viewModel::setDownloadQuality,
                    )
                    if (downloadQuality != DownloadQuality.Original || wifiQuality.maxBitRateKbps > 0 || mobileQuality.maxBitRateKbps > 0) {
                        // Tapping the row picks the formats; the switch on its own side of the divider turns the allowlist off.
                        MediaRow(
                            title = "Transcoding Allowlist",
                            subtitle = if (allowlistEnabled) {
                                transcodeFormats.takeIf { it.isNotEmpty() }
                                    ?.joinToString(", ") { if (it == "dolby atmos") "Dolby Atmos" else it.uppercase() }
                                    ?: "No formats: everything stays as it is"
                            } else "All songs are transcoded",
                            onClick = { showFormatsDialog = true },
                            trailing = {
                                VerticalDivider(Modifier.height(32.dp))
                                Switch(checked = allowlistEnabled, onCheckedChange = viewModel::setAllowlistEnabled, modifier = Modifier.padding(horizontal = 12.dp))
                            },
                        )
                    }
                    DropdownSettingRow(
                        title = "ReplayGain Normalization",
                        subtitle = rgMode.label,
                        options = ReplayGainMode.entries,
                        selected = rgMode,
                        optionLabel = { it.label },
                        onSelect = viewModel::setReplayGainMode,
                    )
                    if (rgMode != ReplayGainMode.Off) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text(
                                "ReplayGain Preamp: ${if (rgPreamp >= 0) "+" else ""}${rgPreamp.roundToInt()} dB",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Slider(
                                value = rgPreamp,
                                onValueChange = { viewModel.setReplayGainPreamp(it.roundToInt().toFloat()) },
                                valueRange = -12f..12f,
                            )
                        }
                    }
                    SwitchSettingRow(
                        title = "Scrobble & Report Playback",
                        subtitle = "Updates Now Playing, play counts, Last.fm & ListenBrainz",
                        checked = scrobble,
                        onCheckedChange = viewModel::setScrobbleToServer,
                    )
                }
            }
        }

        // Artist & album info for local music
        item(key = "info-heading") {
            SectionHeading("Artist & Album Info")
        }
        item(key = "info-card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SwitchSettingRow(
                        title = "Fetch info from Last.fm",
                        subtitle = "Biographies and album texts for music on this device. Artist and album names are sent to Last.fm; off by default",
                        checked = lastFmInfo,
                        onCheckedChange = viewModel::setLastFmInfo,
                    )
                    if (lastFmInfo) {
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        var key by remember(lastFmApiKey) { mutableStateOf(lastFmApiKey) }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { key = it; viewModel.setLastFmApiKey(it) },
                                label = { Text("Last.fm API key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Last.fm only answers apps that have a key. A free personal one takes a minute to create.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { runCatching { uriHandler.openUri("https://www.last.fm/api/account/create") } }) {
                                Text("Create an API key")
                            }
                        }
                    }
                }
            }
        }

        // 3. Radio, Shares & Offline Storage Section
        if (activeAccount != null) {
            item(key = "features-heading") {
                SectionHeading("Radio, Shares & Offline Storage")
            }
            item(key = "features-card") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        MediaRow(
                            title = "Add Internet Radio Station",
                            subtitle = "Save a live stream URL to your Navidrome server",
                            leading = { SymbolIcon(Symbols.Radio, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { showAddRadioDialog = true },
                        )
                        MediaRow(
                            title = "Manage Public Shares",
                            subtitle = "View visit counts or revoke shared links",
                            leading = { SymbolIcon(Symbols.Share, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = { showSharesSheet = true },
                        )
                        val mb = downloadedBytes / (1024 * 1024)
                        MediaRow(
                            title = "Clear Offline Downloads",
                            subtitle = if (downloadQueue.total > 0) "Downloading ${downloadQueue.done + 1} of ${downloadQueue.total}" else "$mb MB used on this device",
                            leading = { SymbolIcon(Symbols.OfflinePin, null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                scope.launch {
                                    viewModel.downloads.clearAllDownloads()
                                    downloadedBytes = 0L
                                }
                            },
                        )
                    }
                }
            }
        }

        // 4. Navidrome Admin & Maintenance
        if (activeAccount?.isAdmin == true) {
            item(key = "admin-heading") {
                SectionHeading("Server Administration")
            }
            item(key = "admin-card") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SymbolIcon(Symbols.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (scanStatus.scanning) "Scanner running (${scanStatus.count} files)"
                                    else "Library Scanner Idle (${scanStatus.count} files indexed)",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                scanStatus.error?.takeIf { it.isNotBlank() }?.let { err ->
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.triggerScan(fullScan = false) },
                                enabled = !scanStatus.scanning,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Quick Scan")
                            }
                            OutlinedButton(
                                onClick = { viewModel.triggerScan(fullScan = true) },
                                enabled = !scanStatus.scanning,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Full Scan")
                            }
                        }
                        MediaRow(
                            title = "Inspect & Purge Missing Files",
                            subtitle = "View tracks missing from disk on the server",
                            onClick = { showMissingFilesSheet = true },
                        )
                    }
                }
            }
        }

        // 5. Appearance & Design System
        item(key = "appearance-heading") {
            SectionHeading("Appearance")
        }
        item(key = "appearance-card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DropdownSettingRow(
                        title = "Theme",
                        subtitle = themeMode.name,
                        options = ThemeMode.entries,
                        selected = themeMode,
                        optionLabel = { it.name },
                        onSelect = viewModel::setThemeMode,
                    )
                    // Live blur of any kind needs Android 12 (API 31): below it both settings do nothing, so they are greyed out.
                    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    DropdownSettingRow(
                        title = "Floating Chrome Blur",
                        subtitle = if (canBlur) "${glassBlur.name} · bars, mini player, back animation and lyrics" else "Needs Android 12 or newer",
                        options = BlurMode.entries,
                        selected = glassBlur,
                        optionLabel = { it.name },
                        onSelect = viewModel::setGlassBlur,
                        enabled = canBlur,
                    )
                    DropdownSettingRow(
                        title = "Live Cover Blur",
                        subtitle = when {
                            !canBlur -> "Needs Android 12 or newer"
                            // The blur around the Now Playing cover and the artist image is a shader: Android 13.
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "${coverBlur.name} · album covers and lyrics only on Android 12"
                            else -> "${coverBlur.name} · Now Playing, artist and album artwork"
                        },
                        options = BlurMode.entries,
                        selected = coverBlur,
                        optionLabel = { it.name },
                        onSelect = viewModel::setCoverBlur,
                        enabled = canBlur,
                    )
                    SwitchSettingRow(
                        title = "Collapse Bars on Scroll",
                        subtitle = "The tab bar and mini player shrink while a page scrolls down",
                        checked = collapseBars,
                        onCheckedChange = viewModel::setCollapseBars,
                    )
                    SwitchSettingRow(
                        title = "Audio Info in Now Playing",
                        subtitle = "The codec and quality pill above the seek bar",
                        checked = showAudioInfo,
                        onCheckedChange = viewModel::setShowAudioInfo,
                    )
                    SwitchSettingRow(
                        title = "Classic Now Playing",
                        subtitle = "A smaller cover on a plain surface, coloured from the cover like the album page",
                        checked = classicNowPlaying,
                        onCheckedChange = viewModel::setClassicNowPlaying,
                    )
                    if (classicNowPlaying) {
                        var radius by remember(classicCoverRadius) { mutableFloatStateOf(classicCoverRadius.toFloat()) }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text("Cover Corner Radius: ${radius.roundToInt()} dp", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = radius,
                                onValueChange = { radius = it.roundToInt().toFloat() },
                                onValueChangeFinished = { viewModel.setClassicCoverRadius(radius.roundToInt()) },
                                valueRange = 0f..48f,
                            )
                        }
                    }
                    SwitchSettingRow(
                        title = "Animated Background",
                        subtitle = "The cover's colours flowing behind the lyrics and queue, and behind the whole classic screen, like Apple Music",
                        checked = flowBackground,
                        onCheckedChange = viewModel::setFlowBackground,
                    )
                    SwitchSettingRow(
                        title = "Always Dark Now Playing",
                        subtitle = "Always light controls and a dimmed bottom, even on bright covers",
                        checked = alwaysDark,
                        onCheckedChange = viewModel::setAlwaysDark,
                    )
                    MediaRow(
                        title = "Accent Colour",
                        subtitle = if (accent == AccentColor.Wallpaper) {
                            if (Build.VERSION.SDK_INT >= 31) "From your wallpaper" else "Default"
                        } else "${accent.displayLabel()}, on pages without a colour of their own",
                        onClick = {},
                    )
                    AccentSwatches(
                        selected = accent,
                        onSelect = viewModel::setAccent,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    )
                    MediaRow(
                        title = "App Icon",
                        subtitle = "${appIcon.shape.label}, ${appIcon.color.displayLabel()}",
                        leading = { AppIconImage(appIcon, Modifier.size(40.dp)) },
                        onClick = { showAppIconSheet = true },
                    )
                    // Only tablets choose; phones' bar is locked to their bottom edge.
                    if (com.mrgnstr.harmony.core.designsystem.LocalWindowLayout.current.isTablet) {
                        DropdownSettingRow(
                            title = "Tab Bar Position",
                            subtitle = tabBarSide.label,
                            options = TabBarSide.entries,
                            selected = tabBarSide,
                            optionLabel = { it.label },
                            onSelect = viewModel::setTabBarSide,
                        )
                    }
                }
            }
        }

        // 6. Lyrics
        item(key = "lyrics-heading") {
            SectionHeading("Lyrics")
        }
        item(key = "lyrics-card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    DropdownSettingRow(
                        title = "Lyrics source",
                        subtitle = lyricsPriority.description,
                        options = LyricsPriority.entries,
                        selected = lyricsPriority,
                        optionLabel = { it.label },
                        onSelect = viewModel::setLyricsPriority,
                    )
                }
            }
        }

        // 7. Debug options: only in builds that have them (see DebugOptions).
        if (DebugOptions.enabled) {
            item(key = "debug-heading") {
                SectionHeading("Debug")
            }
            item(key = "debug-card") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        if (onOpenGallery != null) {
                            MediaRow(
                                title = "Design gallery",
                                subtitle = "Colour engine, motion and components (M2)",
                                leading = { SymbolIcon(Symbols.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = onOpenGallery,
                            )
                        }
                        SwitchSettingRow(
                            title = "Switches over the cover",
                            subtitle = "Now Playing; swipe sideways on the cover to hide or show them",
                            checked = DebugOptions.overlayVisible,
                            onCheckedChange = { DebugOptions.overlayVisible = it },
                        )
                        DebugOptions.all.forEach { flag ->
                            SwitchSettingRow(
                                title = flag.label,
                                subtitle = if (flag.on) "On" else "Off",
                                checked = flag.on,
                                onCheckedChange = { flag.on = it },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConnectSheet) {
        NavidromeConnectSheet(onDismiss = { showConnectSheet = false })
    }

    if (showFormatsDialog) {
        TranscodeFormatsDialog(
            scan = viewModel.downloads,
            selected = transcodeFormats,
            onChange = viewModel::setTranscodeFormats,
            onDismiss = { showFormatsDialog = false },
        )
    }
    if (showQuickConnectAuthorize) {
        QuickConnectAuthorizeDialog(
            onDismiss = { showQuickConnectAuthorize = false },
            onAuthorize = { code -> viewModel.navidrome.authorizeQuickConnectCode(code) },
        )
    }

    if (showAddRadioDialog) {
        AddRadioStationDialog(
            onDismiss = { showAddRadioDialog = false },
            onSave = { name, url, home ->
                showAddRadioDialog = false
                viewModel.createRadioStation(name, url, home)
            },
        )
    }

    if (showAppIconSheet) {
        AppIconSheet(
            current = appIcon,
            accent = accent,
            onApply = { AppIcon.select(context, it); appIcon = it },
            onDismiss = { showAppIconSheet = false },
        )
    }

    if (showSharesSheet) {
        ManageSharesSheet(
            navidrome = viewModel.navidrome,
            onDismiss = { showSharesSheet = false },
        )
    }

    if (showLocalFoldersSheet) {
        LocalFoldersSheet(
            excluded = libraryFilters.excludedFolders,
            loadFolders = viewModel::localMusicFolders,
            onToggle = viewModel::setFolderExcluded,
            onDismiss = { showLocalFoldersSheet = false },
        )
    }

    if (showMissingFilesSheet) {
        MissingFilesSheet(
            navidrome = viewModel.navidrome,
            onDismiss = { showMissingFilesSheet = false },
        )
    }
}

/** "Wallpaper" means nothing below API 31, where there are no wallpaper colours: the fixed palette is just the default. */
private fun AccentColor.displayLabel() =
    if (this == AccentColor.Wallpaper && Build.VERSION.SDK_INT < 31) "Default" else label

/** The colour that stands for an accent in a swatch. */
@Composable
private fun AccentColor.swatch(): Color = when {
    seed != null -> Color(seed!!)
    Build.VERSION.SDK_INT >= 31 -> colorResource(android.R.color.system_accent1_500)
    else -> FallbackSeed
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(selected: AccentColor, onSelect: (AccentColor) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        AccentColor.entries.forEach { accent ->
            val on = accent == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.swatch())
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(accent) }
                    .semantics { contentDescription = accent.displayLabel() },
            ) {
                when {
                    on -> SymbolIcon(Symbols.Check, null, tint = Color.White)
                    // The wallpaper swatch has no colour of its own to be told by.
                    accent == AccentColor.Wallpaper -> SymbolIcon(Symbols.Palette, null, tint = Color.White)
                }
            }
        }
    }
}

/** A launcher icon as the system draws it (adaptive mask; wallpaper colours for the wallpaper variant). */
@Composable
private fun AppIconImage(icon: AppIcon, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(icon) {
        icon.resource(context).takeIf { it != 0 }
            ?.let { ContextCompat.getDrawable(context, it) }?.toBitmap(192, 192)?.asImageBitmap()
    }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = modifier)
}

/** Shape and colour are chosen first and applied together: switching the launcher icon restarts the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppIconSheet(
    current: AppIcon,
    accent: AccentColor,
    onApply: (AppIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("App Icon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 5,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                AppIconShape.entries.forEach { shape ->
                    val on = shape == picked.shape
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { picked = picked.copy(shape = shape) },
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(68.dp).padding(vertical = 8.dp),
                        ) {
                            AppIconImage(AppIcon(shape, picked.color), Modifier.size(52.dp))
                            Text(
                                shape.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            Text(
                "Colour",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp).padding(top = 16.dp, bottom = 8.dp),
            )
            AccentSwatches(
                selected = picked.color,
                onSelect = { picked = picked.copy(color = it) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (accent != AccentColor.Wallpaper && picked.color != accent) {
                TextButton(onClick = { picked = picked.copy(color = accent) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Match the accent colour (${accent.label})")
                }
            }
            Text(
                "The app restarts to change its icon. Your launcher may take a moment to show it, and may move it on the home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp).padding(top = 12.dp, bottom = 12.dp),
            )
            Button(
                onClick = { onApply(picked) },
                enabled = picked != current,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) { Text("Apply") }
        }
    }
}

@Composable
private fun <T> DropdownSettingRow(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        MediaRow(
            title = title,
            subtitle = subtitle,
            onClick = { if (enabled) open = true },
            dimmed = !enabled,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    trailingIcon = { if (opt == selected) SymbolIcon(Symbols.Check, null) },
                    onClick = { onSelect(opt); open = false },
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MediaRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.padding(end = 12.dp))
        },
    )
}

/** The formats the server really has (scanned the first time), each with a tick for "convert on download". */
@Composable
private fun TranscodeFormatsDialog(
    scan: OfflineDownloadManager,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val formats by scan.formats.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { scan.scanFormats() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transcoding Allowlist") },
        text = {
            Column {
                Text(
                    "Songs in a ticked format are converted when streamed or downloaded (see the quality settings); every other format is sent as it is.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (formats == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                    Text("Checking the library's formats…", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
                formats?.forEach { (name, count) ->
                    val key = name.lowercase()
                    MediaRow(
                        title = name,
                        subtitle = "$count ${if (count == 1) "song" else "songs"}",
                        onClick = { onChange(if (key in selected) selected - key else selected + key) },
                        trailing = { Checkbox(checked = key in selected, onCheckedChange = { onChange(if (it) selected + key else selected - key) }) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun QuickConnectAuthorizeDialog(
    onDismiss: () -> Unit,
    onAuthorize: suspend (String) -> Any,
) {
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approve Quick Connect") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the 6-digit code shown on the other device:")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(10); status = null },
                    label = { Text("6-Digit Code") },
                    singleLine = true,
                )
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { onAuthorize(code) }
                            .onSuccess { onDismiss() }
                            .onFailure { status = it.message ?: "Invalid code" }
                    }
                },
                enabled = code.isNotBlank(),
            ) { Text("Approve") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddRadioStationDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, streamUrl: String, homePageUrl: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var homepage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Internet Radio Station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Station Name") }, singleLine = true)
                OutlinedTextField(value = streamUrl, onValueChange = { streamUrl = it }, label = { Text("Stream URL (http/https)") }, singleLine = true)
                OutlinedTextField(value = homepage, onValueChange = { homepage = it }, label = { Text("Homepage URL (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), streamUrl.trim(), homepage.trim().takeIf { it.isNotEmpty() }) },
                enabled = name.isNotBlank() && streamUrl.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ManageSharesSheet(
    navidrome: NavidromeRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shares by remember { mutableStateOf<List<ShareLink>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shares = navidrome.getShares()
        loaded = true
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Public Shares", style = MaterialTheme.typography.titleLargeEmphasized)
            if (loaded && shares.isEmpty()) {
                Text("No active public shares.", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shares, key = { it.id }) { share ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(share.description.ifBlank { share.id }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${share.visitCount} visits · ${share.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(
                                    onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, share.url)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Share link"))
                                    },
                                ) {
                                    SymbolIcon(Symbols.Share, "Share URL")
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            navidrome.deleteShare(share.id)
                                            shares = shares.filterNot { it.id == share.id }
                                        }
                                    },
                                ) {
                                    SymbolIcon(Symbols.Delete, "Delete share")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingFilesSheet(
    navidrome: NavidromeRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf<List<MissingFileItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        missing = navidrome.getMissingFiles()
        loaded = true
    }

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
                Text("Missing Files (${missing.size})", style = MaterialTheme.typography.titleLargeEmphasized)
                if (missing.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                if (navidrome.purgeMissingFiles()) missing = emptyList()
                            }
                        },
                    ) {
                        Text("Purge All")
                    }
                }
            }
            if (loaded && missing.isEmpty()) {
                Text("No missing files on the server.", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(missing, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${item.artist} - ${item.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinimumLength(seconds: Int): String =
    if (seconds < 60) "$seconds s" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} min"

/** Every folder MediaStore has music in (plus excluded ones that have since gone), each with a switch. */
@Composable
private fun LocalFoldersSheet(
    excluded: Set<String>,
    loadFolders: suspend () -> List<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var found by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { found = loadFolders() }
    var query by remember { mutableStateOf("") }
    val folders = found?.let { all ->
        (all + excluded).distinct().sorted().filter { query.isBlank() || it.removePrefix("/storage/emulated/0/").contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Music Folders", style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                "Turn off folders to hide their songs from the local library. Files are never deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search folders") },
                leadingIcon = { SymbolIcon(Symbols.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                folders == null -> Text("Loading…", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                folders.isEmpty() -> Text(if (query.isBlank()) "No music folders found." else "No folders match.", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(folders, key = { it }) { path ->
                        val enabled = path !in excluded
                        MediaRow(
                            title = path.substringAfterLast('/'),
                            subtitle = path.removePrefix("/storage/emulated/0/").removePrefix("/sdcard/"),
                            onClick = { onToggle(path, enabled) },
                            trailing = { Switch(checked = enabled, onCheckedChange = { onToggle(path, enabled) }, modifier = Modifier.padding(end = 12.dp)) },
                        )
                    }
                }
            }
        }
    }
}

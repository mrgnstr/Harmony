package com.mrgnstr.harmony.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mrgnstr.harmony.core.model.DownloadQuality
import com.mrgnstr.harmony.core.model.LibraryFilters
import com.mrgnstr.harmony.core.model.LibrarySort
import com.mrgnstr.harmony.core.model.LibrarySourceMode
import com.mrgnstr.harmony.core.model.BlurMode
import com.mrgnstr.harmony.core.model.AccentColor
import com.mrgnstr.harmony.core.model.TabBarSide
import com.mrgnstr.harmony.core.model.LyricsPriority
import com.mrgnstr.harmony.core.model.ReplayGainMode
import com.mrgnstr.harmony.core.model.StreamQuality
import com.mrgnstr.harmony.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** The user's settings, kept in DataStore. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[themeKey]?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.System
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    private val glassBlurKey = stringPreferencesKey("glass_blur")
    private val coverBlurKey = stringPreferencesKey("cover_blur")

    private fun blurModeOf(saved: String?) = BlurMode.entries.firstOrNull { it.name == saved } ?: BlurMode.Auto

    /** Live blur behind the tab bar, mini player and buttons. */
    val glassBlur: Flow<BlurMode> = context.settingsDataStore.data.map { blurModeOf(it[glassBlurKey]) }

    /** Live blur around the Now Playing cover. */
    val coverBlur: Flow<BlurMode> = context.settingsDataStore.data.map { blurModeOf(it[coverBlurKey]) }

    suspend fun setGlassBlur(mode: BlurMode) { context.settingsDataStore.edit { it[glassBlurKey] = mode.name } }

    suspend fun setCoverBlur(mode: BlurMode) { context.settingsDataStore.edit { it[coverBlurKey] = mode.name } }

    private val accentKey = stringPreferencesKey("accent_color")

    /** The accent that replaces the wallpaper's colours (see [AccentColor]). */
    val accent: Flow<AccentColor> = context.settingsDataStore.data.map { prefs ->
        AccentColor.entries.firstOrNull { it.name == prefs[accentKey] } ?: AccentColor.Wallpaper
    }

    suspend fun setAccent(accent: AccentColor) { context.settingsDataStore.edit { it[accentKey] = accent.name } }

    private val tabBarSideKey = stringPreferencesKey("tab_bar_side")

    /** Which side the tab bar stands on, on tablets (phones are locked to their bottom edge, whatever this says). */
    val tabBarSide: Flow<TabBarSide> = context.settingsDataStore.data.map { prefs ->
        TabBarSide.entries.firstOrNull { it.name == prefs[tabBarSideKey] } ?: TabBarSide.Left
    }

    suspend fun setTabBarSide(side: TabBarSide) { context.settingsDataStore.edit { it[tabBarSideKey] = side.name } }

    private val nowPlayingAlwaysDarkKey = booleanPreferencesKey("now_playing_always_dark")

    /** Now Playing keeps light controls and a dimmed bottom even on a bright cover. Off by default. */
    val nowPlayingAlwaysDark: Flow<Boolean> = context.settingsDataStore.data.map { it[nowPlayingAlwaysDarkKey] ?: false }

    suspend fun setNowPlayingAlwaysDark(on: Boolean) { context.settingsDataStore.edit { it[nowPlayingAlwaysDarkKey] = on } }

    private val nowPlayingClassicKey = booleanPreferencesKey("now_playing_classic")

    /** Now Playing as a traditional player: an inset cover on a plain Material surface, coloured from the cover like the album page. Off by default. */
    val nowPlayingClassic: Flow<Boolean> = context.settingsDataStore.data.map { it[nowPlayingClassicKey] ?: false }

    suspend fun setNowPlayingClassic(on: Boolean) { context.settingsDataStore.edit { it[nowPlayingClassicKey] = on } }

    private val classicCoverRadiusKey = intPreferencesKey("now_playing_classic_cover_radius")

    /** Corner radius (dp) of the classic Now Playing cover. */
    val classicCoverRadius: Flow<Int> = context.settingsDataStore.data.map { (it[classicCoverRadiusKey] ?: 15).coerceIn(0, 48) }

    suspend fun setClassicCoverRadius(dp: Int) { context.settingsDataStore.edit { it[classicCoverRadiusKey] = dp.coerceIn(0, 48) } }

    private val collapseBarsKey = booleanPreferencesKey("collapse_bars_on_scroll")

    /** The tab bar and mini player minimize while pages scroll down. On by default. */
    val collapseBarsOnScroll: Flow<Boolean> = context.settingsDataStore.data.map { it[collapseBarsKey] ?: true }

    suspend fun setCollapseBarsOnScroll(on: Boolean) { context.settingsDataStore.edit { it[collapseBarsKey] = on } }

    private val showAudioInfoKey = booleanPreferencesKey("show_audio_info")

    /** The codec / quality pill above the seek bar in full-screen Now Playing. On by default. */
    val showAudioInfo: Flow<Boolean> = context.settingsDataStore.data.map { it[showAudioInfoKey] ?: true }

    suspend fun setShowAudioInfo(on: Boolean) { context.settingsDataStore.edit { it[showAudioInfoKey] = on } }

    private val lyricsPriorityKey = stringPreferencesKey("lyrics_priority")

    /** Lyrics files beside a song or the lyrics in its tags first (files by default: they carry word timing and voices). */
    val lyricsPriority: Flow<LyricsPriority> = context.settingsDataStore.data.map { prefs ->
        LyricsPriority.entries.firstOrNull { it.name == prefs[lyricsPriorityKey] } ?: LyricsPriority.FilesFirst
    }

    suspend fun setLyricsPriority(priority: LyricsPriority) { context.settingsDataStore.edit { it[lyricsPriorityKey] = priority.name } }

    private val systemBarFadeKey = booleanPreferencesKey("system_bar_fade")

    /** Content fading into the page colour behind the status and navigation bars (on by default). */
    val systemBarFade: Flow<Boolean> = context.settingsDataStore.data.map { it[systemBarFadeKey] ?: true }

    suspend fun setSystemBarFade(on: Boolean) { context.settingsDataStore.edit { it[systemBarFadeKey] = on } }

    private fun sortKey(tab: String) = stringPreferencesKey("sort_$tab")

    /** The saved ordering of each library tab, by tab name; tabs never sorted are missing. */
    val librarySorts: Flow<Map<String, LibrarySort>> = context.settingsDataStore.data.map { prefs ->
        buildMap { for ((key, value) in prefs.asMap()) if (key.name.startsWith("sort_")) LibrarySort.decode(value as? String)?.let { put(key.name.removePrefix("sort_"), it) } }
    }

    suspend fun setLibrarySort(tab: String, sort: LibrarySort) { context.settingsDataStore.edit { it[sortKey(tab)] = sort.encode() } }

    private val minimumDurationKey = longPreferencesKey("minimum_duration_ms")
    private val excludedFoldersKey = stringSetPreferencesKey("excluded_folders")

    val libraryFilters: Flow<LibraryFilters> = context.settingsDataStore.data.map { prefs ->
        LibraryFilters(prefs[minimumDurationKey]?.coerceAtLeast(0L) ?: 30_000L, prefs[excludedFoldersKey] ?: emptySet())
    }

    suspend fun setMinimumDuration(ms: Long) {
        context.settingsDataStore.edit { it[minimumDurationKey] = ms.coerceAtLeast(0L) }
    }

    suspend fun setFolderExcluded(path: String, excluded: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val folders = prefs[excludedFoldersKey] ?: emptySet()
            prefs[excludedFoldersKey] = if (excluded) folders + path else folders - path
        }
    }

    // --- Navidrome / Streaming / ReplayGain Settings ---

    private val sourceModeKey = stringPreferencesKey("library_source_mode")
    private val wifiQualityKey = stringPreferencesKey("wifi_stream_quality")
    private val mobileQualityKey = stringPreferencesKey("mobile_stream_quality")
    private val downloadQualityKey = stringPreferencesKey("download_quality")
    private val transcodeAllowlistKey = stringSetPreferencesKey("download_transcode_formats")
    private val transcodeAllowlistEnabledKey = booleanPreferencesKey("transcode_allowlist_enabled")
    private val replayGainModeKey = stringPreferencesKey("replay_gain_mode")
    private val nightcoreKey = booleanPreferencesKey("nightcore")
    private val nightcoreSpeedKey = floatPreferencesKey("nightcore_speed")
    private val replayGainPreampKey = floatPreferencesKey("replay_gain_preamp_db")
    private val scrobbleKey = booleanPreferencesKey("scrobble_to_server")
    private val lastFmInfoKey = booleanPreferencesKey("last_fm_info")
    private val lastFmApiKeyKey = stringPreferencesKey("last_fm_api_key")

    val librarySourceMode: Flow<LibrarySourceMode> = context.settingsDataStore.data.map { prefs ->
        prefs[sourceModeKey]?.let { s -> LibrarySourceMode.entries.firstOrNull { it.name == s } } ?: LibrarySourceMode.All
    }

    suspend fun setLibrarySourceMode(mode: LibrarySourceMode) {
        context.settingsDataStore.edit { it[sourceModeKey] = mode.name }
    }

    val wifiStreamQuality: Flow<StreamQuality> = context.settingsDataStore.data.map { prefs ->
        prefs[wifiQualityKey]?.let { s -> StreamQuality.entries.firstOrNull { it.name == s } } ?: StreamQuality.Lossless
    }

    suspend fun setWifiStreamQuality(quality: StreamQuality) {
        context.settingsDataStore.edit { it[wifiQualityKey] = quality.name }
    }

    val mobileStreamQuality: Flow<StreamQuality> = context.settingsDataStore.data.map { prefs ->
        prefs[mobileQualityKey]?.let { s -> StreamQuality.entries.firstOrNull { it.name == s } } ?: StreamQuality.Balanced
    }

    suspend fun setMobileStreamQuality(quality: StreamQuality) {
        context.settingsDataStore.edit { it[mobileQualityKey] = quality.name }
    }

    val downloadQuality: Flow<DownloadQuality> = context.settingsDataStore.data.map { prefs ->
        prefs[downloadQualityKey]?.let { s -> DownloadQuality.entries.firstOrNull { it.name == s } } ?: DownloadQuality.Original
    }

    suspend fun setDownloadQuality(quality: DownloadQuality) {
        context.settingsDataStore.edit { it[downloadQualityKey] = quality.name }
    }

    /** Lower-case names of the formats the server may convert, for streaming and downloads (any other format is sent as it is). FLAC until chosen. */
    val transcodeAllowlist: Flow<Set<String>> = context.settingsDataStore.data.map { it[transcodeAllowlistKey] ?: setOf("flac") }

    suspend fun setTranscodeAllowlist(formats: Set<String>) {
        context.settingsDataStore.edit { it[transcodeAllowlistKey] = formats }
    }

    /** Off: every format is converted, Dolby Atmos included, and the allowlist is ignored. */
    val transcodeAllowlistEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[transcodeAllowlistEnabledKey] != false }

    suspend fun setTranscodeAllowlistEnabled(on: Boolean) {
        context.settingsDataStore.edit { it[transcodeAllowlistEnabledKey] = on }
    }

    /** Nightcore: every song plays [com.mrgnstr.harmony.core.playback.NightcoreSpeed] times faster with its pitch raised by the same factor. */
    val nightcore: Flow<Boolean> = context.settingsDataStore.data.map { it[nightcoreKey] == true }

    suspend fun setNightcore(on: Boolean) {
        context.settingsDataStore.edit { it[nightcoreKey] = on }
    }

    /** The speed (and pitch factor) nightcore plays at; [com.mrgnstr.harmony.core.playback.NightcoreSpeed] until the slider moves it. */
    val nightcoreSpeed: Flow<Float> = context.settingsDataStore.data.map { it[nightcoreSpeedKey] ?: com.mrgnstr.harmony.core.playback.NightcoreSpeed }

    suspend fun setNightcoreSpeed(speed: Float) {
        context.settingsDataStore.edit { it[nightcoreSpeedKey] = speed }
    }

    val replayGainMode: Flow<ReplayGainMode> = context.settingsDataStore.data.map { prefs ->
        prefs[replayGainModeKey]?.let { s -> ReplayGainMode.entries.firstOrNull { it.name == s } } ?: ReplayGainMode.Smart
    }

    suspend fun setReplayGainMode(mode: ReplayGainMode) {
        context.settingsDataStore.edit { it[replayGainModeKey] = mode.name }
    }

    val replayGainPreampDb: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        (prefs[replayGainPreampKey] ?: 0f).coerceIn(-12f, 12f)
    }

    suspend fun setReplayGainPreampDb(db: Float) {
        context.settingsDataStore.edit { it[replayGainPreampKey] = db.coerceIn(-12f, 12f) }
    }

    val scrobbleToServer: Flow<Boolean> = context.settingsDataStore.data.map { it[scrobbleKey] ?: true }

    suspend fun setScrobbleToServer(enabled: Boolean) {
        context.settingsDataStore.edit { it[scrobbleKey] = enabled }
    }

    /** Whether artist and album text for local music is fetched from Last.fm (off: nothing leaves the device for it). */
    val lastFmInfo: Flow<Boolean> = context.settingsDataStore.data.map { it[lastFmInfoKey] ?: false }

    suspend fun setLastFmInfo(enabled: Boolean) {
        context.settingsDataStore.edit { it[lastFmInfoKey] = enabled }
    }

    /** The user's own Last.fm API key; Last.fm's API cannot be used without one. */
    val lastFmApiKey: Flow<String> = context.settingsDataStore.data.map { it[lastFmApiKeyKey].orEmpty() }

    suspend fun setLastFmApiKey(key: String) {
        context.settingsDataStore.edit { it[lastFmApiKeyKey] = key.trim() }
    }
}

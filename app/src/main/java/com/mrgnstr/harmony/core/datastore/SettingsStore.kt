package com.mrgnstr.harmony.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mrgnstr.harmony.core.model.LibraryFilters
import com.mrgnstr.harmony.core.model.LibrarySort
import com.mrgnstr.harmony.core.model.BlurMode
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
}

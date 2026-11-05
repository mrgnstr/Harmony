package com.mrgnstr.harmony.app

import com.mrgnstr.harmony.core.model.ArtistReleaseShelf
import com.mrgnstr.harmony.core.model.AlbumReleaseShelf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Home and Library sit in the tab bar; Search is the separate round button beside it, with its own back stack. */
enum class Tab(val label: String) {
    Home("Home"),
    Library("Library"),
    Search("Search"),
}

/** A screen inside a tab. Features add their keys as they land. */
sealed interface Dest {
    data class Root(val tab: Tab) : Dest
    data class Album(val albumId: Long, val title: String) : Dest
    data class Artist(val name: String) : Dest
    data class ArtistSongs(val name: String, val accent: Int? = null) : Dest
    data class ArtistInfo(val name: String, val accent: Int? = null) : Dest
    data class ArtistReleases(val name: String, val shelf: ArtistReleaseShelf, val accent: Int? = null) : Dest
    data class AlbumShelf(val albumId: Long, val title: String, val shelf: AlbumReleaseShelf, val accent: Int? = null) : Dest
    data class LibraryAlbums(val accent: Int? = null) : Dest
    data class LibrarySongs(val accent: Int? = null) : Dest
    data class Genre(val name: String) : Dest
    data class Playlist(val playlistId: Long) : Dest
    data object Settings : Dest
    /** Debug: every design-system piece in one place (M2). */
    data object Gallery : Dest
}

/** One back stack per tab; switching tabs keeps each stack alive. */
@Stable
class TabNavigator(initial: Map<Tab, List<Dest>> = emptyMap(), start: Tab = Tab.Home) {
    var current by mutableStateOf(start)
        private set

    private val stacks: Map<Tab, SnapshotStateList<Dest>> =
        Tab.entries.associateWith { tab -> mutableStateListOf<Dest>().apply { addAll(initial[tab]?.takeIf { it.isNotEmpty() } ?: listOf(Dest.Root(tab))) } }

    val backStack: SnapshotStateList<Dest> get() = stacks.getValue(current)

    fun stackOf(tab: Tab): SnapshotStateList<Dest> = stacks.getValue(tab)

    /** Selecting the current tab again goes back to its root. */
    fun select(tab: Tab) {
        if (tab == current) backStack.retainAll(listOf(backStack.first())) else current = tab
    }

    fun push(dest: Dest) = backStack.add(dest)

    /** @return true if a screen was popped. */
    fun pop(): Boolean = if (backStack.size > 1) { backStack.removeAt(backStack.lastIndex); true } else false

    internal fun save(): List<Any> = listOf(current.name) + Tab.entries.map { tab -> stacks.getValue(tab).map(Dest::encode) }

    companion object {
        val Saver: Saver<TabNavigator, List<Any>> = Saver(
            save = { it.save() },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                TabNavigator(
                    initial = Tab.entries.mapIndexed { i, tab -> tab to (saved[i + 1] as List<List<Any>>).mapNotNull(::decodeDest) }.toMap(),
                    start = Tab.valueOf(saved[0] as String),
                )
            },
        )
    }
}

/** The navigation state survives the activity being recreated (rotation, theme or wallpaper change, process death). */
@Composable
fun rememberTabNavigator(): TabNavigator = rememberSaveable(saver = TabNavigator.Saver) { TabNavigator() }

private fun Dest.encode(): List<Any> = when (this) {
    is Dest.Root -> listOf("root", tab.name)
    is Dest.Album -> listOf("album", albumId, title)
    is Dest.Artist -> listOf("artist", name)
    is Dest.ArtistSongs -> listOf("artistSongs", name) + listOfNotNull(accent)
    is Dest.ArtistInfo -> listOf("artistInfo", name) + listOfNotNull(accent)
    is Dest.ArtistReleases -> listOf("artistReleases", name, shelf.name) + listOfNotNull(accent)
    is Dest.AlbumShelf -> listOf("albumShelf", albumId, title, shelf.name) + listOfNotNull(accent)
    is Dest.LibraryAlbums -> listOf("libraryAlbums") + listOfNotNull(accent)
    is Dest.LibrarySongs -> listOf("librarySongs") + listOfNotNull(accent)
    is Dest.Genre -> listOf("genre", name)
    is Dest.Playlist -> listOf("playlist", playlistId)
    Dest.Settings -> listOf("settings")
    Dest.Gallery -> listOf("gallery")
}

private fun decodeDest(parts: List<Any>): Dest? = runCatching {
    when (parts[0] as String) {
        "root" -> Dest.Root(Tab.valueOf(parts[1] as String))
        "album" -> Dest.Album(parts[1] as Long, parts[2] as String)
        "artist" -> Dest.Artist(parts[1] as String)
        "artistSongs" -> Dest.ArtistSongs(parts[1] as String, parts.getOrNull(2) as? Int)
        "artistInfo" -> Dest.ArtistInfo(parts[1] as String, parts.getOrNull(2) as? Int)
        "artistReleases" -> Dest.ArtistReleases(parts[1] as String, ArtistReleaseShelf.valueOf(parts[2] as String), parts.getOrNull(3) as? Int)
        "albumShelf" -> Dest.AlbumShelf(parts[1] as Long, parts[2] as String, AlbumReleaseShelf.valueOf(parts[3] as String), parts.getOrNull(4) as? Int)
        "libraryAlbums" -> Dest.LibraryAlbums(parts.getOrNull(1) as? Int)
        "librarySongs" -> Dest.LibrarySongs(parts.getOrNull(1) as? Int)
        "genre" -> Dest.Genre(parts[1] as String)
        "playlist" -> Dest.Playlist(parts[1] as Long)
        "settings" -> Dest.Settings
        "gallery" -> Dest.Gallery
        else -> null
    }
}.getOrNull()

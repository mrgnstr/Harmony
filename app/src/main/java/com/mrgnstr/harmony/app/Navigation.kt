package com.mrgnstr.harmony.app

import com.mrgnstr.harmony.core.model.ArtistReleaseShelf
import com.mrgnstr.harmony.core.model.AlbumReleaseShelf
import com.mrgnstr.harmony.core.model.ArtistListKind
import com.mrgnstr.harmony.core.model.HomeShelf
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
    data class ArtistList(val kind: ArtistListKind, val ref: String = "", val accent: Int? = null) : Dest
    data class HomeShelfDest(val shelf: HomeShelf) : Dest
    data class LibraryAlbums(val accent: Int? = null) : Dest
    data class LibrarySongs(val accent: Int? = null) : Dest
    data class Genre(val name: String) : Dest
    data class Playlist(val playlistId: Long) : Dest
    data object Settings : Dest
    /** Debug: every design-system piece in one place (M2). */
    data object Gallery : Dest
    /** Debug: the Apple Music-style flow background prototype, a submenu of the gallery. */
    data object FlowGallery : Dest
}

/** One back stack per tab; switching tabs keeps each stack alive. */
@Stable
class TabNavigator(initial: Map<Tab, List<Dest>> = emptyMap(), start: Tab = Tab.Home, visited: List<Tab> = emptyList()) {
    var current by mutableStateOf(start)
        private set

    private val stacks: Map<Tab, SnapshotStateList<Dest>> =
        Tab.entries.associateWith { tab -> mutableStateListOf<Dest>().apply { addAll(initial[tab]?.takeIf { it.isNotEmpty() } ?: listOf(Dest.Root(tab))) } }

    val backStack: SnapshotStateList<Dest> get() = stacks.getValue(current)

    /** The tabs left behind, most recent last, each once: back at a tab's root returns to the last of them before it quits the app. */
    private val history = mutableStateListOf<Tab>().apply { addAll(visited) }

    /** Whether back at the current tab's root has a tab to go back to. */
    val canLeaveTab: Boolean get() = backStack.size <= 1 && history.isNotEmpty()

    /** Back at a root: returns to the tab before this one. @return false when there is none. */
    fun backToPreviousTab(): Boolean {
        val previous = history.lastOrNull() ?: return false
        history.removeAt(history.lastIndex)
        current = previous
        return true
    }

    fun stackOf(tab: Tab): SnapshotStateList<Dest> = stacks.getValue(tab)

    /** Selecting the current tab again goes back to its root. */
    fun select(tab: Tab) {
        lastWasBack = false
        if (tab == current) backStack.retainAll(listOf(backStack.first())) else {
            history.remove(tab)
            history.remove(current)
            history.add(current)
            current = tab
        }
    }

    /** Whether the last move was a back (a pop or a tab returned to), which pages read to animate as going back. */
    var lastWasBack by mutableStateOf(false)
        private set
    var lastBackByGesture by mutableStateOf(false)
        private set

    fun push(dest: Dest): Boolean { lastWasBack = false; return backStack.add(dest) }

    /** @return true if a screen was popped. */
    /** @param byGesture the back came from a swipe, whose motion is quicker than the arrow's. */
    fun pop(byGesture: Boolean = false): Boolean =
        if (backStack.size > 1) { lastWasBack = true; lastBackByGesture = byGesture; backStack.removeAt(backStack.lastIndex); true } else false

    internal fun save(): List<Any> = listOf(current.name) + Tab.entries.map { tab -> stacks.getValue(tab).map(Dest::encode) } + listOf(history.map { it.name })

    companion object {
        val Saver: Saver<TabNavigator, List<Any>> = Saver(
            save = { it.save() },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                TabNavigator(
                    initial = Tab.entries.mapIndexed { i, tab -> tab to (saved[i + 1] as List<List<Any>>).mapNotNull(::decodeDest) }.toMap(),
                    start = Tab.valueOf(saved[0] as String),
                    visited = runCatching { (saved.getOrNull(Tab.entries.size + 1) as? List<*>).orEmpty().map { Tab.valueOf(it as String) } }.getOrDefault(emptyList()),
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
    is Dest.ArtistList -> listOf("artistList", kind.name, ref) + listOfNotNull(accent)
    is Dest.HomeShelfDest -> listOf("homeShelf", shelf.name)
    is Dest.LibraryAlbums -> listOf("libraryAlbums") + listOfNotNull(accent)
    is Dest.LibrarySongs -> listOf("librarySongs") + listOfNotNull(accent)
    is Dest.Genre -> listOf("genre", name)
    is Dest.Playlist -> listOf("playlist", playlistId)
    Dest.Settings -> listOf("settings")
    Dest.Gallery -> listOf("gallery")
    Dest.FlowGallery -> listOf("flowGallery")
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
        "artistList" -> Dest.ArtistList(ArtistListKind.valueOf(parts[1] as String), parts[2] as String, parts.getOrNull(3) as? Int)
        "homeShelf" -> Dest.HomeShelfDest(HomeShelf.valueOf(parts[1] as String))
        "libraryAlbums" -> Dest.LibraryAlbums(parts.getOrNull(1) as? Int)
        "librarySongs" -> Dest.LibrarySongs(parts.getOrNull(1) as? Int)
        "genre" -> Dest.Genre(parts[1] as String)
        "playlist" -> Dest.Playlist(parts[1] as Long)
        "settings" -> Dest.Settings
        "gallery" -> Dest.Gallery
        "flowGallery" -> Dest.FlowGallery
        else -> null
    }
}.getOrNull()

package com.mrgnstr.harmony.app

import com.mrgnstr.harmony.core.model.AlbumReleaseShelf
import com.mrgnstr.harmony.core.model.ArtistReleaseShelf
import com.mrgnstr.harmony.core.model.HomeShelf
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNavigationTest {
    @Test fun artistChildPagesKeepInheritedColorAcrossRestoration() {
        val accent = 0xff556699.toInt()
        for (destination in listOf(Dest.ArtistInfo("A", accent), Dest.ArtistSongs("A", accent))) {
            val nav = TabNavigator(mapOf(Tab.Library to listOf(Dest.Root(Tab.Library), Dest.Artist("A"), destination)), Tab.Library)
            val restored = TabNavigator.Saver.restore(nav.save())!!
            assertEquals(Tab.Library, restored.current)
            assertEquals(destination, restored.backStack.last())
        }
    }

    @Test fun olderSavedArtistPagesWithoutColorStillRestore() {
        val saved: List<Any> = listOf(
            "Library",
            listOf(listOf("root", "Home")),
            listOf(listOf("root", "Library"), listOf("artistInfo", "A")),
            listOf(listOf("root", "Search")),
        )
        assertEquals(Dest.ArtistInfo("A"), TabNavigator.Saver.restore(saved)!!.backStack.last())
    }
    @Test fun expandedCollectionsRestoreTheirSourceSectionAndColor() {
        val accent = 0xff112233.toInt()
        val destinations = ArtistReleaseShelf.entries.map { Dest.ArtistReleases("A & B", it, accent) } +
            AlbumReleaseShelf.entries.map { Dest.AlbumShelf(42L, "Record (Deluxe)", it, accent) } +
            listOf(Dest.LibraryAlbums(accent), Dest.LibrarySongs(accent))
        for (destination in destinations) {
            val nav = TabNavigator(mapOf(Tab.Library to listOf(Dest.Root(Tab.Library), destination)), Tab.Library)
            assertEquals(destination, TabNavigator.Saver.restore(nav.save())!!.backStack.last())
        }
    }

    @Test fun expandedCollectionsAlsoRestoreWithoutArtworkColors() {
        val destinations = listOf(
            Dest.ArtistReleases("A", ArtistReleaseShelf.FeaturedOn),
            Dest.AlbumShelf(7L, "Record", AlbumReleaseShelf.OtherVersions),
            Dest.HomeShelfDest(HomeShelf.RecentlyPlayed),
            Dest.HomeShelfDest(HomeShelf.FavouriteAlbums),
            Dest.LibraryAlbums(), Dest.LibrarySongs(),
        )
        for (destination in destinations) {
            val nav = TabNavigator(mapOf(Tab.Home to listOf(Dest.Root(Tab.Home), destination)))
            assertEquals(destination, TabNavigator.Saver.restore(nav.save())!!.backStack.last())
        }
    }
}

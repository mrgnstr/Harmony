package com.mrgnstr.harmony

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.data.FavouritesRepository
import com.mrgnstr.harmony.core.data.PlaylistCoverStore
import com.mrgnstr.harmony.core.data.PlaylistRepository
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import com.mrgnstr.harmony.core.database.SongEntity
import com.mrgnstr.harmony.core.model.FAVOURITES_PLAYLIST_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavouritesTest {
    private lateinit var db: HarmonyDatabase
    private lateinit var favourites: FavouritesRepository
    private lateinit var playlists: PlaylistRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, HarmonyDatabase::class.java).build()
        favourites = FavouritesRepository(db.favouritesDao(), scope)
        playlists = PlaylistRepository(db, db.playlistDao(), PlaylistCoverStore(ctx), favourites)
    }

    @After fun tearDown() = db.close()

    private fun song(id: Long, albumId: Long) = SongEntity(id, "t$id", "a", "a", "alb$albumId", albumId, 60_000, 1, 1, 1, 1, null)

    private fun await(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(20) }
        throw AssertionError("timed out waiting for $what")
    }

    @Test fun songsAndAlbumsAreFavouritedSeparately() = runBlocking {
        db.songDao().upsert(listOf(song(1, 10), song(2, 10), song(3, 11)))
        favourites.toggleAlbum(10, "alb10")
        await("album") { favourites.isFavouriteAlbum(10, "alb10") }
        // The album being a favourite says nothing about its songs, and a same-id edition with another title is another album.
        assertTrue(favourites.songIds.value.isEmpty())
        assertFalse(favourites.isFavouriteAlbum(10, "alb10 (Deluxe)"))
        assertTrue(favourites.entries.first().isEmpty())

        favourites.toggleSong(2)
        await("song") { favourites.isFavourite(2) }
        assertFalse(favourites.isFavourite(1))
        favourites.toggleSong(2)
        await("song removed") { !favourites.isFavourite(2) }
        favourites.toggleAlbum(10, "alb10")
        await("album removed") { !favourites.isFavouriteAlbum(10, "alb10") }
    }

    @Test fun artistsAreIndependentAndListedNewestFirst() = runBlocking {
        favourites.toggleSong(1)
        favourites.toggleAlbum(10, "alb10")
        db.favouritesDao().toggleArtist("First", 100)
        db.favouritesDao().toggleArtist("Second", 200)
        await("artists") { favourites.artists.value.size == 2 }
        assertEquals(listOf("Second", "First"), db.favouritesDao().observeArtistNames().first())
        assertTrue(favourites.isFavouriteArtist("First"))

        favourites.toggleArtist("First")
        await("artist removed") { !favourites.isFavouriteArtist("First") }
        assertEquals(setOf("Second"), favourites.artists.value)
        await("other favourites") { favourites.isFavourite(1) && favourites.isFavouriteAlbum(10, "alb10") }
        assertEquals(setOf(1L), favourites.songIds.value)
        assertEquals(1, favourites.albums.value.size)
    }

    @Test fun favouritesPlaylistListsNewestFirstAndDropsMissingSongs() = runBlocking {
        db.songDao().upsert(listOf(song(1, 10), song(2, 10), song(3, 11)))
        favourites.toggleSong(1); Thread.sleep(5)
        favourites.toggleSong(99); Thread.sleep(5) // not in the library
        favourites.toggleSong(3); Thread.sleep(5)
        favourites.toggleSong(2)
        await("four toggles") { favourites.songIds.value.size == 4 }
        assertEquals(listOf(2L, 3L, 1L), favourites.entries.first().map { it.song.id })

        val all = playlists.playlistsWithFavourites.first()
        assertEquals(FAVOURITES_PLAYLIST_ID, all.first().id)
        assertTrue(all.first().isFavourites)
        assertEquals(3, all.first().songCount)
        // It is not a stored playlist: the pickers (which read `playlists`) never see it.
        assertEquals(0, playlists.playlists.first().size)
        assertEquals(listOf(2L, 3L, 1L), playlists.entries(FAVOURITES_PLAYLIST_ID).first().map { it.song.id })
        assertEquals("Favourites", playlists.playlist(FAVOURITES_PLAYLIST_ID).first()?.name)
        assertNull(playlists.playlist(12345).first())
    }
}

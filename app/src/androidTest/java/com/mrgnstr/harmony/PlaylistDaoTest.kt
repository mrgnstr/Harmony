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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    private lateinit var db: HarmonyDatabase
    private lateinit var repo: PlaylistRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, HarmonyDatabase::class.java).build()
        repo = PlaylistRepository(
            db, db.playlistDao(), PlaylistCoverStore(ctx),
            FavouritesRepository(db.favouritesDao(), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())),
        )
    }

    @After fun tearDown() = db.close()

    private fun song(id: Long, albumId: Long) = SongEntity(id, "t$id", "a", "a", "alb$albumId", albumId, 60_000, 1, 1, 1, 1, null)

    @Test fun createAddRemoveKeepsOrderAndDuplicates() = runBlocking {
        db.songDao().upsert(listOf(song(1, 10), song(2, 10), song(3, 11)))
        val id = repo.create("Mix", songIds = listOf(3, 1))
        repo.addSongs(id, listOf(2, 1))
        val entries = repo.entries(id).first()
        assertEquals(listOf(3L, 1L, 2L, 1L), entries.map { it.song.id })
        repo.removeEntry(id, entries[1].entryId)
        assertEquals(listOf(3L, 2L, 1L), repo.entries(id).first().map { it.song.id })
    }

    @Test fun coverArtIsFirstFourUniqueAlbums() = runBlocking {
        db.songDao().upsert((1L..8L).map { song(it, albumId = (it + 1) / 2) }) // albums 1..4, two songs each
        val id = repo.create("All", songIds = (1L..8L).toList())
        val p = repo.playlist(id).first()!!
        assertEquals(listOf(1L, 2L, 3L, 4L), p.art.map { it.albumId })
        assertEquals(8, p.songCount)
        assertNull(p.coverPath)
    }

    @Test fun deletingPlaylistDeletesItsSongRows() = runBlocking {
        db.songDao().upsert(listOf(song(1, 10)))
        val id = repo.create("Gone", songIds = listOf(1))
        repo.delete(id)
        assertEquals(0, repo.playlists.first().size)
        assertEquals(0, db.playlistDao().observeMembers().first().size)
    }
}

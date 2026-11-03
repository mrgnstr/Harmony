package com.mrgnstr.harmony.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleasesTest {
    private fun album(id: Long, title: String, songs: Int = 10, year: Int = 2020, artist: String = "A") =
        Album(id, title, artist, artSongId = id, songCount = songs, addedSec = 0, durationMs = 0, year = year)

    private fun song(id: Long, title: String, album: Album, track: Int = 1, artist: String = album.artist) =
        Song(id, title, artist, album.artist, album.title, album.id, 0, track, 1, 0, 0)

    @Test
    fun splitsSinglesAndHidesEditions() {
        val lp = album(1, "Record")
        val deluxe = album(2, "Record (Deluxe)")
        val single = album(3, "Song - Single", songs = 1)
        val ep = album(4, "Five Songs EP", songs = 5)
        val d = discographyOf(listOf(lp, deluxe, single, ep), emptyList(), listOf(lp, deluxe, single, ep))
        assertEquals(listOf(lp), d.albums)
        assertEquals(setOf(single, ep), d.singles.toSet())
    }

    @Test
    fun featuredOnListsOtherArtistsAlbumsOnly() {
        val own = album(1, "Mine")
        val other = album(2, "Theirs", artist = "B")
        val guest = song(10, "Duet", other, artist = "B feat. A")
        val d = discographyOf(listOf(own), listOf(guest), listOf(own, other))
        assertEquals(listOf(other), d.featuredOn)
    }

    @Test
    fun topSongsPutsPlayedFirstThenOpeners() {
        val a = album(1, "First", year = 2021)
        val b = album(2, "Second", year = 2019)
        val songs = listOf(song(1, "a1", a, 1), song(2, "a2", a, 2), song(3, "b1", b, 1), song(4, "b2", b, 2))
        val top = topSongs(songs, listOf(a, b), counts = mapOf(4L to 3, 2L to 5), limit = 3)
        assertEquals(listOf(2L, 4L, 1L), top.map { it.id })
    }
}

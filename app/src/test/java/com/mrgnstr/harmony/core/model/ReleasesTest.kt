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

    @Test
    fun otherVersionsAreEditionsByTheSameArtist() {
        val base = album(1, "Record")
        val deluxe = album(2, "Record (Deluxe Edition)", year = 2021)
        val dashed = album(3, "Record - Deluxe Edition", year = 2022)
        val atmos = album(4, "Record [Atmos]", year = 2023)
        val spelled = album(5, "Record: The Deluxe", year = 2024)
        val otherName = album(6, "Another Record")
        val otherArtist = album(7, "Record (Deluxe)", artist = "B")
        val all = listOf(base, deluxe, dashed, atmos, spelled, otherName, otherArtist)
        val versions = otherVersionsOf(base, all) { it.artist == "A" }
        assertEquals(listOf(deluxe, dashed, atmos, spelled).toSet(), versions.toSet())
        // From a deluxe edition, the plain album is a version too, and the album itself never is.
        val fromDeluxe = otherVersionsOf(deluxe, all) { it.artist == "A" }
        assertEquals(true, base in fromDeluxe)
        assertEquals(false, deluxe in fromDeluxe)
    }
    @Test
    fun heroPrefersNewestFullAlbumAndOriginalEdition() {
        val original = album(1, "Record", year = 2023)
        val deluxe = album(2, "Record (Deluxe)", year = 2025)
        val single = album(3, "New - Single", songs = 1, year = 2026)
        assertEquals(original, heroOf(listOf(single, deluxe, original)))
        assertEquals(single, heroOf(listOf(single)))
        assertEquals(null, heroOf(emptyList()))
    }

    @Test
    fun expandedTopSongsKeepsPreviewOrderAndHidesDuplicateEditions() {
        val a = album(1, "Record")
        val deluxe = album(2, "Record (Deluxe)")
        val songs = (1L..7L).map { song(it, "Song $it", a, it.toInt()) } + song(8, "Song 1", deluxe)
        val counts = mapOf(7L to 10, 6L to 4)
        val all = topSongs(songs, listOf(a, deluxe), counts, songs.size)
        assertEquals(7, all.size)
        assertEquals(listOf(7L, 6L), all.take(2).map { it.id })
        assertEquals(topSongs(songs, listOf(a, deluxe), counts), all.take(5))
    }

    @Test
    fun featuredCreditsMatchArtistFieldsAndExplicitTitleCredits() {
        val other = album(1, "Guest record", artist = "B")
        val index = ArtistIndex(listOf("A", "B", "Ann", "Anne", "AC/DC"))
        assertEquals(true, song(1, "Duet", other, artist = "B & A").featuresArtist("A", index))
        assertEquals(true, song(2, "Duet (feat. A)", other).featuresArtist("A", index))
        assertEquals(true, song(3, "Duet [ft. A & B]", other).featuresArtist("A", index))
        assertEquals(true, song(4, "Duet (feat. AC/DC)", other).featuresArtist("AC/DC", index))
        assertEquals(false, song(5, "Duet (feat. Anne)", other).featuresArtist("Ann", index))
        assertEquals(false, song(6, "A love song", other).featuresArtist("A", index))
    }
}

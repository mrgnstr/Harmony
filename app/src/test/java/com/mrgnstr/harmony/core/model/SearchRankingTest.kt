package com.mrgnstr.harmony.core.model

import org.junit.Assert.*
import org.junit.Test

class SearchRankingTest {
    @Test fun artistNameOutranksTheirSongsAndAlbums() {
        assertEquals(SearchCategory.Artists, SearchRanking.categories("mili", listOf("Compass"), listOf("Miracle Milk"), listOf("Mili")).first())
    }

    @Test fun albumTitleOutranksSongsMatchedThroughAlbumMetadata() {
        assertEquals(SearchCategory.Albums, SearchRanking.categories("miracle milk", listOf("Compass"), listOf("Miracle Milk"), emptyList()).first())
    }

    @Test fun exactSongOutranksPartialArtistAndAlbumNames() {
        assertEquals(SearchCategory.Songs, SearchRanking.categories("Compass", listOf("Compass"), listOf("Compass Collection"), listOf("Compass Band")).first())
    }

    @Test fun prefixesAccentsAndTiesAreStable() {
        assertEquals(SearchCategory.Artists, SearchRanking.categories("bey", listOf("Song"), listOf("Album"), listOf("Beyoncé")).first())
        assertEquals(100, SearchRanking.score("Déjà Vu", "deja vu"))
        assertTrue(SearchRanking.score("Milk Miracle", "miracle milk") > SearchRanking.score("Collection", "miracle milk"))
        assertEquals(listOf(SearchCategory.Artists, SearchCategory.Albums, SearchCategory.Songs),
            SearchRanking.categories("Same", listOf("Same"), listOf("Same"), listOf("Same")))
        assertEquals(listOf(SearchCategory.Artists, SearchCategory.Albums, SearchCategory.Songs, SearchCategory.Playlists),
            SearchRanking.categories("Same", listOf("Same"), listOf("Same"), listOf("Same"), listOf("Same")))
        assertEquals(SearchCategory.Playlists,
            SearchRanking.categories("Road Trip", listOf("Other"), emptyList(), emptyList(), listOf("Road Trip")).first())
        assertTrue(SearchRanking.categories("", emptyList(), emptyList(), emptyList()).isEmpty())
    }
}

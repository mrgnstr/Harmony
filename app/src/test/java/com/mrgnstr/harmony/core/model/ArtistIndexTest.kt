package com.mrgnstr.harmony.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistIndexTest {
    private val index = ArtistIndex(
        listOf("Billie Eilish", "Billie Eilish & ROSALÍA", "Simon & Garfunkel", "BANSHIMOKU", "BANSHIMOKU, 大里侑平, BANSHIMOKU", "Tyler, The Creator", "AC/DC", "Ado"),
    )

    @Test fun splitsWhenAPartStandsAlone() = assertEquals(listOf("Billie Eilish", "ROSALÍA"), index.artistsOf("Billie Eilish & ROSALÍA"))

    @Test fun keepsBandNames() {
        assertEquals(listOf("Simon & Garfunkel"), index.artistsOf("Simon & Garfunkel"))
        assertEquals(listOf("Tyler, The Creator"), index.artistsOf("Tyler, The Creator"))
        assertEquals(listOf("AC/DC"), index.artistsOf("AC/DC"))
    }

    @Test fun collapsesRepeats() = assertEquals(listOf("BANSHIMOKU", "大里侑平"), index.artistsOf("BANSHIMOKU, 大里侑平, BANSHIMOKU"))

    @Test fun otherSeparators() {
        assertEquals(listOf("Ado", "Someone"), index.artistsOf("Ado feat. Someone"))
        assertEquals(listOf("Ado", "Someone"), index.artistsOf("Ado x Someone"))
        assertEquals(listOf("Lil Nas X"), index.artistsOf("Lil Nas X"))
    }
}

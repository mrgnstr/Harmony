package com.mrgnstr.harmony.core.model

import org.junit.Assert.*
import org.junit.Test

class SearchQueryTest {
    @Test fun prefixesAndAccentsMatchAcrossWords() {
        assertTrue(SearchQuery.matches("Beyoncé — Déjà Vu", "bey deja"))
        assertTrue(SearchQuery.matches("東京 音楽", "東京"))
        assertFalse(SearchQuery.matches("Compass", "pass"))
        assertFalse(SearchQuery.matches("Artist Album", "artist missing"))
    }

    @Test fun punctuationCannotBecomeFtsSyntax() {
        assertNull(SearchQuery.match(" -*()\" : % "))
        assertEquals(listOf("rock", "n", "roll"), SearchQuery.tokens("Rock 'n' Roll"))
        assertEquals("\"foo*\" \"or*\" \"bar*\"", SearchQuery.match("foo OR bar"))
        assertEquals("beyoncé live", SearchQuery.recentKey("  Beyoncé   LIVE  "))
    }
}

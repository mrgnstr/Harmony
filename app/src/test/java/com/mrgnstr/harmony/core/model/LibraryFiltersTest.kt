package com.mrgnstr.harmony.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFiltersTest {
    @Test fun exclusionsIncludeSubfoldersButNotSimilarlyNamedFolders() {
        val filters = LibraryFilters(excludedFolders = setOf("/storage/Music/Hidden"))
        assertTrue(filters.excludes("/storage/Music/Hidden/song.mp3"))
        assertTrue(filters.excludes("/storage/Music/Hidden/Album/song.mp3"))
        assertFalse(filters.excludes("/storage/Music/Hidden Extras/song.mp3"))
        assertFalse(filters.excludes("/storage/Music/Visible/Hidden/song.mp3"))
    }

    @Test fun pathsAreCaseSensitiveAndMissingPathsRemainVisible() {
        val filters = LibraryFilters(excludedFolders = setOf("/storage/Music/"))
        assertTrue(filters.excludes("/storage/Music/song.mp3"))
        assertFalse(filters.excludes("/storage/music/song.mp3"))
        assertFalse(filters.excludes(null))
        assertFalse(LibraryFilters().excludes("/storage/Music/song.mp3"))
    }
}

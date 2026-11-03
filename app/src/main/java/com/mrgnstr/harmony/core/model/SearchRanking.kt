package com.mrgnstr.harmony.core.model

enum class SearchCategory { Songs, Albums, Artists }

/** Direct names/titles outrank matches inherited through an artist or album credit. */
object SearchRanking {
    fun score(name: String, query: String): Int {
        val wanted = SearchQuery.tokens(query)
        if (wanted.isEmpty()) return 0
        val actual = SearchQuery.tokens(name)
        return when {
            actual == wanted -> 100
            actual.joinToString(" ").startsWith(wanted.joinToString(" ")) -> 80
            wanted.all { prefix -> actual.any { it.startsWith(prefix) } } -> 60
            else -> 0
        }
    }

    fun categories(query: String, songTitles: List<String>, albumTitles: List<String>, artistNames: List<String>): List<SearchCategory> {
        val names = mapOf(SearchCategory.Songs to songTitles, SearchCategory.Albums to albumTitles, SearchCategory.Artists to artistNames)
        // Prefer the entity itself on equal-strength matches: artist, then album, then song.
        return listOf(SearchCategory.Artists, SearchCategory.Albums, SearchCategory.Songs)
            .filter { names.getValue(it).isNotEmpty() }
            .sortedByDescending { category -> names.getValue(category).maxOf { score(it, query) } }
    }
}

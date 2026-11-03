package com.mrgnstr.harmony.core.model

import java.util.concurrent.ConcurrentHashMap

// "A & B", "A, B", "A x B", "A feat. B", "A with B", "A vs. B", "A; B", "A/B".
private val Separators = Regex("""\s*(?:[,;/&]|\s(?:x|vs\.?|feat\.?|ft\.?|featuring|with)\s)\s*""", RegexOption.IGNORE_CASE)

/** Who a song belongs to: its album artist, or its artist when the album artist is empty. */
val Song.owner: String get() = albumArtist.ifEmpty { artist }

/**
 * Splits collaboration credits into artists. Band names contain the same separators ("Simon & Garfunkel",
 * "Earth, Wind & Fire"), so a credit is only split when at least one of its parts is credited on its own somewhere in the
 * library ([credits] = every album artist and artist string). Repeated parts collapse into one.
 */
class ArtistIndex(credits: Collection<String>) {
    private val standalone = credits.mapTo(HashSet()) { it.trim().lowercase() }
    private val cache = ConcurrentHashMap<String, List<String>>()

    fun artistsOf(credit: String): List<String> = cache.getOrPut(credit) {
        val parts = credit.split(Separators).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        if (parts.size > 1 && parts.any { it.lowercase() in standalone }) parts else listOf(credit.trim())
    }

    fun credits(credit: String, artist: String): Boolean = artistsOf(credit).any { it.equals(artist, ignoreCase = true) }

    companion object {
        fun of(songs: List<Song>) = ArtistIndex(songs.flatMapTo(HashSet()) { listOf(it.albumArtist, it.artist) }.filter { it.isNotBlank() })
    }
}

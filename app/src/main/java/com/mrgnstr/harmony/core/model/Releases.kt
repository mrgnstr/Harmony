package com.mrgnstr.harmony.core.model

/** Release grouping for artist pages (moved from the old artist view model so every page agrees on it). */

val NewestFirst: Comparator<Album> = compareByDescending<Album> { it.year }.thenByDescending { it.addedSec }

private val SingleOrEp = Regex("""(?i)(\bsingle\b|\bep\b)""")

/** Short releases and anything titled as a single or EP go to "Singles & EPs". */
fun Album.isSingleOrEp(): Boolean = songCount <= 3 || SingleOrEp.containsMatchIn(title)

private val Bracketed = Regex("""\([^)]*\)|\[[^\]]*\]""")

/** Album title without any (parenthesised) or [bracketed] parts, so editions like "(Deluxe)" or "[Atmos]" match. */
fun String.baseTitle(): String =
    replace(Bracketed, " ").trim().replace(Regex("\\s+"), " ").lowercase().ifEmpty { trim().lowercase() }

private val EditionSuffix =
    Regex("""(?i)\s+[-–—]\s+[^-–—]*\b(deluxe|edition|expanded|remaster(ed)?|anniversary|atmos|spatial|version|bonus)\b[^-–—]*$""")

private val ReleaseTypeSuffix = Regex("""(?i)\s+[-–—]\s+(single|ep)\s*$""")

/** The title with edition markers removed: "(Deluxe)", "[Dolby Atmos]", " - Deluxe Edition", " - Single" and the like. */
fun String.editionBase(): String = replace(ReleaseTypeSuffix, "").replace(EditionSuffix, "").baseTitle()

/**
 * One release per album: deluxe, remastered, Atmos and other editions are hidden behind the main one. The main one is the
 * edition with the plainest title, and the list keeps its order.
 */
fun List<Album>.mainEditions(): List<Album> {
    val main = groupBy { it.title.editionBase() }.mapValues { (_, editions) ->
        editions.minWith(compareBy<Album> { it.title.trim().lowercase() != it.title.editionBase() }.thenBy { it.title.length })
    }
    return filter { main[it.title.editionBase()] === it }
}

private val Deluxe = Regex("""(?i)\bdeluxe\b""")

/**
 * The other editions of [current] among [albums] (an album page's "Other Versions"): releases by the same artist ([sameArtist]) whose
 * title is the same once parentheses are ignored ("Album (Deluxe)", "Album [Atmos]"), the same once edition markers are removed
 * ("Album - Deluxe Edition"), or that holds this album's name and says "deluxe" (or the other way round). Oldest first.
 */
fun otherVersionsOf(current: Album, albums: List<Album>, sameArtist: (Album) -> Boolean): List<Album> {
    val base = current.title.baseTitle()
    val edition = current.title.editionBase()
    return albums.filter { other ->
        if (other.id == current.id && other.title == current.title) return@filter false
        if (!sameArtist(other)) return@filter false
        val otherBase = other.title.baseTitle()
        otherBase == base || other.title.editionBase() == edition ||
            ((Deluxe.containsMatchIn(other.title) || Deluxe.containsMatchIn(current.title)) &&
                (other.title.lowercase().contains(base) || current.title.lowercase().contains(otherBase)))
    }.sortedWith(compareBy<Album> { if (it.year > 0) it.year else Int.MAX_VALUE }.thenBy { it.title.length })
}

/** The release an artist is pictured with: the newest full album (main edition), else the newest single or EP. */
fun heroOf(releases: List<Album>): Album? {
    val sorted = releases.sortedWith(NewestFirst).mainEditions()
    return sorted.firstOrNull { !it.isSingleOrEp() } ?: sorted.firstOrNull()
}

/** An artist's releases split for the artist page. */
data class Discography(val albums: List<Album>, val singles: List<Album>, val featuredOn: List<Album>)

/**
 * [own] = albums the artist owns (album artist), [guestSongs] = songs crediting the artist on someone else's album.
 * Featured On lists the albums containing those songs, newest first, never one of the artist's own.
 */
fun discographyOf(own: List<Album>, guestSongs: List<Song>, all: List<Album>): Discography {
    val sorted = own.sortedWith(NewestFirst)
    val (singles, full) = sorted.partition { it.isSingleOrEp() }
    val ownKeys = own.mapTo(HashSet()) { it.id to it.title }
    val guestKeys = guestSongs.mapTo(HashSet()) { it.albumId to it.album }
    val featured = all.filter { (it.id to it.title) in guestKeys && (it.id to it.title) !in ownKeys }.sortedWith(NewestFirst).mainEditions()
    return Discography(full.mainEditions(), singles.mainEditions(), featured)
}

/**
 * The artist's [limit] most played songs ([counts]: song id → plays). Songs never played fill the rest: the opening tracks of
 * the newest releases first (track 1 of each, then track 2, ...). One song per title, so the same track on two editions shows once.
 */
fun topSongs(songs: List<Song>, releases: List<Album>, counts: Map<Long, Int>, limit: Int = 5): List<Song> {
    val out = ArrayList<Song>(limit)
    val seen = HashSet<String>()
    songs.filter { (counts[it.id] ?: 0) > 0 }.sortedByDescending { counts[it.id] }.forEach {
        if (out.size < limit && seen.add(it.title.baseTitle())) out += it
    }
    val byRelease = songs.groupBy { it.albumId to it.album }
    val lists = releases.sortedWith(NewestFirst).let { r -> r.mainEditions().let { main -> main + (r - main.toSet()) } }
        .mapNotNull { byRelease[it.id to it.title] }
    var round = 0
    while (out.size < limit && lists.any { round < it.size }) {
        for (list in lists) {
            if (round < list.size && out.size < limit && seen.add(list[round].title.baseTitle())) out += list[round]
        }
        round++
    }
    return out
}

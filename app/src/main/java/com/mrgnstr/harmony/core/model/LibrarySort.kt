package com.mrgnstr.harmony.core.model

/** What a library tab can be ordered by. [descendingFirst]: the direction a freshly chosen key starts in (newest, most, longest first). */
enum class SortKey(val label: String, val descendingFirst: Boolean = false) {
    Title("Title"),
    Artist("Artist"),
    AlbumArtist("Album Artist"),
    Album("Album"),
    Year("Year", descendingFirst = true),
    DateAdded("Date Added", descendingFirst = true),
    Updated("Recently Updated", descendingFirst = true),
    RecentlyPlayed("Recently Played", descendingFirst = true),
    PlayCount("Play Count", descendingFirst = true),
    Rating("Rating", descendingFirst = true),
    Duration("Duration", descendingFirst = true),
    TrackCount("Track Count", descendingFirst = true),
}

data class LibrarySort(val key: SortKey, val ascending: Boolean = !key.descendingFirst) {
    /** Choosing [next] in the menu: the same key flips direction, another key starts in its natural direction. */
    fun choose(next: SortKey) = if (next == key) copy(ascending = !ascending) else LibrarySort(next)

    fun encode() = "${key.name}:${if (ascending) "asc" else "desc"}"

    companion object {
        fun decode(saved: String?): LibrarySort? {
            val (name, dir) = saved?.split(':')?.takeIf { it.size == 2 } ?: return null
            val key = SortKey.entries.firstOrNull { it.name == name } ?: return null
            return LibrarySort(key, dir == "asc")
        }
    }
}

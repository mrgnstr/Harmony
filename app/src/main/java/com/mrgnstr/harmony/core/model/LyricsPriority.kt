package com.mrgnstr.harmony.core.model

/** Which lyrics a song uses when it has both: the first source in the order that has any wins. */
enum class LyricsPriority(val label: String, val description: String) {
    FilesFirst("Lyrics files first", "A .ttml or .lrc file beside the song, then the lyrics in its tags"),
    EmbeddedFirst("Tags first", "The lyrics in the song's tags, then a .ttml or .lrc file beside it"),
}

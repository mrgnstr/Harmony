package com.mrgnstr.harmony.core.model

/**
 * Navidrome Smart Playlist (.nsp) criteria model, matching `model/criteria` in Navidrome:
 * `{ "all": [ { "is": { "loved": true } }, { "gt": { "year": 2015 } } ], "sort": "year", "order": "desc", "limit": 100 }`.
 */
enum class SmartConjunction(val wire: String, val label: String) {
    All("all", "Match all rules"),
    Any("any", "Match any rule"),
}

enum class SmartFieldType { Text, Number, Boolean, Date,PlaylistRef }

enum class SmartField(val wire: String, val label: String, val type: SmartFieldType) {
    Title("title", "Title", SmartFieldType.Text),
    Album("album", "Album", SmartFieldType.Text),
    Artist("artist", "Artist", SmartFieldType.Text),
    AlbumArtist("albumartist", "Album Artist", SmartFieldType.Text),
    Genre("genre", "Genre", SmartFieldType.Text),
    Year("year", "Year", SmartFieldType.Number),
    OriginalYear("originalyear", "Original Year", SmartFieldType.Number),
    DateAdded("dateadded", "Date Added", SmartFieldType.Date),
    LastPlayed("lastplayed", "Last Played", SmartFieldType.Date),
    PlayCount("playcount", "Play Count", SmartFieldType.Number),
    Loved("loved", "Favourite (Song)", SmartFieldType.Boolean),
    AlbumLoved("albumloved", "Favourite (Album)", SmartFieldType.Boolean),
    ArtistLoved("artistloved", "Favourite (Artist)", SmartFieldType.Boolean),
    Rating("rating", "Rating (0–5)", SmartFieldType.Number),
    AlbumRating("albumrating", "Album Rating (0–5)", SmartFieldType.Number),
    Duration("duration", "Duration (sec)", SmartFieldType.Number),
    Bitrate("bitrate", "Bitrate (kbps)", SmartFieldType.Number),
    BitDepth("bitdepth", "Bit Depth", SmartFieldType.Number),
    SampleRate("samplerate", "Sample Rate (Hz)", SmartFieldType.Number),
    Channels("channels", "Channels", SmartFieldType.Number),
    Codec("codec", "Codec", SmartFieldType.Text),
    FileType("filetype", "File Extension", SmartFieldType.Text),
    Bpm("bpm", "BPM", SmartFieldType.Number),
    Mood("mood", "Mood", SmartFieldType.Text),
    Grouping("grouping", "Grouping", SmartFieldType.Text),
    RecordLabel("recordlabel", "Record Label", SmartFieldType.Text),
    ReleaseType("releasetype", "Release Type", SmartFieldType.Text),
    Compilation("compilation", "Compilation", SmartFieldType.Boolean),
    ExplicitStatus("explicitstatus", "Explicit Status", SmartFieldType.Text),
    Composer("composer", "Composer", SmartFieldType.Text),
    Producer("producer", "Producer", SmartFieldType.Text),
    Conductor("conductor", "Conductor", SmartFieldType.Text),
    Lyricist("lyricist", "Lyricist", SmartFieldType.Text),
    Performer("performer", "Performer", SmartFieldType.Text),
    Lyrics("lyrics", "Lyrics", SmartFieldType.Text),
    Comment("comment", "Comment", SmartFieldType.Text),
    HasCoverArt("hascoverart", "Has Cover Art", SmartFieldType.Boolean),
    LibraryId("library_id", "Library ID", SmartFieldType.Number),
    PlaylistId("id", "Playlist", SmartFieldType.PlaylistRef),
    ;

    companion object {
        fun fromWire(wire: String): SmartField =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: Title
    }
}

enum class SmartOperator(val wire: String, val label: String, val supportedTypes: Set<SmartFieldType>) {
    Contains("contains", "contains", setOf(SmartFieldType.Text)),
    NotContains("notContains", "does not contain", setOf(SmartFieldType.Text)),
    Is("is", "is", setOf(SmartFieldType.Text, SmartFieldType.Number, SmartFieldType.Boolean)),
    IsNot("isNot", "is not", setOf(SmartFieldType.Text, SmartFieldType.Number, SmartFieldType.Boolean)),
    StartsWith("startsWith", "starts with", setOf(SmartFieldType.Text)),
    EndsWith("endsWith", "ends with", setOf(SmartFieldType.Text)),
    Gt("gt", "is greater than", setOf(SmartFieldType.Number)),
    Lt("lt", "is less than", setOf(SmartFieldType.Number)),
    InTheRange("inTheRange", "is in range", setOf(SmartFieldType.Number, SmartFieldType.Date)),
    InTheLast("inTheLast", "in the last (days)", setOf(SmartFieldType.Date)),
    NotInTheLast("notInTheLast", "not in the last (days)", setOf(SmartFieldType.Date)),
    Before("before", "before (YYYY-MM-DD)", setOf(SmartFieldType.Date)),
    After("after", "after (YYYY-MM-DD)", setOf(SmartFieldType.Date)),
    IsPresent("isPresent", "is present", setOf(SmartFieldType.Text, SmartFieldType.Number)),
    IsMissing("isMissing", "is missing", setOf(SmartFieldType.Text, SmartFieldType.Number)),
    InPlaylist("inPlaylist", "is in playlist", setOf(SmartFieldType.PlaylistRef)),
    NotInPlaylist("notInPlaylist", "is not in playlist", setOf(SmartFieldType.PlaylistRef)),
    ;

    companion object {
        fun fromWire(wire: String): SmartOperator =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: Contains

        fun forField(field: SmartField): List<SmartOperator> =
            entries.filter { field.type in it.supportedTypes }
    }
}

/** One leaf condition inside a SmartPlaylistCriteria. */
data class SmartRuleCondition(
    val field: SmartField = SmartField.Loved,
    val operator: SmartOperator = SmartOperator.Is,
    val value: String = "true",
    val secondValue: String = "",
)

/** Complete Navidrome Smart Playlist specification. */
data class SmartPlaylistCriteria(
    val conjunction: SmartConjunction = SmartConjunction.All,
    val rules: List<SmartRuleCondition> = listOf(SmartRuleCondition()),
    val sort: String = "dateadded",
    val ascending: Boolean = false,
    val limit: Int = 100,
) {
    /** Serializes into Navidrome's `.nsp` JSON rules object. */
    fun toJsonString(): String {
        val ruleStrings = rules.map { rule ->
            val fieldName = if (rule.operator == SmartOperator.InPlaylist || rule.operator == SmartOperator.NotInPlaylist) {
                "id"
            } else {
                rule.field.wire
            }
            val valJson = when {
                rule.operator == SmartOperator.IsPresent || rule.operator == SmartOperator.IsMissing -> "\"$fieldName\""
                rule.operator == SmartOperator.InTheRange -> {
                    val v1 = formatScalar(rule.field, rule.value)
                    val v2 = formatScalar(rule.field, rule.secondValue.ifBlank { rule.value })
                    "{\"$fieldName\":[$v1,$v2]}"
                }
                else -> "{\"$fieldName\":${formatScalar(rule.field, rule.value)}}"
            }
            "{\"${rule.operator.wire}\":$valJson}"
        }
        val order = if (ascending) "asc" else "desc"
        return buildString {
            append('{')
            append('"').append(conjunction.wire).append("\":[").append(ruleStrings.joinToString(",")).append(']')
            if (sort.isNotBlank()) {
                append(",\"sort\":\"").append(escapeJson(sort)).append('"')
                append(",\"order\":\"").append(order).append('"')
            }
            if (limit > 0) {
                append(",\"limit\":").append(limit)
            }
            append('}')
        }
    }

    private fun formatScalar(field: SmartField, raw: String): String {
        val trimmed = raw.trim()
        return when (field.type) {
            SmartFieldType.Boolean -> if (trimmed.equals("true", ignoreCase = true) || trimmed == "1") "true" else "false"
            SmartFieldType.Number -> trimmed.toLongOrNull()?.toString() ?: trimmed.toDoubleOrNull()?.toString() ?: "0"
            SmartFieldType.Date -> {
                trimmed.toLongOrNull()?.toString() ?: "\"${escapeJson(trimmed)}\""
            }
            else -> "\"${escapeJson(trimmed)}\""
        }
    }

    companion object {
        val SortChoices: List<Pair<String, String>> = listOf(
            "random" to "Random",
            "title" to "Title",
            "album" to "Album",
            "artist" to "Artist",
            "year" to "Year",
            "dateadded" to "Date Added",
            "lastplayed" to "Last Played",
            "playcount" to "Play Count",
            "rating" to "Rating",
            "duration" to "Duration",
            "bpm" to "BPM",
            "bitrate" to "Bitrate",
        )

        private fun escapeJson(s: String): String = buildString(s.length) {
            for (c in s) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
    }
}

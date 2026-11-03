package com.mrgnstr.harmony.core.mediastore

import android.os.Build
import android.provider.MediaStore.Audio.AudioColumns as C

/**
 * Per-API differences in what MediaStore exposes. Pure functions over the SDK int so they are unit-testable.
 *  - ALBUM_ARTIST / DISC_NUMBER / GENRE columns exist from API 30; before that disc is packed into TRACK (disc*1000+track)
 *    and genres come from the Audio.Genres join (see MediaStoreScanner.legacyGenres).
 */
internal object ColumnMapping {
    val BASE = arrayOf(
        C._ID, C.TITLE, C.ARTIST, C.ALBUM, C.ALBUM_ID, C.DURATION, C.TRACK, C.DATE_ADDED, C.DATE_MODIFIED, C.YEAR, C.MIME_TYPE,
    )

    fun hasExtendedColumns(sdk: Int) = sdk >= Build.VERSION_CODES.R

    fun projection(sdk: Int): Array<String> =
        if (hasExtendedColumns(sdk)) BASE + arrayOf(C.ALBUM_ARTIST, C.DISC_NUMBER, C.GENRE) else BASE

    /** Splits a raw TRACK value into (disc, track). */
    fun decodeTrack(raw: Int): Pair<Int, Int> =
        if (raw >= 1000) (raw / 1000) to (raw % 1000) else 1 to raw

    /** DISC_NUMBER is a string like "1/2" (or null). */
    fun parseDisc(raw: String?): Int? = raw?.substringBefore('/')?.trim()?.toIntOrNull()

    private val LOSSLESS_MIME = setOf(
        "audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/wave", "audio/aiff", "audio/x-aiff",
    )

    fun isLossless(mime: String?): Boolean = mime?.lowercase() in LOSSLESS_MIME

    /** MediaStore reports "<unknown>" for missing tags. */
    fun clean(raw: String?, fallback: String): String =
        raw?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: fallback

    fun cleanOrNull(raw: String?): String? = raw?.takeIf { it.isNotBlank() && it != "<unknown>" }
}

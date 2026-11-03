package com.mrgnstr.harmony.core.mediastore

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.AudioColumns as C
import com.mrgnstr.harmony.core.model.LibraryFilters
import com.mrgnstr.harmony.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Music only (IS_MUSIC) and at least [minDurationMs], which drops voice notes and ringtones. */
    suspend fun scan(minDurationMs: Long = 30_000L, excludedFolders: Set<String> = emptySet()): List<Song> = withContext(Dispatchers.IO) {
        val filters = LibraryFilters(minDurationMs, excludedFolders)
        val sdk = Build.VERSION.SDK_INT
        val extended = ColumnMapping.hasExtendedColumns(sdk)
        val songs = ArrayList<Song>(1024)
        val legacyGenres = if (extended) emptyMap() else legacyGenres()
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            ColumnMapping.projection(sdk) + C.DATA,
            "${C.IS_MUSIC} != 0 AND ${C.DURATION} >= ?",
            arrayOf(minDurationMs.toString()),
            "${C.TITLE} COLLATE NOCASE ASC",
        ) ?: error("MediaStore query returned null") // never read as "library is empty"
        cursor.use { c ->
            val path = c.getColumnIndexOrThrow(C.DATA)
            val id = c.getColumnIndexOrThrow(C._ID)
            val title = c.getColumnIndexOrThrow(C.TITLE)
            val artist = c.getColumnIndexOrThrow(C.ARTIST)
            val album = c.getColumnIndexOrThrow(C.ALBUM)
            val albumId = c.getColumnIndexOrThrow(C.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(C.DURATION)
            val track = c.getColumnIndexOrThrow(C.TRACK)
            val added = c.getColumnIndexOrThrow(C.DATE_ADDED)
            val modified = c.getColumnIndexOrThrow(C.DATE_MODIFIED)
            val mimeCol = c.getColumnIndexOrThrow(C.MIME_TYPE)
            val yearCol = c.getColumnIndexOrThrow(C.YEAR)
            val albumArtistCol = if (extended) c.getColumnIndexOrThrow(C.ALBUM_ARTIST) else -1
            val discCol = if (extended) c.getColumnIndexOrThrow(C.DISC_NUMBER) else -1
            val genreCol = if (extended) c.getColumnIndexOrThrow(C.GENRE) else -1

            while (c.moveToNext()) {
                if (filters.excludes(c.getString(path))) continue
                val artistName = ColumnMapping.clean(c.getString(artist), "Unknown Artist")
                val (packedDisc, trackNo) = ColumnMapping.decodeTrack(c.getInt(track))
                val disc = if (extended) ColumnMapping.parseDisc(c.getString(discCol)) ?: packedDisc else packedDisc
                val songId = c.getLong(id)
                songs += Song(
                    id = songId,
                    title = ColumnMapping.clean(c.getString(title), "Unknown"),
                    artist = artistName,
                    albumArtist = if (extended) ColumnMapping.clean(c.getString(albumArtistCol), artistName) else artistName,
                    album = ColumnMapping.clean(c.getString(album), "Unknown Album"),
                    albumId = c.getLong(albumId),
                    durationMs = c.getLong(duration),
                    trackNumber = trackNo,
                    discNumber = disc,
                    dateAddedSec = c.getLong(added),
                    dateModifiedSec = c.getLong(modified),
                    genre = if (extended) ColumnMapping.cleanOrNull(c.getString(genreCol)) else legacyGenres[songId],
                    year = c.getInt(yearCol).coerceAtLeast(0),
                    lossless = ColumnMapping.isLossless(c.getString(mimeCol)),
                )
            }
        }
        songs
    }

    /** All music folders, including excluded folders and songs below the duration threshold. */
    suspend fun musicFolders(): List<String> = withContext(Dispatchers.IO) {
        val folders = sortedSetOf<String>()
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(C.DATA), "${C.IS_MUSIC} != 0", null, null,
        ) ?: error("MediaStore query returned null")
        cursor.use { c ->
            while (c.moveToNext()) {
                c.getString(0)?.substringBeforeLast('/', "")?.takeIf { it.isNotBlank() }?.let { folders += it }
            }
        }
        folders.toList()
    }

    /** API < 30: song id -> genre name via the Genres/Members join. Best effort; OEM providers can be flaky. */
    private fun legacyGenres(): Map<Long, String> {
        val result = HashMap<Long, String>()
        try {
            val resolver = context.contentResolver
            resolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null,
            )?.use { g ->
                while (g.moveToNext()) {
                    val name = ColumnMapping.cleanOrNull(g.getString(1)) ?: continue
                    val members = MediaStore.Audio.Genres.Members.getContentUri("external", g.getLong(0))
                    resolver.query(members, arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null)?.use { m ->
                        while (m.moveToNext()) result.putIfAbsent(m.getLong(0), name)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }
}

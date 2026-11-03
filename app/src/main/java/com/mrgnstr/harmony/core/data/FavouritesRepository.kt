package com.mrgnstr.harmony.core.data

import com.mrgnstr.harmony.core.common.ApplicationScope
import com.mrgnstr.harmony.core.database.AlbumKey
import com.mrgnstr.harmony.core.database.FavouritesDao
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.model.FAVOURITES_PLAYLIST_ID
import com.mrgnstr.harmony.core.model.Playlist
import com.mrgnstr.harmony.core.model.PlaylistEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

const val FAVOURITES_NAME = "Favourites"

/**
 * Favourite songs, albums and artists, kept apart: favouriting one only records that item. The songs are also viewable
 * as the built-in Favourites playlist ([playlist], [entries]), newest favourite first.
 */
@Singleton
class FavouritesRepository @Inject constructor(
    private val dao: FavouritesDao,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Held for the whole app, so menus can read the current state synchronously when they open. */
    val songIds: StateFlow<Set<Long>> = dao.observeSongIds().map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())
    val albums: StateFlow<Set<AlbumKey>> = dao.observeAlbumKeys().map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val artists: StateFlow<Set<String>> = dao.observeArtistNames().map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun isFavouriteArtist(name: String) = name in artists.value

    suspend fun toggleArtist(name: String) = dao.toggleArtist(name, System.currentTimeMillis())

    fun isFavourite(songId: Long) = songId in songIds.value
    fun isFavouriteAlbum(albumId: Long, title: String) = AlbumKey(albumId, title) in albums.value

    suspend fun toggleSong(songId: Long) = dao.toggleSong(songId, System.currentTimeMillis())
    suspend fun toggleAlbum(albumId: Long, title: String) = dao.toggleAlbum(albumId, title, System.currentTimeMillis())

    /** The favourite songs as playlist entries (the entry id is the song id: a song is a favourite at most once). */
    val entries: Flow<List<PlaylistEntry>> = dao.observeSongs()
        .map { rows -> rows.map { PlaylistEntry(it.song.id, it.song.toModel()) } }
        .flowOn(Dispatchers.Default)

    /** The Favourites playlist itself; there is no custom cover, the UI draws the star. */
    val playlist: Flow<Playlist> = dao.observeSongs()
        .map { rows ->
            Playlist(
                id = FAVOURITES_PLAYLIST_ID, name = FAVOURITES_NAME, coverPath = null, songCount = rows.size,
                durationMs = rows.sumOf { it.song.durationMs }, updatedAtMs = rows.maxOfOrNull { it.addedAtMs } ?: 0L, art = emptyList(),
            )
        }
        .flowOn(Dispatchers.Default)
}

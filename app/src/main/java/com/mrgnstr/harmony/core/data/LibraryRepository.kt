package com.mrgnstr.harmony.core.data

import com.mrgnstr.harmony.core.common.ApplicationScope
import com.mrgnstr.harmony.core.database.SongDao
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.datastore.SettingsStore
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.ArtistIndex
import com.mrgnstr.harmony.core.model.LibrarySourceMode
import com.mrgnstr.harmony.core.model.owner
import com.mrgnstr.harmony.core.model.Genre
import com.mrgnstr.harmony.core.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
class LibraryRepository @Inject constructor(
    private val dao: SongDao,
    sync: LibrarySync,
    navidrome: NavidromeRepository,
    settings: SettingsStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val allSongs: Flow<List<Song>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }.flowOn(Dispatchers.Default)

    val songs: Flow<List<Song>> = combine(allSongs, settings.librarySourceMode) { list, mode ->
        when (mode) {
            LibrarySourceMode.All -> list
            LibrarySourceMode.LocalOnly -> list.filter { !it.isRemote }
            LibrarySourceMode.NavidromeOnly -> list.filter { it.isRemote }
            LibrarySourceMode.DownloadedOnly -> list.filter { !it.isRemote || it.isDownloaded }
        }
    }.flowOn(Dispatchers.Default)

    val albums: Flow<List<Album>> = combine(dao.observeAlbums(), songs, settings.librarySourceMode) { rawAlbums, filteredSongs, mode ->
        if (mode == LibrarySourceMode.All) rawAlbums
        else {
            val allowedAlbumIds = filteredSongs.mapTo(HashSet()) { it.albumId to it.album }
            rawAlbums.filter { (it.id to it.title) in allowedAlbumIds }
        }
    }.flowOn(Dispatchers.Default)
    val artistIndex: Flow<ArtistIndex> = songs.map(ArtistIndex::of).flowOn(Dispatchers.Default)

    /** One entry per artist, with collaboration credits split into their artists (see [ArtistIndex]). */
    val artists: Flow<List<Artist>> = songs.map { list ->
        val index = ArtistIndex.of(list)
        class Acc(val name: String) { var songs = 0; val albums = HashSet<Long>(); var artAlbumId = Long.MAX_VALUE; var artSongId = Long.MAX_VALUE }
        val byName = HashMap<String, Acc>()
        for (song in list) for (name in index.artistsOf(song.owner)) {
            val acc = byName.getOrPut(name.lowercase()) { Acc(name) }
            acc.songs++
            acc.albums += song.albumId
            acc.artAlbumId = minOf(acc.artAlbumId, song.albumId)
            acc.artSongId = minOf(acc.artSongId, song.id)
        }
        byName.values.map { Artist(it.name, it.songs, it.albums.size, it.artAlbumId, it.artSongId) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }.flowOn(Dispatchers.Default)
    /** The albums with at least one song in [genre]. */
    fun genreAlbums(genre: String): Flow<List<Album>> =
        combine(dao.observeGenreAlbumKeys(genre), albums) { keys, albums ->
            val wanted = keys.mapTo(HashSet()) { it.albumId to it.title }
            albums.filter { (it.id to it.title) in wanted }
        }.flowOn(Dispatchers.Default)

    val genres: Flow<List<Genre>> = dao.observeGenres().flowOn(Dispatchers.Default)
    fun albumSongs(albumId: Long, title: String): Flow<List<Song>> =
        dao.observeAlbumSongs(albumId, title).map { rows -> rows.map { it.toModel() } }.flowOn(Dispatchers.Default)
    /** Songs whose owner credit includes [name] (solo and collaboration releases), newest release first. */
    fun artistSongs(name: String): Flow<List<Song>> = songs.map { list ->
        val index = ArtistIndex.of(list)
        list.filter { index.credits(it.owner, name) }.sortedWith(ArtistOrder)
    }.flowOn(Dispatchers.Default)

    /** Songs on other artists' releases whose artist field mentions [name]; callers refine the match. */
    fun songsMentioning(name: String): Flow<List<Song>> = songs.map { list ->
        val index = ArtistIndex.of(list)
        list.filter { it.artist.contains(name, ignoreCase = true) && !index.credits(it.owner, name) }
    }.flowOn(Dispatchers.Default)

    val syncState: StateFlow<SyncState> = combine(sync.state, navidrome.syncState) { local, remote ->
        SyncState(
            syncing = local.syncing || remote.syncing,
            completedOnce = local.completedOnce || remote.completedOnce,
            generation = local.generation + remote.generation,
            failed = local.failed && remote.failed,
        )
    }.stateIn(scope, SharingStarted.Eagerly, sync.state.value)
}

private val ArtistOrder = compareByDescending<Song> { it.year }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }
    .thenBy { it.discNumber }
    .thenBy { it.trackNumber }

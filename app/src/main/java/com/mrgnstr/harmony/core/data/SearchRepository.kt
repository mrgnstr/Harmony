package com.mrgnstr.harmony.core.data

import com.mrgnstr.harmony.core.database.RecentSearchEntity
import com.mrgnstr.harmony.core.database.SearchDao
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.Playlist
import com.mrgnstr.harmony.core.model.SearchCategory
import com.mrgnstr.harmony.core.model.SearchRanking
import com.mrgnstr.harmony.core.model.SearchQuery
import com.mrgnstr.harmony.core.model.Song
import com.mrgnstr.harmony.core.model.heroOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

data class SearchResults(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val categoryOrder: List<SearchCategory> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

@Singleton
class SearchRepository @Inject constructor(
    private val dao: SearchDao,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
) {
    val recents = dao.recents()

    fun search(query: String): Flow<SearchResults> {
        val match = SearchQuery.match(query) ?: return flowOf(SearchResults(query))
        return combine(
            dao.songs(match),
            library.albums,
            library.artists,
            playlists.playlistsWithFavourites,
        ) { rows, albums, artists, allPlaylists ->
            val songs = rows.map { it.toModel() }.sortedByDescending { SearchRanking.score(it.title, query) }
            val matchedAlbums = albums.filter { SearchQuery.matches("${it.title} ${it.artist}", query) }
                .sortedByDescending { SearchRanking.score(it.title, query) }
            val matchedArtists = artists.filter { SearchQuery.matches(it.name, query) }
                .sortedByDescending { SearchRanking.score(it.name, query) }
            val matchedPlaylists = allPlaylists
                .filter { (!it.isFavourites || it.songCount > 0) && SearchQuery.matches(it.name, query) }
                .sortedByDescending { SearchRanking.score(it.name, query) }
            SearchResults(
                query = query,
                songs = songs,
                albums = matchedAlbums,
                artists = matchedArtists,
                playlists = matchedPlaylists,
                categoryOrder = SearchRanking.categories(
                    query = query,
                    songTitles = songs.map { it.title },
                    albumTitles = matchedAlbums.map { it.title },
                    artistNames = matchedArtists.map { it.name },
                    playlistNames = matchedPlaylists.map { it.name },
                ),
            )
        }.flowOn(Dispatchers.Default)
    }

    suspend fun remember(query: String) {
        val display = query.trim().replace(Regex("""\s+"""), " ").take(200)
        if (SearchQuery.match(display) != null) dao.remember(RecentSearchEntity(SearchQuery.recentKey(display), display, System.currentTimeMillis()))
    }

    suspend fun removeRecent(key: String) = dao.removeRecent(key)
    suspend fun clearRecents() = dao.clearRecents()
}

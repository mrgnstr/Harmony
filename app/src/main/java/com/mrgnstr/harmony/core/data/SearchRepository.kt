package com.mrgnstr.harmony.core.data

import com.mrgnstr.harmony.core.database.RecentSearchEntity
import com.mrgnstr.harmony.core.database.SearchDao
import com.mrgnstr.harmony.core.database.toModel
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.SearchCategory
import com.mrgnstr.harmony.core.model.SearchRanking
import com.mrgnstr.harmony.core.model.SearchQuery
import com.mrgnstr.harmony.core.model.Song
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
    val categoryOrder: List<SearchCategory> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

@Singleton
class SearchRepository @Inject constructor(
    private val dao: SearchDao,
    private val library: LibraryRepository,
    private val images: ArtistImageStore,
) {
    val recents = dao.recents()

    fun search(query: String): Flow<SearchResults> {
        val match = SearchQuery.match(query) ?: return flowOf(SearchResults(query))
        return combine(dao.songs(match), library.albums, library.artists, images.images) { rows, albums, artists, images ->
            val songs = rows.map { it.toModel() }.sortedByDescending { SearchRanking.score(it.title, query) }
            val matchedAlbums = albums.filter { SearchQuery.matches("${it.title} ${it.artist}", query) }
                .sortedByDescending { SearchRanking.score(it.title, query) }
            val matchedArtists = artists.filter { SearchQuery.matches(it.name, query) }
                .sortedByDescending { SearchRanking.score(it.name, query) }.map { it.copy(imagePath = images[it.name]?.path) }
            SearchResults(
                query = query, songs = songs, albums = matchedAlbums, artists = matchedArtists,
                categoryOrder = SearchRanking.categories(query, songs.map { it.title }, matchedAlbums.map { it.title }, matchedArtists.map { it.name }),
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

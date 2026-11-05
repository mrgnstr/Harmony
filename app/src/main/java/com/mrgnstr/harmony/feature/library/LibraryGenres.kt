package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mrgnstr.harmony.core.data.LibraryRepository
import com.mrgnstr.harmony.core.designsystem.components.AlbumGridPage
import com.mrgnstr.harmony.core.designsystem.components.GenreCardTile
import com.mrgnstr.harmony.core.designsystem.components.TileGrid
import com.mrgnstr.harmony.core.designsystem.components.gridCellPadding
import com.mrgnstr.harmony.core.designsystem.components.itemMotion
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.SortKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Composable
fun GenresTab(viewModel: LibraryViewModel, onOpenGenre: (String) -> Unit) {
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val key = viewModel.sorts.collectAsStateWithLifecycle().value.getValue(LibraryTab.Genres).key
    if (genres.isEmpty()) return LibraryEmpty(viewModel, Symbols.Genres, "No genres", "Genres come from the tags in your music files.")
    TileGrid(genres.size, { i -> genres.getOrNull(i)?.takeIf { key == SortKey.Title }?.let { letterOf(it.name) } }) { columns ->
        itemsIndexed(genres, key = { _, g -> g.name }, contentType = { _, _ -> "genre" }) { i, genre ->
            GenreCardTile(genre, { onOpenGenre(genre.name) }, itemMotion().padding(gridCellPadding(i % columns, columns)))
        }
    }
}

data class GenreUiState(val albums: List<Album> = emptyList(), val loaded: Boolean = false)

@HiltViewModel(assistedFactory = GenreViewModel.Factory::class)
class GenreViewModel @AssistedInject constructor(
    @Assisted genre: String,
    library: LibraryRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(genre: String): GenreViewModel
    }

    val state: StateFlow<GenreUiState> = combine(library.genreAlbums(genre), library.syncState) { albums, sync ->
        GenreUiState(
            albums = albums.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
            loaded = sync.completedOnce || sync.failed || albums.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenreUiState())
}

@Composable
fun GenreRoute(
    name: String,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    viewModel: GenreViewModel = hiltViewModel<GenreViewModel, GenreViewModel.Factory>(
        key = "genre:$name",
        creationCallback = { it.create(name) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlbumGridPage(title = name, subtitle = "Genre", albums = state.albums, loaded = state.loaded, onBack = onBack, onOpenAlbum = onOpenAlbum)
}

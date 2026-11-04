package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import com.mrgnstr.harmony.core.model.Album
import com.mrgnstr.harmony.core.model.Artist
import com.mrgnstr.harmony.core.model.LibrarySort
import kotlinx.coroutines.launch

/**
 * Library: a collapsing large title, a scrollable tab row and one page per tab in a pager (swipe or tap). Each tab keeps its own
 * ordering, remembered across launches.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryRoute(
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenGenre: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tabs = LibraryTab.entries
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val sorts by viewModel.sorts.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val current = tabs[pager.currentPage]
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                LargeFlexibleTopAppBar(
                    title = { Text("Library") },
                    actions = {
                        if (current.keys.isNotEmpty()) {
                            IconButton(onClick = { sortMenuOpen = true }) { SymbolIcon(Symbols.Sort, "Sort") }
                            SortMenu(sortMenuOpen, { sortMenuOpen = false }, current, sorts.getValue(current)) { viewModel.setSort(current, it) }
                        }
                        IconButton(onClick = onOpenSettings) { SymbolIcon(Symbols.Settings, "Settings") }
                    },
                    scrollBehavior = scroll,
                )
                PrimaryScrollableTabRow(
                    selectedTabIndex = pager.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 16.dp,
                    divider = {},
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index, animationSpec = Springs.land()) } },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize(), contentPadding = padding, key = { tabs[it].name }) { page ->
            when (tabs[page]) {
                LibraryTab.RecentlyAdded -> RecentlyAddedTab(viewModel, onOpenAlbum)
                LibraryTab.Albums -> AlbumsTab(viewModel, onOpenAlbum)
                LibraryTab.Songs -> SongsTab(viewModel)
                LibraryTab.Artists -> ArtistsTab(viewModel, onOpenArtist)
                LibraryTab.Playlists -> PlaylistsTab(viewModel, onOpenPlaylist)
                LibraryTab.Genres -> GenresTab(viewModel, onOpenGenre)
            }
        }
    }
}

/** The tab's keys with a check on the chosen one, then the direction. Choosing the chosen key again flips the direction too. */
@Composable
private fun SortMenu(open: Boolean, onDismiss: () -> Unit, tab: LibraryTab, sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    DropdownMenu(open, onDismiss) {
        tab.keys.forEach { key ->
            DropdownMenuItem(
                text = { Text(key.label) },
                trailingIcon = { if (key == sort.key) SymbolIcon(Symbols.Check, null) },
                onClick = { onSort(sort.choose(key)); onDismiss() },
            )
        }
        HorizontalDivider()
        listOf(true to "Ascending", false to "Descending").forEach { (ascending, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                trailingIcon = { if (sort.ascending == ascending) SymbolIcon(Symbols.Check, null) },
                onClick = { onSort(sort.copy(ascending = ascending)); onDismiss() },
            )
        }
    }
}

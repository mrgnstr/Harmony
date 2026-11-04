package com.mrgnstr.harmony.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
import com.mrgnstr.harmony.core.designsystem.blur.chromeSource
import com.mrgnstr.harmony.core.designsystem.blur.rememberChromeBlurState
import kotlin.math.roundToInt
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.core.designsystem.components.TopBarScaffold
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
    val current = tabs[pager.currentPage]
    var sortMenuOpen by remember { mutableStateOf(false) }
    TopBarScaffold(
        title = "Library",
        actions = {
            if (current.keys.isNotEmpty()) {
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) { SymbolIcon(Symbols.Sort, "Sort") }
                    SortMenu(sortMenuOpen, { sortMenuOpen = false }, current, sorts.getValue(current)) { viewModel.setSort(current, it) }
                }
            }
            IconButton(onClick = onOpenSettings) { SymbolIcon(Symbols.Settings, "Settings") }
        },
        tabs = {
            PrimaryScrollableTabRow(
                selectedTabIndex = pager.currentPage,
                containerColor = Color.Transparent,
                edgePadding = TabEdgePadding,
                indicator = {
                    // Follows the swipe: between two tabs it sits between them, its width blending too.
                    TabRowDefaults.PrimaryIndicator(
                        Modifier.tabIndicatorLayout { measurable, constraints, positions ->
                            val at = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, positions.lastIndex.toFloat())
                            val from = positions[at.toInt()]
                            val to = positions[(at.toInt() + 1).coerceAtMost(positions.lastIndex)]
                            val t = at - at.toInt()
                            // The indicator's slot already sits past the row's edge padding, which the tab positions include.
                            fun left(p: TabPosition) = p.left + (p.width - p.contentWidth) / 2 - TabEdgePadding
                            val width = lerp(from.contentWidth, to.contentWidth, t).roundToPx()
                            val placeable = measurable.measure(Constraints(width, width, 0, constraints.maxHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(lerp(left(from), left(to), t).roundToPx(), constraints.maxHeight - placeable.height)
                            }
                        },
                        width = Dp.Unspecified,
                    )
                },
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
        },
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { tabs[it].name }) { page ->
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

private val TabEdgePadding = 16.dp

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

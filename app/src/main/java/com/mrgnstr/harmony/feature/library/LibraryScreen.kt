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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
    // Where each tab's label is drawn (x in the window, width), and where the indicator's slot starts: the indicator is placed from these.
    val labels = remember { mutableStateListOf<Pair<Float, Int>>().apply { repeat(tabs.size) { add(0f to 0) } } }
    var slotX by remember { mutableFloatStateOf(0f) }
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
                    // Follows the swipe: between two tabs it sits between them, its width blending too. It lines up with the label as
                    // drawn (measured), not with the tab positions: a tab wider than its label keeps the label off its centre.
                    TabRowDefaults.PrimaryIndicator(
                        Modifier.onGloballyPositioned { slotX = it.positionInRoot().x }.tabIndicatorLayout { measurable, constraints, positions ->
                            val at = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, positions.lastIndex.toFloat())
                            val i = at.toInt()
                            val j = (i + 1).coerceAtMost(positions.lastIndex)
                            val t = at - i
                            val a = labels[i]
                            val b = labels[j]
                            val width: Int
                            val x: Float
                            if (a.second > 0 && b.second > 0) {
                                width = lerp(a.second.toFloat(), b.second.toFloat(), t).roundToInt()
                                x = lerp(a.first, b.first, t) - slotX
                            } else {
                                // Not measured yet: the row's own positions (its slot already sits past the edge padding).
                                fun left(p: TabPosition) = p.left + (p.width - p.contentWidth) / 2 - TabEdgePadding
                                width = lerp(positions[i].contentWidth, positions[j].contentWidth, t).roundToPx()
                                x = lerp(left(positions[i]), left(positions[j]), t).toPx()
                            }
                            val placeable = measurable.measure(Constraints(width, width, 0, constraints.maxHeight))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place(x.roundToInt(), constraints.maxHeight - placeable.height)
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
                        text = {
                            Text(tab.label, Modifier.onGloballyPositioned { labels[index] = it.positionInRoot().x to it.size.width })
                        },
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

/** Full collections linked by preview sections; reuse the Library tabs' existing grid/list and saved ordering. */
@Composable
fun LibraryAlbumsRoute(
    onBack: () -> Unit, onOpenAlbum: (Album) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(key = "library:expanded-albums"),
) {
    TopBarScaffold("Albums", onBack = onBack) { AlbumsTab(viewModel, onOpenAlbum) }
}

@Composable
fun LibrarySongsRoute(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(key = "library:expanded-songs"),
) {
    TopBarScaffold("Songs", onBack = onBack) { SongsTab(viewModel) }
}

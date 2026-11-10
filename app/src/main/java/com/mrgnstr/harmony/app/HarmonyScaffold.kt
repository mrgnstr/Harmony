package com.mrgnstr.harmony.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.snapshotFlow
import android.os.SystemClock
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.scene.Scene
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.mrgnstr.harmony.core.designsystem.LocalSideInsets
import com.mrgnstr.harmony.core.designsystem.LocalWindowLayout
import com.mrgnstr.harmony.core.designsystem.SideInsets
import com.mrgnstr.harmony.core.designsystem.blur.FloatingChromeMargin
import com.mrgnstr.harmony.core.designsystem.rememberWindowLayout
import com.mrgnstr.harmony.core.model.TabBarSide
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.feature.nowplaying.MiniDock
import com.mrgnstr.harmony.feature.nowplaying.MiniPlayerGap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.mrgnstr.harmony.core.designsystem.motion.Springs
import com.mrgnstr.harmony.feature.nowplaying.MiniPlayerHeight
import com.mrgnstr.harmony.feature.nowplaying.NowPlayingViewModel
import com.mrgnstr.harmony.feature.nowplaying.PlayerSheet
import com.mrgnstr.harmony.feature.nowplaying.rememberPlayerSheetState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlinx.coroutines.delay
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalOpenAlbum
import com.mrgnstr.harmony.core.designsystem.LocalOpenArtist
import com.mrgnstr.harmony.core.designsystem.LocalSharedTransitionScope
import com.mrgnstr.harmony.core.designsystem.components.TopBarRowHeight
import com.mrgnstr.harmony.core.designsystem.components.CoverShare
import com.mrgnstr.harmony.core.designsystem.components.LocalCoverShare
import com.mrgnstr.harmony.core.designsystem.blur.LocalChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.chromeSource
import com.mrgnstr.harmony.core.designsystem.blur.rememberChromeBlurState
import com.mrgnstr.harmony.core.designsystem.LocalChromeBlurMode
import com.mrgnstr.harmony.core.designsystem.LocalCoverBlurMode
import com.mrgnstr.harmony.core.designsystem.theme.ColorSource
import com.mrgnstr.harmony.core.designsystem.theme.ColorSourceRegistry
import com.mrgnstr.harmony.core.designsystem.theme.HarmonyTheme
import com.mrgnstr.harmony.core.designsystem.theme.LocalColorSourceRegistry
import com.mrgnstr.harmony.core.designsystem.theme.LocalEntryKey
import com.mrgnstr.harmony.core.designsystem.theme.SchemeFactory
import com.mrgnstr.harmony.core.designsystem.theme.animateColorScheme
import com.mrgnstr.harmony.core.designsystem.theme.lerp
import com.mrgnstr.harmony.core.designsystem.theme.rememberColorSourceRegistry
import com.mrgnstr.harmony.core.device.VisualTier
import com.mrgnstr.harmony.core.model.BlurMode
import com.mrgnstr.harmony.core.model.ArtistListKind
import com.mrgnstr.harmony.feature.artist.ArtistReleasesRoute
import com.mrgnstr.harmony.feature.artist.SimilarArtistsRoute
import com.mrgnstr.harmony.feature.home.FavouriteArtistsRoute
import com.mrgnstr.harmony.feature.library.PlaylistArtistsRoute
import com.mrgnstr.harmony.feature.album.AlbumShelfRoute
import com.mrgnstr.harmony.feature.library.LibraryAlbumsRoute
import com.mrgnstr.harmony.feature.library.LibrarySongsRoute
import com.mrgnstr.harmony.feature.artist.ArtistRoute
import com.mrgnstr.harmony.feature.artist.ArtistSongsRoute
import com.mrgnstr.harmony.feature.artist.ArtistInfoRoute
import com.mrgnstr.harmony.feature.album.AlbumRoute
import com.mrgnstr.harmony.feature.gallery.GalleryRoute
import com.mrgnstr.harmony.feature.home.HomeRoute
import com.mrgnstr.harmony.feature.home.HomeShelfRoute
import com.mrgnstr.harmony.feature.library.GenreRoute
import com.mrgnstr.harmony.feature.library.LibraryRoute
import com.mrgnstr.harmony.feature.library.PlaylistRoute
import com.mrgnstr.harmony.feature.onboarding.NotificationPermissionEffect
import com.mrgnstr.harmony.feature.onboarding.OnboardingScreen
import com.mrgnstr.harmony.feature.onboarding.rememberAudioPermissionState
import com.mrgnstr.harmony.feature.search.SearchRoute
import com.mrgnstr.harmony.feature.settings.SettingsRoute

/** A back stack entry, scoped to its tab: the same album can be open on two tabs with independent state. */
private data class TabDestination(val tab: Tab, val destination: Dest)
/** Expanded collections and artist child pages inherit a colour before their ViewModel or PageColorSource has run, including after restoration. */
private fun TabDestination.initialColorSource(): ColorSource {
    val accent = when (val dest = destination) {
        is Dest.ArtistInfo -> dest.accent
        is Dest.ArtistSongs -> dest.accent
        is Dest.ArtistReleases -> dest.accent
        is Dest.AlbumShelf -> dest.accent
        is Dest.ArtistList -> dest.accent
        is Dest.LibraryAlbums -> dest.accent
        is Dest.LibrarySongs -> dest.accent
        else -> null
    }
    return accent?.let { ColorSource.Seed(it) } ?: ColorSource.Wallpaper
}

private const val TabMetadata = "harmony.tab"
private const val PrebuildDelayMs = 1500L
private const val PrebuildGapMs = 400L
private const val EnterSnapMs = 450L
// The bar and mini player recompose their whole subtree per notch of the colour blend: few notches, since each one costs a frame.
private const val ChromeBlendSteps = 8

/**
 * The app: themed with the wallpaper's colours at the top (it never changes while the app runs), then the pages with the floating bar
 * over them. Pages wear their own colours ([EntryTheme]); the bar and the mini player blend to the page on top in [ChromeTheme].
 * Colour schemes are a static composition local, so whatever changes one recomposes everything under it: blending the whole app's
 * theme on every page change cost ~20 ms per blend step, and now only the bar and mini player are under the blending theme.
 */
@Composable
fun HarmonyApp(
    dark: Boolean, tier: VisualTier, chromeBlur: BlurMode, coverBlur: BlurMode, schemes: SchemeFactory, openPlayer: Int = 0,
    tabBarSide: TabBarSide = TabBarSide.Left,
) {
    val nav = rememberTabNavigator()
    val registry = rememberColorSourceRegistry()
    val windowLayout = rememberWindowLayout()

    HarmonyTheme(schemes.scheme(ColorSource.Wallpaper, dark), tier, chromeBlur, coverBlur) {
        CompositionLocalProvider(LocalColorSourceRegistry provides registry, LocalWindowLayout provides windowLayout) {
            val permission = rememberAudioPermissionState()
            val connectVm: com.mrgnstr.harmony.core.designsystem.components.NavidromeConnectViewModel = hiltViewModel()
            val activeServer by connectVm.repository.activeAccount.collectAsStateWithLifecycle()
            if (permission.granted || activeServer != null) ProvideOfflineDim { Shell(nav, openPlayer, schemes, dark, tabBarSide) } else OnboardingScreen(permission)
        }
    }
}

/**
 * The scheme of the page on top (blending towards the page below while a back gesture is in progress), animated, around the floating
 * chrome: the tab bar and the mini player / Now Playing sheet.
 */
@Composable
private fun ChromeTheme(nav: TabNavigator, schemes: SchemeFactory, dark: Boolean, tier: VisualTier, content: @Composable () -> Unit) {
    val registry = LocalColorSourceRegistry.current
    val back by rememberBackProgress()
    val stack = nav.backStack
    val top = TabDestination(nav.current, stack.last())
    val below = stack.getOrNull(stack.size - 2)?.let { TabDestination(nav.current, it) }
    val topScheme = schemes.scheme(registry?.sourceOf(top, top.initialColorSource()) ?: top.initialColorSource(), dark)
    val gesture = back != null && below != null
    val target = if (gesture) lerp(topScheme, schemes.scheme(registry?.sourceOf(below, below.initialColorSource()) ?: below.initialColorSource(), dark), back!!) else topScheme
    val scheme = animateColorScheme(target, snap = gesture, lowTier = tier == VisualTier.LOW, steps = ChromeBlendSteps)
    HarmonyTheme(scheme, tier, LocalChromeBlurMode.current, LocalCoverBlurMode.current, content)
}

/** Progress (0…1) of a back gesture in progress, null when there is none. */
@Composable
private fun rememberBackProgress(): State<Float?> {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    return produceState<Float?>(null, dispatcher) {
        dispatcher?.transitionState?.collect { state ->
            value = (state as? NavigationEventTransitionState.InProgress)
                ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
                ?.latestEvent?.progress
        }
    }
}

@Composable
private fun Shell(nav: TabNavigator, openPlayer: Int, schemes: SchemeFactory, dark: Boolean, tabBarSide: TabBarSide) {
    val app = hiltViewModel<AppViewModel>()
    NotificationPermissionEffect()
    val tier = androidx.compose.runtime.rememberUpdatedState(com.mrgnstr.harmony.core.designsystem.LocalVisualTier.current)
    // The entry lambdas below are remembered: read the factory through a state, or pages keep the old accent's schemes.
    val liveSchemes = androidx.compose.runtime.rememberUpdatedState(schemes)
    val blur = rememberChromeBlurState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barTop = navBottom + AppBarEdgeGap + AppBarHeight
    // Wide layouts (tablets, phones on their side): the bar stands on a side, Search on top and Home at the bottom, and the mini player
    // sits on the bar's bottom row beside it. Phones' side is their own bottom edge (WindowLayout.barSide), tablets' the setting.
    val windowLayout = LocalWindowLayout.current
    val vertical = windowLayout.sideChrome
    val barSide = windowLayout.barSide(tabBarSide)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val safeLeft = with(density) { WindowInsets.safeDrawing.getLeft(density, layoutDirection).toDp() }
    val safeRight = with(density) { WindowInsets.safeDrawing.getRight(density, layoutDirection).toDp() }
    // What pages keep clear of at the sides: the cutout / side navigation bar, and the bar's rail on its side.
    // The side bar is as long as it can be (its maximum, or less where the screen is short): the height between the bottom gap and the
    // top bar's collapsed row, which is where it would first run into a page's title bar (the expanded title row is in the page's own way).
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val windowHeight = with(density) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() }
    val slotLength = sideBarSlotLength(windowHeight - statusTop - TopBarRowHeight - 8.dp - navBottom - AppBarEdgeGap)
    // Centred on the screen when it can be without touching the collapsed top bar (or the bottom gap); otherwise as far down as it goes.
    val bottomGap = navBottom + AppBarEdgeGap
    val barLength = AppBarHeight + BarGap + slotLength * 2
    val centredGap = (windowHeight - barLength) / 2
    val sideBarBottom = if (centredGap >= statusTop + TopBarRowHeight + 8.dp && centredGap >= bottomGap) centredGap else bottomGap
    // The mini player sits beside the bar only while the bar comes down into its row.
    val barBesideMini = sideBarBottom < bottomGap + AppBarHeight + BarGap
    val rail = FloatingChromeMargin + AppBarHeight + FloatingChromeMargin
    val sideInsets = if (vertical) {
        SideInsets(left = safeLeft + if (barSide == TabBarSide.Left) rail else 0.dp, right = safeRight + if (barSide == TabBarSide.Right) rail else 0.dp)
    } else SideInsets()
    val player = hiltViewModel<NowPlayingViewModel>()
    val hasMedia by player.snapshot.collectAsStateWithLifecycle().let { st -> androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { st.value.hasMedia } } }
    val sheet = rememberPlayerSheetState()
    // Tabs are built when first shown, and the rest a moment after launch so the first switch to them is free too.
    val built = remember(nav) { mutableStateSetOf(nav.current) }
    LaunchedEffect(nav.current) { built.add(nav.current) }
    LaunchedEffect(Unit) { delay(PrebuildDelayMs); Tab.entries.forEach { built.add(it); delay(PrebuildGapMs) } }
    // Which tab is on screen, as a position along the row of tabs (a switch glides it; a drag could drive it).
    val position = remember { Animatable(nav.current.ordinal.toFloat()) }
    LaunchedEffect(nav.current) {
        val target = nav.current.ordinal.toFloat()
        if (tier.value == VisualTier.LOW) position.animateTo(target, tween(180, easing = FastOutSlowInEasing))
        else position.animateTo(target, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
    }
    // The tab bar minimizes to the selected tab's circle while the pages scroll down (iOS 26), and the mini player moves in beside it.
    val minimize = rememberBarMinimize()
    // Only with a mini player: without one the bar stays whole (and goes back to whole when the last song is cleared).
    // (Nor in wide layouts: the bar stands on a side there and takes no room from the content to give back.)
    LaunchedEffect(hasMedia, vertical) {
        minimize.enabled = hasMedia && !vertical
        if (!hasMedia || vertical) minimize.expand()
    }
    val barMinimize = remember { Animatable(0f) }
    val minimized = minimize.minimized && nav.current != Tab.Search
    LaunchedEffect(minimized) { barMinimize.animateTo(if (minimized) 1f else 0f, Springs.land(stiffness = 450f, visibilityThreshold = 0.001f)) }
    // The same scrolling minimizes the bar on the Search tab, but there the pill folds away entirely and the mini player takes its
    // side; the Search button (like scrolling back up) brings it back.
    val foldTarget = minimize.minimized && nav.current == Tab.Search
    val barFold = remember { Animatable(0f) }
    LaunchedEffect(foldTarget) { barFold.animateTo(if (foldTarget) 1f else 0f, Springs.land(stiffness = 450f, visibilityThreshold = 0.001f)) }
    // A new tab or page starts with the full bar.
    LaunchedEffect(nav.current, nav.stackOf(nav.current).lastOrNull()) { minimize.expand() }
    // The notification was tapped: open Now Playing.
    androidx.compose.runtime.LaunchedEffect(openPlayer) { if (openPlayer > 0) sheet.expand() }
    var rootHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // Pages pad for the tab bar only: scrolled to the end the bar is minimized and the mini player sits in its row, so no room is kept
    // for the mini player above it. In a wide layout the bar is at the side: only the mini player's row is kept (when there is one).
    val bottomInset = if (vertical && !hasMedia) navBottom else barTop

    val entries = remember(nav) {
        val openSettings = { nav.push(Dest.Settings); Unit }
        val openGallery = { nav.push(Dest.Gallery); Unit }
        val openAlbum: (com.mrgnstr.harmony.core.model.Album) -> Unit = { nav.push(Dest.Album(it.id, it.title)) }
        val openArtist: (String) -> Unit = { nav.push(Dest.Artist(it)) }
        val openPlaylist: (Long) -> Unit = { nav.push(Dest.Playlist(it)) }
        val openGenre: (String) -> Unit = { nav.push(Dest.Genre(it)) }
        val back = { nav.pop(); Unit }
        entryProvider<Dest> {
            entry<Dest.Root> { root ->
                when (root.tab) {
                    Tab.Home -> HomeRoute(
                        onOpenSettings = openSettings,
                        onOpenAlbum = openAlbum,
                        onOpenArtist = openArtist,
                        onOpenPlaylist = openPlaylist,
                        onOpenShelf = { nav.push(Dest.HomeShelfDest(it)) },
                        onOpenFavouriteArtists = { nav.push(Dest.ArtistList(ArtistListKind.Favourites)) },
                    )
                    Tab.Library -> LibraryRoute(
                        onOpenSettings = openSettings, onOpenGallery = openGallery,
                        onOpenAlbum = openAlbum, onOpenArtist = { openArtist(it.name) },
                        onOpenPlaylist = openPlaylist, onOpenGenre = openGenre,
                    )
                    Tab.Search -> SearchRoute(
                        onOpenSettings = openSettings,
                        onOpenAlbum = openAlbum,
                        onOpenArtist = openArtist,
                        onOpenPlaylist = openPlaylist,
                        onOpenGenre = openGenre,
                        active = nav.current == Tab.Search,
                    )
                }
            }
            entry<Dest.Settings> { SettingsRoute(onBack = back, onOpenGallery = openGallery) }
            entry<Dest.Gallery> {
                GalleryRoute(onBack = back,
                    onOpenAlbums = { nav.push(Dest.LibraryAlbums(it)) },
                    onOpenSongs = { nav.push(Dest.LibrarySongs(it)) },
                    onOpenAlbum = openAlbum)
            }
            entry<Dest.Album>(metadata = mapOf(CoverPageMetadata to true)) { album ->
                AlbumRoute(
                    album.albumId, album.title, onBack = back,
                    onOpenAlbum = openAlbum, onOpenArtist = openArtist,
                    onOpenShelf = { shelf, accent -> nav.push(Dest.AlbumShelf(album.albumId, album.title, shelf, accent)) },
                )
            }
            entry<Dest.Artist> { artist ->
                ArtistRoute(artist.name, onBack = back,
                    onOpenAlbum = openAlbum,
                    onAllSongs = { accent -> nav.push(Dest.ArtistSongs(artist.name, accent)) },
                    onInfo = { accent -> nav.push(Dest.ArtistInfo(artist.name, accent)) },
                    onOpenShelf = { shelf, accent -> nav.push(Dest.ArtistReleases(artist.name, shelf, accent)) },
                    onSimilar = { accent -> nav.push(Dest.ArtistList(ArtistListKind.Similar, artist.name, accent)) })
            }
            entry<Dest.ArtistSongs> { ArtistSongsRoute(it.name, onBack = back) }
            entry<Dest.ArtistInfo> { ArtistInfoRoute(it.name, onBack = back) }
            entry<Dest.ArtistReleases> { collection ->
                ArtistReleasesRoute(collection.name, collection.shelf, onBack = back,
                    onOpenAlbum = openAlbum)
            }
            entry<Dest.AlbumShelf> { collection ->
                AlbumShelfRoute(collection.albumId, collection.title, collection.shelf, onBack = back,
                    onOpenAlbum = openAlbum)
            }
            entry<Dest.ArtistList> { list ->
                when (list.kind) {
                    ArtistListKind.Favourites -> FavouriteArtistsRoute(onBack = back, onOpenArtist = openArtist)
                    ArtistListKind.Featured -> PlaylistArtistsRoute(list.ref.toLong(), onBack = back, onOpenArtist = openArtist)
                    ArtistListKind.Similar -> SimilarArtistsRoute(list.ref, onBack = back, onOpenArtist = openArtist)
                }
            }
            entry<Dest.HomeShelfDest> { collection ->
                HomeShelfRoute(collection.shelf, onBack = back, onOpenAlbum = openAlbum)
            }
            entry<Dest.LibraryAlbums> { LibraryAlbumsRoute(onBack = back, onOpenAlbum = openAlbum) }
            entry<Dest.LibrarySongs> { LibrarySongsRoute(onBack = back) }
            entry<Dest.Genre> { GenreRoute(it.name, onBack = back, onOpenAlbum = openAlbum) }
            entry<Dest.Playlist> { playlist ->
                PlaylistRoute(playlist.playlistId, onBack = back, onOpenAlbum = openAlbum, onOpenArtist = openArtist,
                    onOpenArtists = { accent -> nav.push(Dest.ArtistList(ArtistListKind.Featured, playlist.playlistId.toString(), accent)) })
            }
        }
    }
    // Whether a back gesture is in progress (its pages animate as going back before the pop itself happens).
    val backProgress = rememberBackProgress()
    val backGesture = remember { androidx.compose.runtime.derivedStateOf { backProgress.value != null } }
    // When a swipe was last under way: a back that completes right after it is the swipe's, not the arrow's.
    val lastGestureAt = remember { LongArray(1) }
    LaunchedEffect(backProgress) { snapshotFlow { backProgress.value }.collect { if (it != null) lastGestureAt[0] = SystemClock.uptimeMillis() } }
    // Tab in the entry's identity and metadata (for the tab slide); the entry key is what pages claim colours under.
    val tabEntries = remember(entries) {
        { key: TabDestination ->
            val entry = entries(key.destination)
            NavEntry(key = key, contentKey = "${key.tab.name}:${entry.contentKey}", metadata = entry.metadata + (TabMetadata to key.tab)) {
                // Popped, or the top page while a back gesture holds it: the page that is going away backwards.
                val leavingBack = key !in nav.stackOf(key.tab).map { TabDestination(key.tab, it) } || nav.stackOf(key.tab).lastOrNull() == key.destination
                BackEffects(leavingBack, backing = nav.lastWasBack || backGesture.value, blurred = tier.value != VisualTier.LOW,
                    ms = if (backGesture.value || nav.lastBackByGesture) GestureMs else BackMs) {
                    CompositionLocalProvider(LocalEntryKey provides key) { EntryTheme(key, liveSchemes.value, dark, tier.value) { entry.Content() } }
                }
            }
        }
    }

    val coverShare = remember { CoverShare() }
    val openAlbumGlobal = remember(nav) { { album: com.mrgnstr.harmony.core.model.Album -> nav.push(Dest.Album(album.id, album.title)); Unit } }
    val openArtistGlobal = remember(nav) { { artist: String -> nav.push(Dest.Artist(artist)); Unit } }
    CompositionLocalProvider(
        LocalBottomBarInset provides bottomInset,
        LocalSideInsets provides sideInsets,
        LocalChromeBlurState provides blur,
        LocalCoverShare provides coverShare,
        LocalOpenAlbum provides openAlbumGlobal,
        LocalOpenArtist provides openArtistGlobal,
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                Box(Modifier.fillMaxSize().onSizeChanged { rootHeightPx = it.height.toFloat() }) {
                    // Its own scope: the colour read (it animates) must not recompose the whole shell.
                    SurfaceFill()
                    // Every tab keeps its own NavDisplay alive, laid side by side and slid as a row: switching tabs builds nothing (a page
                    // is built once, keeps its scroll and state), where one NavDisplay swapping back stacks rebuilt the whole page mid-slide.
                    Box(Modifier.fillMaxSize().nestedScroll(minimize.connection).chromeSource(blur)) {
                        Tab.entries.forEach { tab ->
                            // A tab is built on its first visit (the others shortly after launch, see above) and then stays.
                            if (tab in built) key(tab) {
                                // Only the tab on screen answers the back gesture; the others sit under a disabled dispatcher.
                                val dispatcher = rememberNavigationEventDispatcherOwner(enabled = nav.current == tab)
                                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcher) {
                                    NavDisplay(
                                        backStack = nav.stackOf(tab).map { TabDestination(tab, it) },
                                        modifier = Modifier.fillMaxSize().clipToBounds().graphicsLayer {
                                            translationX = (tab.ordinal - position.value) * size.width
                                        },
                                        onBack = { nav.pop(byGesture = SystemClock.uptimeMillis() - lastGestureAt[0] < 600) },
                                        entryDecorators = listOf(
                                            rememberSaveableStateHolderNavEntryDecorator(),
                                            rememberViewModelStoreNavEntryDecorator(),
                                        ),
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        entryProvider = tabEntries,
                                        transitionSpec = { if (targetState.isCoverPage()) coverPushTransition() else pushTransition() },
                                        popTransitionSpec = { backTransition(gesture = false) },
                                        predictivePopTransitionSpec = { edge -> backTransition(gesture = true, fromRight = edge == NavigationEvent.EDGE_RIGHT) },
                                    )
                                }
                            }
                        }
                    }
                    // At a tab's root, back returns to the tab it was reached from; only at the first one does it leave the app.
                    BackHandler(enabled = nav.canLeaveTab) { nav.backToPreviousTab() }
                    ChromeTheme(nav, schemes, dark, tier.value) {
                        AppBar(
                            current = nav.current,
                            onSelect = nav::select,
                            blur = blur,
                            minimize = { barMinimize.value },
                            onExpand = minimize::expand,
                            fold = { barFold.value },
                            onSearchAgain = minimize::expand,
                            sideSlide = { sheet.progress.value.coerceIn(0f, 1f) },
                            vertical = vertical,
                            slotLength = slotLength,
                            modifier = (if (vertical) {
                                Modifier
                                    .align(if (barSide == TabBarSide.Left) Alignment.BottomStart else Alignment.BottomEnd)
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                    .padding(start = FloatingChromeMargin, end = FloatingChromeMargin, bottom = sideBarBottom)
                            } else Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = navBottom + AppBarEdgeGap))
                                // The open player slides the bar off the bottom; a mini player thrown down pushes it the same way (capped), and
                                // once the pill is gone the push springs back on its own.
                                .graphicsLayer {
                                    if (vertical) {
                                        // The open player pushes the bar off its own side.
                                        val away = sheet.progress.value.coerceIn(0f, 1f) * (if (barSide == TabBarSide.Left) sideInsets.left else sideInsets.right).toPx()
                                        translationX = if (barSide == TabBarSide.Left) -away else away
                                        return@graphicsLayer
                                    }
                                    // Minimized, the bar's buttons leave to the sides instead (AppBar's sideSlide), so it does not also go down.
                                    val docked = maxOf(barMinimize.value, barFold.value)
                                    val slide = sheet.progress.value.coerceIn(0f, 1f) * (barTop + 16.dp).toPx() * (1f - docked)
                                    val cap = 0.1f * (rootHeightPx - (barTop + MiniPlayerGap + MiniPlayerHeight).toPx())
                                    val pushed = sheet.barPush
                                    translationY = maxOf(slide, if (pushed > 0f && cap > 0f) cap * kotlin.math.tanh(pushed / cap) else 0f)
                                },
                        )
                        PlayerSheet(
                            sheet, restingBottom = barTop + MiniPlayerGap, blur = blur,
                            onThrownDown = minimize::collapse,
                            dock = if (vertical) {
                                // Always on the bar's bottom row, beside the bar's column and as wide as what is left (so the next button stays).
                                MiniDock(
                                    { 1f }, drop = MiniPlayerGap + AppBarHeight,
                                    left = safeLeft + if (barBesideMini && barSide == TabBarSide.Left) AppBarHeight + BarGap else 0.dp,
                                    right = safeRight + if (barBesideMini && barSide == TabBarSide.Right) AppBarHeight + BarGap else 0.dp,
                                    compact = false,
                                    // The pill is as wide as it is when the device is upright: its shorter side, less the margins.
                                    maxWidth = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp.dp - FloatingChromeMargin * 2,
                                )
                            } else MiniDock({ maxOf(barMinimize.value, barFold.value) }, drop = MiniPlayerGap + AppBarHeight, left = AppBarHeight + BarGap, right = AppBarHeight + BarGap, fold = { barFold.value }),
                        )
                    }
                }
            }
        }
    }
}

/** The theme's surface colour behind everything. */
@Composable
private fun SurfaceFill() = Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))

/**
 * A page wears its own colours: the scheme of the source it claimed, blended from the wallpaper's when the claim arrives. Pages
 * slide past each other (tab switch, push, back) in their own colours, so no colour has to animate across both of them, which
 * recomposed every colour reader of both pages every frame; only the chrome (tab bar, mini player) blends, in [ChromeTheme].
 */
@Composable
private fun EntryTheme(key: TabDestination, schemes: SchemeFactory, dark: Boolean, tier: VisualTier, content: @Composable () -> Unit) {
    val registry = LocalColorSourceRegistry.current
    val target = schemes.scheme(registry?.sourceOf(key, key.initialColorSource()) ?: key.initialColorSource(), dark)
    // A page that is still coming in takes its colours at once: it is fading in anyway, and every blend step recomposes the whole page.
    val entering by produceState(true) { delay(EnterSnapMs); value = false }
    val scheme = animateColorScheme(target, snap = entering, lowTier = tier == VisualTier.LOW)
    HarmonyTheme(scheme, tier, LocalChromeBlurMode.current, LocalCoverBlurMode.current, content)
}

// In-tab pages: Material's shared X axis (a short slide and a fade), on springs that settle without bounce.
private val PageSlide = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)
private val PageFade = spring<Float>(dampingRatio = 1f, stiffness = 700f)

/** Album pages receive their cover from a tile, so they do not slide: the page fades in over the old one while the cover flies into place. */
private const val CoverPageMetadata = "harmony.coverPage"
private fun Scene<*>.isCoverPage() = entries.lastOrNull()?.metadata?.containsKey(CoverPageMetadata) == true

private val CoverPageFade = tween<Float>(240, easing = LinearOutSlowInEasing)

private fun coverPushTransition(): ContentTransform = ContentTransform(fadeIn(CoverPageFade, initialAlpha = 0f), ExitTransition.None)


private fun pushTransition(): ContentTransform =
    (slideInHorizontally(PageSlide) { it / 4 } + fadeIn(PageFade)) togetherWith (slideOutHorizontally(PageSlide) { -it / 6 } + fadeOut(PageFade))

/**
 * Going back is one motion whether it comes from the gesture, the system's back key or the in-app arrow: the page shrinks and slides
 * off to the right while the one below slides in from a short parallax offset. It ends fully off screen (a page that stops short
 * simply vanishes when the transition ends), and its corners round while it leaves ([BackEffects]).
 *
 * Under a finger the animation is a seek and the spec is the curve of the gesture's progress, so the curves are shaped for it: the
 * page shrinks and moves only a little over the drag ([BackDrag]), and letting go carries it the rest of the way out, along the same
 * curve ([BackFlyOut]). The arrow, with no finger, uses a decelerating curve.
 */
private fun backTransition(gesture: Boolean, fromRight: Boolean = false): ContentTransform {
    // A swipe from the right edge sends the page off to the left.
    val dir = if (fromRight) -1 else 1
    val ms = if (gesture) GestureMs else BackMs
    val move = if (gesture) BackFlyOut else BackDecelerate
    val shape = if (gesture) BackDrag else BackDecelerate
    val enter = slideInHorizontally(tween(ms, easing = move)) { -dir * it / 5 } + fadeIn(tween(ms, easing = shape), initialAlpha = 0.6f)
    val exit = slideOutHorizontally(tween(ms, easing = move)) { dir * it } + scaleOut(tween(ms, easing = shape), targetScale = BackScale)
    // The page going away stays on top of the one below it.
    return ContentTransform(enter, exit, targetContentZIndex = -1f)
}

/**
 * Under the finger the page only leans a little (8% of its travel over the first half of the gesture): it leaves the screen after the
 * finger lets go, which carries on from wherever it was along the rest of the curve: fast out, soft end.
 */
private val BackFlyOut = Easing { x -> if (x < 0.5f) 0.08f * (x / 0.5f) else 0.08f + 0.92f * (1f - (1f - (x - 0.5f) / 0.5f).let { it * it }) }

/** Reaches its end over the first 40% of the gesture and holds (the shrink and the rounding settle while the finger is still down). */
private val BackDrag = Easing { x -> (x / 0.4f).coerceAtMost(1f) }

/** The arrow's curve: a clear start and a soft settle (a steeper one finished before the eye caught it). */
private val BackDecelerate = FastOutSlowInEasing

/** One duration for every part of the back motion, so the pieces stay in step (under a finger the parts are scaled onto one progress). */
private const val BackMs = 480

/** A swipe's motion is quicker: the part after letting go is what is left of this, so it must not drag. */
private const val GestureMs = 300
private const val BackScale = 0.9f
private val BackCorner = 32.dp
private val BackBlur = 24.dp

/**
 * The back motion's effects on a page, driven by its own transition so the gesture's seek and the arrow's animation share them:
 * the page leaving backwards rounds its corners ([leavingBack]: the one on top during the gesture, or one already popped), and the
 * one coming back into view starts blurred and comes into focus as the other slides away ([backing]: a back is what brought it).
 */
@Composable
private fun BackEffects(leavingBack: Boolean, backing: Boolean, blurred: Boolean, ms: Int, content: @Composable () -> Unit) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val radius by transition.animateDp(
        transitionSpec = { tween(if (targetState == EnterExitState.PostExit) ms else 0, easing = BackDrag) },
        label = "back-corner",
    ) { state -> if (state == EnterExitState.PostExit && leavingBack) BackCorner else 0.dp }
    // Focus follows the whole motion (linear), so it is sharp as the other page finishes leaving.
    val blur by transition.animateDp(
        transitionSpec = { tween(if (initialState == EnterExitState.PreEnter) ms else 0, easing = LinearEasing) },
        label = "back-blur",
    ) { state -> if (state == EnterExitState.PreEnter && backing && blurred) BackBlur else 0.dp }
    var modifier: Modifier = Modifier
    if (radius > 0.dp) modifier = modifier.clip(RoundedCornerShape(radius))
    if (blur > 0.dp) modifier = modifier.blur(blur)
    Box(modifier) { content() }
}

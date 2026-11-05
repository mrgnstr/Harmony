package com.mrgnstr.harmony.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrgnstr.harmony.feature.nowplaying.MiniPlayerGap
import com.mrgnstr.harmony.feature.nowplaying.MiniPlayerHeight
import com.mrgnstr.harmony.feature.nowplaying.NowPlayingViewModel
import com.mrgnstr.harmony.feature.nowplaying.PlayerSheet
import com.mrgnstr.harmony.feature.nowplaying.rememberPlayerSheetState
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
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlinx.coroutines.delay
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.LocalSharedTransitionScope
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
import com.mrgnstr.harmony.feature.artist.ArtistReleasesRoute
import com.mrgnstr.harmony.feature.album.AlbumShelfRoute
import com.mrgnstr.harmony.feature.library.LibraryAlbumsRoute
import com.mrgnstr.harmony.feature.library.LibrarySongsRoute
import com.mrgnstr.harmony.feature.artist.ArtistRoute
import com.mrgnstr.harmony.feature.artist.ArtistSongsRoute
import com.mrgnstr.harmony.feature.artist.ArtistInfoRoute
import com.mrgnstr.harmony.feature.album.AlbumRoute
import com.mrgnstr.harmony.feature.gallery.GalleryRoute
import com.mrgnstr.harmony.feature.home.HomeRoute
import com.mrgnstr.harmony.feature.library.LibraryRoute
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
fun HarmonyApp(dark: Boolean, tier: VisualTier, chromeBlur: BlurMode, coverBlur: BlurMode, schemes: SchemeFactory, openPlayer: Int = 0) {
    val nav = rememberTabNavigator()
    val registry = rememberColorSourceRegistry()

    HarmonyTheme(schemes.scheme(ColorSource.Wallpaper, dark), tier, chromeBlur, coverBlur) {
        CompositionLocalProvider(LocalColorSourceRegistry provides registry) {
            val permission = rememberAudioPermissionState()
            if (permission.granted) Shell(nav, openPlayer, schemes, dark) else OnboardingScreen(permission)
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
private fun Shell(nav: TabNavigator, openPlayer: Int, schemes: SchemeFactory, dark: Boolean) {
    val app = hiltViewModel<AppViewModel>()
    NotificationPermissionEffect()
    val tier = androidx.compose.runtime.rememberUpdatedState(com.mrgnstr.harmony.core.designsystem.LocalVisualTier.current)
    val blur = rememberChromeBlurState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barTop = navBottom + AppBarEdgeGap + AppBarHeight
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
    // The notification was tapped: open Now Playing.
    androidx.compose.runtime.LaunchedEffect(openPlayer) { if (openPlayer > 0) sheet.expand() }
    var rootHeightPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // Pages pad for the tab bar and, with a song loaded, the mini player resting above it.
    val bottomInset = if (hasMedia) barTop + MiniPlayerGap + MiniPlayerHeight else barTop

    val entries = remember(nav) {
        val openSettings = { nav.push(Dest.Settings); Unit }
        val openGallery = { nav.push(Dest.Gallery); Unit }
        val back = { nav.pop(); Unit }
        entryProvider<Dest> {
            entry<Dest.Root> { root ->
                when (root.tab) {
                    Tab.Home -> HomeRoute(onOpenSettings = openSettings, onOpenGallery = openGallery)
                    Tab.Library -> LibraryRoute(
                        onOpenSettings = openSettings, onOpenGallery = openGallery,
                        onOpenAlbum = { nav.push(Dest.Album(it.id, it.title)) }, onOpenArtist = { nav.push(Dest.Artist(it.name)) },
                        onOpenPlaylist = { nav.push(Dest.Playlist(it)) }, onOpenGenre = { nav.push(Dest.Genre(it)) },
                    )
                    Tab.Search -> SearchRoute()
                }
            }
            entry<Dest.Settings> { SettingsRoute(onBack = back) }
            entry<Dest.Gallery> {
                GalleryRoute(onBack = back,
                    onOpenAlbums = { nav.push(Dest.LibraryAlbums(it)) },
                    onOpenSongs = { nav.push(Dest.LibrarySongs(it)) },
                    onOpenAlbum = { nav.push(Dest.Album(it.id, it.title)) })
            }
            entry<Dest.Album>(metadata = mapOf(CoverPageMetadata to true)) { album ->
                AlbumRoute(
                    album.albumId, album.title, onBack = back,
                    onOpenAlbum = { nav.push(Dest.Album(it.id, it.title)) }, onOpenArtist = { nav.push(Dest.Artist(it)) },
                    onOpenShelf = { shelf, accent -> nav.push(Dest.AlbumShelf(album.albumId, album.title, shelf, accent)) },
                )
            }
            entry<Dest.Artist> { artist ->
                ArtistRoute(artist.name, onBack = back,
                    onOpenAlbum = { nav.push(Dest.Album(it.id, it.title)) },
                    onAllSongs = { accent -> nav.push(Dest.ArtistSongs(artist.name, accent)) },
                    onInfo = { accent -> nav.push(Dest.ArtistInfo(artist.name, accent)) },
                    onOpenShelf = { shelf, accent -> nav.push(Dest.ArtistReleases(artist.name, shelf, accent)) })
            }
            entry<Dest.ArtistSongs> { ArtistSongsRoute(it.name, onBack = back) }
            entry<Dest.ArtistInfo> { ArtistInfoRoute(it.name, onBack = back) }
            entry<Dest.ArtistReleases> { collection ->
                ArtistReleasesRoute(collection.name, collection.shelf, onBack = back,
                    onOpenAlbum = { nav.push(Dest.Album(it.id, it.title)) })
            }
            entry<Dest.AlbumShelf> { collection ->
                AlbumShelfRoute(collection.albumId, collection.title, collection.shelf, onBack = back,
                    onOpenAlbum = { nav.push(Dest.Album(it.id, it.title)) })
            }
            entry<Dest.LibraryAlbums> { LibraryAlbumsRoute(onBack = back, onOpenAlbum = { nav.push(Dest.Album(it.id, it.title)) }) }
            entry<Dest.LibrarySongs> { LibrarySongsRoute(onBack = back) }
            // Genre and Playlist pages land with their milestones.
            entry<Dest.Genre> { SettingsRoute(onBack = back) }
            entry<Dest.Playlist> { SettingsRoute(onBack = back) }
        }
    }
    // Tab in the entry's identity and metadata (for the tab slide); the entry key is what pages claim colours under.
    val tabEntries = remember(entries) {
        { key: TabDestination ->
            val entry = entries(key.destination)
            NavEntry(key = key, contentKey = "${key.tab.name}:${entry.contentKey}", metadata = entry.metadata + (TabMetadata to key.tab)) {
                CompositionLocalProvider(LocalEntryKey provides key) { EntryTheme(key, schemes, dark, tier.value) { entry.Content() } }
            }
        }
    }

    val coverShare = remember { CoverShare() }
    CompositionLocalProvider(LocalBottomBarInset provides bottomInset, LocalChromeBlurState provides blur, LocalCoverShare provides coverShare) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                Box(Modifier.fillMaxSize().onSizeChanged { rootHeightPx = it.height.toFloat() }) {
                    // Its own scope: the colour read (it animates) must not recompose the whole shell.
                    SurfaceFill()
                    // Every tab keeps its own NavDisplay alive, laid side by side and slid as a row: switching tabs builds nothing (a page
                    // is built once, keeps its scroll and state), where one NavDisplay swapping back stacks rebuilt the whole page mid-slide.
                    Box(Modifier.fillMaxSize().chromeSource(blur)) {
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
                                        onBack = { nav.pop() },
                                        entryDecorators = listOf(
                                            rememberSaveableStateHolderNavEntryDecorator(),
                                            rememberViewModelStoreNavEntryDecorator(),
                                        ),
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        entryProvider = tabEntries,
                                        transitionSpec = { if (targetState.isCoverPage()) coverPushTransition() else pushTransition() },
                                        popTransitionSpec = { if (initialState.isCoverPage()) coverPopTransition() else popTransition() },
                                        predictivePopTransitionSpec = { predictivePopTransition() },
                                    )
                                }
                            }
                        }
                    }
                    ChromeTheme(nav, schemes, dark, tier.value) {
                        AppBar(
                            current = nav.current,
                            onSelect = nav::select,
                            blur = blur,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = navBottom + AppBarEdgeGap)
                                // The open player slides the bar off the bottom; a mini player thrown down pushes it the same way (capped), and
                                // once the pill is gone the push springs back on its own.
                                .graphicsLayer {
                                    val slide = sheet.progress.value.coerceIn(0f, 1f) * (barTop + 16.dp).toPx()
                                    val cap = 0.1f * (rootHeightPx - (barTop + MiniPlayerGap + MiniPlayerHeight).toPx())
                                    val pushed = sheet.barPush
                                    translationY = maxOf(slide, if (pushed > 0f && cap > 0f) cap * kotlin.math.tanh(pushed / cap) else 0f)
                                },
                        )
                        PlayerSheet(sheet, restingBottom = barTop + MiniPlayerGap, blur = blur)
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

// The page going away stays on top of the one below it (which is already there) while it fades.
private fun coverPopTransition(): ContentTransform = ContentTransform(EnterTransition.None, fadeOut(CoverPageFade), targetContentZIndex = -1f)

private fun pushTransition(): ContentTransform =
    (slideInHorizontally(PageSlide) { it / 4 } + fadeIn(PageFade)) togetherWith (slideOutHorizontally(PageSlide) { -it / 6 } + fadeOut(PageFade))

private fun popTransition(): ContentTransform =
    (slideInHorizontally(PageSlide) { -it / 6 } + fadeIn(PageFade)) togetherWith (slideOutHorizontally(PageSlide) { it / 4 } + fadeOut(PageFade))

/**
 * Back gesture: the page shrinks and slides away with the finger, staying opaque, while the one below slides in from its
 * parallax offset. The gesture drives the progress; no fades, since a linear cross-fade under a finger looks muddy.
 */
private fun predictivePopTransition(): ContentTransform =
    slideInHorizontally(PageSlide) { -it / 6 } togetherWith
        (slideOutHorizontally(PageSlide) { it / 3 } + scaleOut(PageFade, targetScale = 0.85f))

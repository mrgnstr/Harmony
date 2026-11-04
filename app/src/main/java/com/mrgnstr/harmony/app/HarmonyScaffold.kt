package com.mrgnstr.harmony.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.mrgnstr.harmony.core.designsystem.LocalBottomBarInset
import com.mrgnstr.harmony.core.designsystem.blur.LocalChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.chromeSource
import com.mrgnstr.harmony.core.designsystem.blur.rememberChromeBlurState
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
private const val TabMetadata = "harmony.tab"

/**
 * The app: resolves the colour source of the page on top and themes everything with it (blending towards the page below
 * while a back gesture is in progress), then lays out the pages with the floating bar over them.
 */
@Composable
fun HarmonyApp(dark: Boolean, tier: VisualTier, chromeBlur: BlurMode, coverBlur: BlurMode, schemes: SchemeFactory) {
    val nav = rememberTabNavigator()
    val registry = rememberColorSourceRegistry()
    val back by rememberBackProgress()
    val stack = nav.backStack
    val top = TabDestination(nav.current, stack.last())
    val below = stack.getOrNull(stack.size - 2)?.let { TabDestination(nav.current, it) }
    val topScheme = schemes.scheme(registry.sourceOf(top), dark)
    val gesture = back != null && below != null
    val target = if (gesture) lerp(topScheme, schemes.scheme(registry.sourceOf(below), dark), back!!) else topScheme
    val scheme = animateColorScheme(target, snap = gesture, lowTier = tier == VisualTier.LOW)

    HarmonyTheme(scheme, tier, chromeBlur, coverBlur) {
        CompositionLocalProvider(LocalColorSourceRegistry provides registry) {
            val permission = rememberAudioPermissionState()
            if (permission.granted) Shell(nav) else OnboardingScreen(permission)
        }
    }
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
private fun Shell(nav: TabNavigator) {
    val app = hiltViewModel<AppViewModel>()
    NotificationPermissionEffect()
    val tier = androidx.compose.runtime.rememberUpdatedState(com.mrgnstr.harmony.core.designsystem.LocalVisualTier.current)
    val blur = rememberChromeBlurState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barTop = navBottom + AppBarEdgeGap + AppBarHeight
    val player = hiltViewModel<NowPlayingViewModel>()
    val hasMedia by player.snapshot.collectAsStateWithLifecycle().let { st -> androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { st.value.hasMedia } } }
    val sheet = rememberPlayerSheetState()
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
                    Tab.Library -> LibraryRoute(onOpenSettings = openSettings, onOpenGallery = openGallery)
                    Tab.Search -> SearchRoute()
                }
            }
            entry<Dest.Settings> { SettingsRoute(onBack = back) }
            entry<Dest.Gallery> { GalleryRoute(onBack = back) }
            // Album, Artist, Genre and Playlist pages land with their milestones (M5–M7).
            entry<Dest.Album> { SettingsRoute(onBack = back) }
            entry<Dest.Artist> { SettingsRoute(onBack = back) }
            entry<Dest.Genre> { SettingsRoute(onBack = back) }
            entry<Dest.Playlist> { SettingsRoute(onBack = back) }
        }
    }
    // Tab in the entry's identity and metadata (for the tab slide); the entry key is what pages claim colours under.
    val tabEntries = remember(entries) {
        { key: TabDestination ->
            val entry = entries(key.destination)
            NavEntry(key = key, contentKey = "${key.tab.name}:${entry.contentKey}", metadata = entry.metadata + (TabMetadata to key.tab)) {
                CompositionLocalProvider(LocalEntryKey provides key) { entry.Content() }
            }
        }
    }

    CompositionLocalProvider(LocalBottomBarInset provides bottomInset, LocalChromeBlurState provides blur) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    Box(Modifier.fillMaxSize().chromeSource(blur)) {
                        NavDisplay(
                            backStack = nav.backStack.map { TabDestination(nav.current, it) },
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                            onBack = { nav.pop() },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            sharedTransitionScope = this@SharedTransitionLayout,
                            entryProvider = tabEntries,
                            transitionSpec = { tabSlide(tier.value) ?: pushTransition() },
                            popTransitionSpec = { tabSlide(tier.value) ?: popTransition() },
                            predictivePopTransitionSpec = { predictivePopTransition() },
                        )
                    }
                    AppBar(
                        current = nav.current,
                        onSelect = nav::select,
                        blur = blur,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = navBottom + AppBarEdgeGap)
                            // The open player slides the bar off the bottom.
                            .graphicsLayer { translationY = sheet.progress.value.coerceIn(0f, 1f) * (barTop + 16.dp).toPx() },
                    )
                    PlayerSheet(sheet, restingBottom = barTop + MiniPlayerGap, blur = blur)
                }
            }
        }
    }
}

/** For pages that share elements across navigation (cover → album header, …). */
val LocalSharedTransitionScope = staticCompositionLocalOf<androidx.compose.animation.SharedTransitionScope?> { null }

private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.tabOf(scene: Scene<T>): Tab? =
    scene.entries.lastOrNull()?.metadata?.get(TabMetadata) as? Tab

/** Switching tabs slides both pages horizontally in tab order (Home → Library → Search). */
private fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.tabSlide(tier: VisualTier): ContentTransform? {
    val from = tabOf(initialState) ?: return null
    val to = tabOf(targetState) ?: return null
    if (from == to) return null
    val direction = if (to.ordinal > from.ordinal) 1 else -1
    val spec = if (tier == VisualTier.LOW) tween<IntOffset>(180, easing = FastOutSlowInEasing)
    else spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
    return slideInHorizontally(spec) { it * direction } togetherWith slideOutHorizontally(spec) { -it * direction }
}

// In-tab pages: Material's shared X axis (a short slide and a fade), on springs that settle without bounce.
private val PageSlide = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)
private val PageFade = spring<Float>(dampingRatio = 1f, stiffness = 700f)

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

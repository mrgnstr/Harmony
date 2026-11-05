package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.mrgnstr.harmony.core.designsystem.LocalTopBarCollapse
import com.mrgnstr.harmony.core.designsystem.LocalTopBarInset
import com.mrgnstr.harmony.core.designsystem.blur.ChromeBlurState
import com.mrgnstr.harmony.core.designsystem.blur.ChromeSurface
import com.mrgnstr.harmony.core.designsystem.blur.chromeSource
import com.mrgnstr.harmony.core.designsystem.blur.rememberChromeBlurState
import com.mrgnstr.harmony.core.designsystem.icons.SymbolIcon
import com.mrgnstr.harmony.core.designsystem.icons.Symbols
import kotlin.math.roundToInt

private val BarRow = 64.dp
// Compact rows match the 64 dp Artist/Album detail bar.
private val TitleExtra = 44.dp
private val SubtitleExtra = 20.dp
private val TitleBottomGap = 4.dp
private val TitleStart = 16.dp
private val TitleStartWithBack = 56.dp
private val SubtitleGap = 4.dp

/**
 * The top bar every page uses: a large title that rises into the bar's row and scales down to the small title as the content
 * scrolls, with optional [actions], back button, subtitle and a [tabs] row (a tab row with a transparent container). The title row
 * and the tabs are one blurred surface (the chrome blur, same Auto/On/Off setting) so they are always the same colour, and
 * [content] runs under it: it gets the bar's expanded height as [LocalTopBarInset], to pad its first item with.
 *
 * Content is laid out as tall as the bar's collapse and slid up with it, so a list keeps one fixed top padding and is not
 * re-measured while the bar collapses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    tabs: (@Composable () -> Unit)? = null,
    floatingBarHeight: Dp = 0.dp,
    floatingBar: (@Composable (ChromeBlurState) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val rowPx = with(density) { BarRow.toPx() }
    var titleHeight by remember { mutableFloatStateOf(0f) }
    var subtitleHeight by remember { mutableFloatStateOf(0f) }
    // Reserve measured text heights as well as the normal minimums, so larger system fonts keep their spacing.
    val titleExtraPx = maxOf(with(density) { TitleExtra.toPx() }, titleHeight + with(density) { TitleBottomGap.toPx() })
    val subtitleRoom = if (subtitle != null) maxOf(with(density) { SubtitleExtra.toPx() }, subtitleHeight + with(density) { SubtitleGap.toPx() }) else 0f
    val extraPx = titleExtraPx + subtitleRoom
    val tabsPx = if (tabs != null) with(density) { TabsHeight.toPx() } else 0f
    val floatingPx = if (floatingBar != null) with(density) { floatingBarHeight.toPx() } else 0f
    // Collapsing is the scroll behaviour's offset (0 expanded .. -distance collapsed); the layers below only read it when drawing or placing.
    val distance = extraPx
    LaunchedEffect(distance) { scroll.state.heightOffsetLimit = -distance }
    val collapsed: () -> Float = { (-scroll.state.heightOffset / distance).coerceIn(0f, 1f) }
    val pageBlur = rememberChromeBlurState()
    val largeStyle = MaterialTheme.typography.headlineLarge
    val titleScale = MaterialTheme.typography.titleLarge.fontSize.value / largeStyle.fontSize.value
    val expandedInset: Dp = with(density) { (statusTop + rowPx + extraPx + tabsPx + floatingPx).toDp() }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).nestedScroll(scroll.nestedScrollConnection)) {
        // Scaffold used to set this; without it every Text on the page falls back to the light theme's black.
        CompositionLocalProvider(
            LocalTopBarInset provides expandedInset,
            LocalTopBarCollapse provides with(density) { distance.toDp() },
            LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layout { measurable, c ->
                        val placeable = measurable.measure(Constraints.fixed(c.maxWidth, c.maxHeight + distance.roundToInt()))
                        layout(c.maxWidth, c.maxHeight) { placeable.place(0, scroll.state.heightOffset.roundToInt()) }
                    }
                    .chromeSource(pageBlur),
            ) { content() }
        }

        ChromeSurface(
            pageBlur, RectangleShape,
            Modifier.fillMaxWidth().layout { measurable, c ->
                val h = (statusTop + rowPx + extraPx + tabsPx + scroll.state.heightOffset.coerceIn(-distance, 0f)).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(c.maxWidth, h))
                layout(c.maxWidth, h) { placeable.place(0, 0) }
            },
            container = MaterialTheme.colorScheme.surface,
        ) {
            // The surface does not set a content colour; without this the text and icons stay the light theme's black.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Box(Modifier.fillMaxSize()) {
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            Modifier
                                .padding(horizontal = TitleStart)
                                .onSizeChanged { subtitleHeight = it.height.toFloat() }
                                .graphicsLayer {
                                    val f = collapsed()
                                    val scale = lerp(1f, titleScale, f)
                                    val expandedTop = statusTop + rowPx + extraPx - subtitleRoom - titleHeight - TitleBottomGap.toPx()
                                    val collapsedTop = statusTop + (rowPx - titleHeight * titleScale) / 2f
                                    translationY = lerp(expandedTop, collapsedTop, f) + titleHeight * scale + SubtitleGap.toPx()
                                    // Keep it below the moving title, then finish fading before that title reaches the row.
                                    alpha = (1f - f * 2f).coerceIn(0f, 1f)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // One title: it rises from the large position to the small row and scales down to the small style on the way.
                    Text(
                        title,
                        Modifier
                            .padding(horizontal = TitleStart)
                            .onSizeChanged { titleHeight = it.height.toFloat() }
                            .graphicsLayer {
                                val f = collapsed()
                                val s = lerp(1f, titleScale, f)
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = s; scaleY = s
                                val expandedTop = statusTop + rowPx + extraPx - subtitleRoom - titleHeight - TitleBottomGap.toPx()
                                val collapsedTop = statusTop + (rowPx - titleHeight * titleScale) / 2f
                                val compactStart = if (onBack != null) TitleStartWithBack else TitleStart
                                translationX = (compactStart - TitleStart).toPx() * f
                                translationY = lerp(expandedTop, collapsedTop, f)
                            },
                        style = largeStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            Modifier.padding(top = with(density) { statusTop.toDp() }, start = 4.dp).height(BarRow),
                        ) {
                            SymbolIcon(Symbols.ArrowBack, "Back")
                        }
                    }
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(top = with(density) { statusTop.toDp() }, end = 4.dp).height(BarRow),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                    if (tabs != null) Box(Modifier.align(Alignment.BottomStart)) { tabs() }
                }
            }
        }

        if (floatingBar != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Box(
                    Modifier.fillMaxWidth().layout { measurable, c ->
                        val barH = (statusTop + rowPx + extraPx + tabsPx + scroll.state.heightOffset.coerceIn(-distance, 0f)).roundToInt()
                        val h = floatingPx.roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(c.maxWidth, h))
                        layout(c.maxWidth, barH + h) { placeable.place(0, barH) }
                    },
                ) {
                    floatingBar(pageBlur)
                }
            }
        }
    }
}

/** The height of a text-only tab row, which [TopBarScaffold] reserves under the title when it has tabs. */
val TabsHeight = 48.dp

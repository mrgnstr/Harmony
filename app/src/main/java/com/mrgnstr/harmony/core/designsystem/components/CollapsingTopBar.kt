package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
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
/** The collapsed top bar's row under the status bar. */
val TopBarRowHeight: Dp get() = BarRow
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
@Composable
fun TopBarScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    tabs: (@Composable () -> Unit)? = null,
    /**
     * The [tabs] row's natural width (all labels, edge padding included). When it fits between the small title and the [actions] it is
     * placed there, centred, in the title's own row, and the row under the title is not reserved (wide screens).
     */
    tabsWidth: Dp? = null,
    /** The blank space at each end of the [tabs] row (edge padding and a tab's own padding): it may sit under the title or the buttons. */
    tabsInset: Dp = 0.dp,
    floatingBarHeight: Dp = 0.dp,
    floatingBar: (@Composable (ChromeBlurState) -> Unit)? = null,
    content: @Composable () -> Unit,
) = BoxWithConstraints(modifier) {
    TopBarScaffoldImpl(title, subtitle, onBack, actions, tabs, tabsWidth, tabsInset, constraints.maxWidth.toFloat(), floatingBarHeight, floatingBar, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarScaffoldImpl(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    tabs: (@Composable () -> Unit)?,
    tabsWidth: Dp?,
    tabsInset: Dp,
    widthPx: Float,
    floatingBarHeight: Dp,
    floatingBar: (@Composable (ChromeBlurState) -> Unit)?,
    content: @Composable () -> Unit,
) {
    val modifier = Modifier
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
    // Do the tabs fit in the title's row, between the small title and the buttons? (Measured text; the buttons' width is read back once laid out.)
    val textMeasurer = rememberTextMeasurer()
    val smallTitlePx = textMeasurer.measure(title, MaterialTheme.typography.titleLarge, maxLines = 1).size.width
    var actionsPx by remember { mutableFloatStateOf(with(density) { 148.dp.toPx() }) }
    val leftEdge = with(density) { (if (onBack != null) TitleStartWithBack else TitleStart).toPx() + smallTitlePx + 12.dp.toPx() }
    val rightEdge = widthPx - actionsPx - with(density) { 4.dp.toPx() }
    val tabsWidthPx = tabsWidth?.let { with(density) { it.toPx() } } ?: 0f
    // Only the labels have to fit between the title and the buttons: the row's blank ends may overlap them.
    val insetPx = with(density) { tabsInset.toPx() }
    val inlineTabs = tabs != null && tabsWidth != null && tabsWidthPx - 2 * insetPx <= rightEdge - leftEdge
    // Inline, the tabs ride on the title's line (below), so the bar keeps a little room under it for the tabs' underline and the usual gap.
    val tabsPx = when {
        tabs == null -> 0f
        inlineTabs -> with(density) { InlineTabsBottomPad.toPx() }
        else -> with(density) { TabsHeight.toPx() }
    }
    val largeTitlePx = textMeasurer.measure(title, MaterialTheme.typography.headlineLarge, maxLines = 1).size.width
    val floatingPx = if (floatingBar != null) with(density) { floatingBarHeight.toPx() } else 0f
    // Collapsing is the scroll behaviour's offset (0 expanded .. -distance collapsed); the layers below only read it when drawing or placing.
    val distance = extraPx
    LaunchedEffect(distance) { scroll.state.heightOffsetLimit = -distance }
    val collapsed: () -> Float = { (-scroll.state.heightOffset / distance).coerceIn(0f, 1f) }
    // The room under the tabs row stays; the room kept under the title for inline tabs goes as the bar collapses (a collapsed bar is as tall as any other).
    val tabsRoom: () -> Float = { if (inlineTabs) tabsPx * (1f - collapsed()) else tabsPx }
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
                val h = (statusTop + rowPx + extraPx + tabsRoom() + scroll.state.heightOffset.coerceIn(-distance, 0f)).roundToInt()
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
                        Modifier.align(Alignment.TopEnd).padding(top = with(density) { statusTop.toDp() }, end = 4.dp).height(BarRow)
                            .onSizeChanged { actionsPx = it.width.toFloat() },
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                    if (tabs != null) {
                        if (inlineTabs) {
                            // They travel with the title: on its line while it is large, up into the title row as it shrinks (between it and the buttons).
                            Box(
                                Modifier
                                    .offset {
                                        val f = collapsed()
                                        val compactStart = (if (onBack != null) TitleStartWithBack else TitleStart).toPx()
                                        // The title's right edge: its start plus its width at the current scale.
                                        val left = lerp(TitleStart.toPx(), compactStart, f) + largeTitlePx * lerp(1f, titleScale, f) + 12.dp.toPx()
                                        val right = lerp(widthPx - 16.dp.toPx(), rightEdge, f)
                                        val expandedCentre = statusTop + rowPx + extraPx - subtitleRoom - titleHeight - TitleBottomGap.toPx() + titleHeight / 2f
                                        val centre = lerp(expandedCentre, statusTop + rowPx / 2f, f)
                                        IntOffset(
                                            // Centred on the screen; only pushed aside as far as the title (or the buttons) force it.
                                            ((widthPx - tabsWidthPx) / 2f).coerceIn(left - insetPx, maxOf(left - insetPx, right + insetPx - tabsWidthPx)).roundToInt(),
                                            (centre - TabsHeight.toPx() / 2f).roundToInt(),
                                        )
                                    }
                                    .size(tabsWidth!!, TabsHeight),
                                contentAlignment = Alignment.Center,
                            ) { tabs() }
                        } else {
                            Box(Modifier.align(Alignment.BottomStart)) { tabs() }
                        }
                    }
                }
            }
        }

        if (floatingBar != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Box(
                    Modifier.fillMaxWidth().layout { measurable, c ->
                        val barH = (statusTop + rowPx + extraPx + tabsRoom() + scroll.state.heightOffset.coerceIn(-distance, 0f)).roundToInt()
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

/** Room kept under the title when the tabs sit on its line instead of in a row of their own. */
private val InlineTabsBottomPad = 24.dp

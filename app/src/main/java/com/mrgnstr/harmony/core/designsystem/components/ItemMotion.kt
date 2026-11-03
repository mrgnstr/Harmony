package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.mrgnstr.harmony.core.designsystem.LocalVisualTier
import com.mrgnstr.harmony.core.device.VisualTier

/** The springs list rows use to fade in/out and glide to a new place (library filters, the queue). */
val ItemFadeSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
val ItemPlaceSpec = spring<IntOffset>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

/** Rows fade in/out and glide to their new place (spring) when a filter or sort changes the list. Off on LOW tier. */
@Composable
fun LazyItemScope.itemMotion(): Modifier =
    if (LocalVisualTier.current == VisualTier.LOW) Modifier
    else Modifier.animateItem(fadeInSpec = ItemFadeSpec, placementSpec = ItemPlaceSpec, fadeOutSpec = ItemFadeSpec)

@Composable
fun LazyGridItemScope.itemMotion(): Modifier =
    if (LocalVisualTier.current == VisualTier.LOW) Modifier
    else Modifier.animateItem(fadeInSpec = ItemFadeSpec, placementSpec = ItemPlaceSpec, fadeOutSpec = ItemFadeSpec)

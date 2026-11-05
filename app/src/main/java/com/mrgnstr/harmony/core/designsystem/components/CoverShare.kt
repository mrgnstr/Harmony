package com.mrgnstr.harmony.core.designsystem.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.mrgnstr.harmony.core.designsystem.LocalSharedTransitionScope

/**
 * Which tile the album cover travels from. The same album can be on screen in several places (two Library tabs, a shelf), and a
 * shared-element key may only be on screen once, so a tile joins the transition only after it was the one tapped: [claim] it on click.
 */
@Stable
class CoverShare {
    private var key by mutableStateOf<String?>(null)
    private var owner by mutableStateOf<Any?>(null)

    fun claim(key: String, owner: Any) { this.key = key; this.owner = owner }

    fun isClaimedBy(key: String, owner: Any) = this.key == key && this.owner === owner
}

val LocalCoverShare = staticCompositionLocalOf<CoverShare?> { null }

fun albumCoverKey(albumId: Long, title: String) = "cover:$albumId:$title"

/**
 * Makes this the shared cover of [key] (the album page's header cover always is; a tile only once it [CoverShare.isClaimedBy] it,
 * pass [owner] for tiles). Outside a navigation entry or the shared-transition root it does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(key: String, owner: Any? = null): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    if (owner != null && LocalCoverShare.current?.isClaimedBy(key, owner) != true) return this
    val visibility = LocalNavAnimatedContentScope.current
    return with(scope) {
        this@sharedCover.sharedElement(rememberSharedContentState(key), visibility, clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp)))
    }
}

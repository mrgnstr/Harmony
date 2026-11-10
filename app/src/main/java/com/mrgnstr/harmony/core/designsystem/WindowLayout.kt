package com.mrgnstr.harmony.core.designsystem

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.mrgnstr.harmony.core.model.TabBarSide

/**
 * What kind of screen the app is on, for any part of the app that lays itself out differently on tablets and in landscape.
 * Read it from [LocalWindowLayout]; the tab bar is the first user (it stands on a side when [sideChrome]).
 *
 * @property isTablet the device's shortest side is at least [TabletMinDp] (a tablet, or a foldable unfolded), in any orientation.
 * @property isLandscape the window is wider than it is tall (split screen counts the window, not the display).
 * @property deviceBottomSide in landscape, the side of the window where the device's own bottom edge is (it flips between the two
 *   landscape rotations); null while upright.
 */
@Immutable
data class WindowLayout(val isTablet: Boolean, val isLandscape: Boolean, val deviceBottomSide: TabBarSide?) {
    /** Navigation and the mini player stand beside the content instead of under it: every tablet, and phones on their side. */
    val sideChrome: Boolean get() = isTablet || isLandscape

    /**
     * The side the tab bar is on when [sideChrome]. Tablets follow [preference]; phones are locked to their own bottom edge, whichever
     * way up the landscape is (right when the rotation is unknown, e.g. a landscape-shaped window on an upright display).
     */
    fun barSide(preference: TabBarSide): TabBarSide = if (isTablet) preference else deviceBottomSide ?: TabBarSide.Right

    companion object {
        const val TabletMinDp = 600

        val Phone = WindowLayout(isTablet = false, isLandscape = false, deviceBottomSide = null)

        /** Where the device's bottom edge is for a display [rotation] (`Surface.ROTATION_*`), or null when upright. */
        fun bottomSideOf(rotation: Int): TabBarSide? = when (rotation) {
            Surface.ROTATION_90 -> TabBarSide.Right
            Surface.ROTATION_270 -> TabBarSide.Left
            else -> null
        }

        fun of(smallestWidthDp: Int, widthDp: Int, heightDp: Int, rotation: Int) = WindowLayout(
            isTablet = smallestWidthDp >= TabletMinDp,
            isLandscape = widthDp > heightDp,
            deviceBottomSide = bottomSideOf(rotation),
        )
    }
}

val LocalWindowLayout = compositionLocalOf { WindowLayout.Phone }

/** The current [WindowLayout]; follows rotation (including the flip between the two landscapes), folding and split screen. */
@Composable
fun rememberWindowLayout(): WindowLayout {
    val config = LocalConfiguration.current
    val rotation = rememberDisplayRotation()
    return remember(config.smallestScreenWidthDp, config.screenWidthDp, config.screenHeightDp, rotation) {
        WindowLayout.of(config.smallestScreenWidthDp, config.screenWidthDp, config.screenHeightDp, rotation)
    }
}

/**
 * The display's rotation, live. A turn from one landscape to the other changes no Configuration (the orientation is the same), so
 * this listens to the display itself.
 */
@Composable
private fun rememberDisplayRotation(): Int {
    val context = LocalContext.current
    val view = LocalView.current
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(context, view) {
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) { view.display?.let { rotation = it.rotation } }
        }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        view.display?.let { rotation = it.rotation }
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return rotation
}

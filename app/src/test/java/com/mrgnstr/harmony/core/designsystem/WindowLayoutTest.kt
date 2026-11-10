package com.mrgnstr.harmony.core.designsystem

import android.view.Surface
import com.mrgnstr.harmony.core.model.TabBarSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {
    private fun phone(widthDp: Int, heightDp: Int, rotation: Int) = WindowLayout.of(smallestWidthDp = 393, widthDp, heightDp, rotation)
    private fun tablet(widthDp: Int, heightDp: Int, rotation: Int) = WindowLayout.of(smallestWidthDp = 800, widthDp, heightDp, rotation)

    @Test fun upright_phone_keeps_the_bottom_bar() {
        val layout = phone(393, 851, Surface.ROTATION_0)
        assertFalse(layout.sideChrome)
        assertFalse(layout.isTablet)
    }

    @Test fun upside_down_phone_keeps_the_bottom_bar() {
        assertFalse(phone(393, 851, Surface.ROTATION_180).sideChrome)
    }

    @Test fun landscape_phone_is_locked_to_its_own_bottom_edge_whatever_the_setting() {
        for (preference in TabBarSide.entries) {
            val turnedLeft = phone(851, 393, Surface.ROTATION_90)
            val turnedRight = phone(851, 393, Surface.ROTATION_270)
            assertTrue(turnedLeft.sideChrome && turnedRight.sideChrome)
            // Rotated 90 (device top to the left): its bottom edge is on the right; 270: on the left.
            assertEquals(TabBarSide.Right, turnedLeft.barSide(preference))
            assertEquals(TabBarSide.Left, turnedRight.barSide(preference))
        }
    }

    @Test fun phone_in_a_landscape_window_on_an_upright_display_falls_back_to_right() {
        // Split screen: the window is wide, the display is not turned. Still never the setting.
        val layout = phone(500, 300, Surface.ROTATION_0)
        assertTrue(layout.sideChrome)
        assertNull(layout.deviceBottomSide)
        assertEquals(TabBarSide.Right, layout.barSide(TabBarSide.Left))
    }

    @Test fun tablets_use_a_side_bar_in_every_orientation_and_follow_the_setting() {
        listOf(
            tablet(800, 1280, Surface.ROTATION_0),
            tablet(1280, 800, Surface.ROTATION_90),
            tablet(1280, 800, Surface.ROTATION_270),
            tablet(800, 1280, Surface.ROTATION_180),
        ).forEach { layout ->
            assertTrue(layout.isTablet && layout.sideChrome)
            assertEquals(TabBarSide.Left, layout.barSide(TabBarSide.Left))
            assertEquals(TabBarSide.Right, layout.barSide(TabBarSide.Right))
        }
    }

    @Test fun a_foldable_is_a_phone_folded_and_a_tablet_unfolded() {
        assertFalse(WindowLayout.of(smallestWidthDp = 411, widthDp = 411, heightDp = 900, rotation = Surface.ROTATION_0).sideChrome)
        assertTrue(WindowLayout.of(smallestWidthDp = 700, widthDp = 840, heightDp = 700, rotation = Surface.ROTATION_0).isTablet)
    }

    @Test fun tablet_threshold_is_600dp() {
        assertFalse(WindowLayout.of(599, 599, 900, Surface.ROTATION_0).isTablet)
        assertTrue(WindowLayout.of(600, 600, 900, Surface.ROTATION_0).isTablet)
    }
}

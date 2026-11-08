package com.mrgnstr.harmony.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabHistoryTest {
    @Test
    fun backAtARootReturnsThroughTheTabsVisited() {
        val nav = TabNavigator()
        assertFalse(nav.canLeaveTab)
        nav.select(Tab.Library)
        nav.select(Tab.Search)
        assertTrue(nav.canLeaveTab)
        assertTrue(nav.backToPreviousTab()); assertEquals(Tab.Library, nav.current)
        assertTrue(nav.backToPreviousTab()); assertEquals(Tab.Home, nav.current)
        assertFalse(nav.canLeaveTab)
        assertFalse(nav.backToPreviousTab())
    }

    @Test
    fun aTabAppearsOnceInTheHistoryAndAPageInItBlocksLeaving() {
        val nav = TabNavigator()
        nav.select(Tab.Library); nav.select(Tab.Home); nav.select(Tab.Library)
        nav.push(Dest.Genre("Rock"))
        // Back first pops the page, so the tab is not left while it has one.
        assertFalse(nav.canLeaveTab)
        nav.pop()
        assertTrue(nav.backToPreviousTab()); assertEquals(Tab.Home, nav.current)
        assertFalse(nav.canLeaveTab)
    }

    @Test
    fun historySurvivesSaveAndRestore() {
        val nav = TabNavigator()
        nav.select(Tab.Library)
        val saved = nav.save()
        @Suppress("UNCHECKED_CAST")
        val restored = TabNavigator.Saver.restore(saved)!!
        assertEquals(Tab.Library, restored.current)
        assertTrue(restored.backToPreviousTab()); assertEquals(Tab.Home, restored.current)
    }
}

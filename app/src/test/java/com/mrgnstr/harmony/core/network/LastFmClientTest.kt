package com.mrgnstr.harmony.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class LastFmClientTest {
    @Test
    fun cleanTextDropsTheReadMoreLinkAndKeepsParagraphs() {
        val raw = "First paragraph.<br />\n<br />\nSecond &amp; last.  <a href=\"https://www.last.fm/music/X\">Read more on Last.fm</a>. " +
            "User-contributed text is available under the Creative Commons By-SA License; additional terms may apply."
        assertEquals(
            "First paragraph.\n\nSecond & last.  . User-contributed text is available under the Creative Commons By-SA License; additional terms may apply.",
            LastFmClient.cleanText(raw),
        )
    }
}

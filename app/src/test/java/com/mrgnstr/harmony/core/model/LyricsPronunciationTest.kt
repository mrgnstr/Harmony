package com.mrgnstr.harmony.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPronunciationTest {
    private fun lyrics(vararg lines: LyricLine) = Lyrics(lines.toList(), synced = true, hasPronunciations = true)

    @Test fun latinOnlyLinesLoseTheirPronunciation() {
        val l = lyrics(
            LyricLine(0, "Dance for me, one and two", pronunciation = "Dance for me, one and two"),
            LyricLine(1000, "Café déjà vu", pronunciation = "kafe"),
        ).withoutRedundantPronunciations()
        assertNull(l.lines[0].pronunciation)
        assertNull(l.lines[1].pronunciation)
        assertFalse(l.hasPronunciations)
    }

    @Test fun otherScriptsKeepIt() {
        val l = lyrics(
            LyricLine(0, "Hello", pronunciation = "Hello"),
            LyricLine(1000, "誰かの言葉で", pronunciation = "dare ka no kotoba de"),
        ).withoutRedundantPronunciations()
        assertNull(l.lines[0].pronunciation)
        assertEquals("dare ka no kotoba de", l.lines[1].pronunciation)
        assertTrue(l.hasPronunciations)
    }
}

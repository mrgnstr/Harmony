package com.mrgnstr.harmony.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElrcParserTest {
    @Test fun wordCuesKeepSpacesAndEnds() {
        val l = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>big <00:02.00>world<00:02.60>\n[00:04.00]<00:04.00>Next <00:04.50>one")!!
        assertTrue(l.hasWordCues)
        val first = l.lines[0]
        assertEquals("Hello big world", first.text)
        assertEquals(listOf("Hello ", "big ", "world"), first.cues.map { it.text })
        assertEquals("Hello big world", first.cues.joinToString("") { it.text })
        assertEquals(listOf(1_000L, 1_500L, 2_000L), first.cues.map { it.startMs })
        assertEquals(listOf<Long?>(1_500L, 2_000L, 2_600L), first.cues.map { it.endMs })
        assertEquals(2_600L, first.endMs)
        // No trailing tag: the line has no end and its last word ends at the next line's start.
        val second = l.lines[1]
        assertNull(second.endMs)
        assertEquals(listOf<Long?>(4_500L, 5_000L).first(), second.cues[0].endMs)
        assertNull(second.cues[1].endMs)
    }

    @Test fun plainLinesHaveNoCues() {
        val l = LrcParser.parse("[00:01.00]a\n[00:02.00]b")!!
        assertFalse(l.hasWordCues)
        assertTrue(l.lines.all { it.cues.isEmpty() })
    }

    @Test fun shortTimesAndOffset() {
        val l = LrcParser.parse("[offset:+100]\n[00:01.5]<00:01>a <00:02.250>b")!!
        assertEquals(1_400L, l.lines[0].startMs)
        assertEquals(listOf(900L, 2_150L), l.lines[0].cues.map { it.startMs })
    }

    @Test fun duetWithBackground() {
        val src = """
            [ti:Song]
            [00:10.000]v1: <00:10.000>Hold <00:10.500>on <00:11.000>tight
            [00:10.800]bg: (<00:10.800>ooh <00:11.300>yeah<00:11.900>)
            [00:13.000]v2: <00:13.000>I <00:13.200>will
            [bg: <00:13.500>never <00:13.900>let go<00:14.400>]
            [00:15.000]
            [00:16.000]<00:16.000>Together
        """.trimIndent()
        val l = LrcParser.parse(src)!!
        assertTrue(l.hasWordCues)
        assertEquals(listOf("v1", "v2"), l.agents.map { it.id })
        assertEquals(listOf("main", "voice"), l.agents.map { it.role })
        assertEquals(4, l.lines.size) // two sung, a break, one sung; backgrounds are not lines
        val a = l.lines[0]
        assertFalse(a.duet)
        assertEquals("v1", a.agentId)
        val abg = a.background!!
        assertEquals("ooh yeah", abg.text)
        assertEquals(listOf("ooh ", "yeah"), abg.cues.map { it.text })
        assertEquals(10_800L, abg.startMs)
        assertEquals(11_900L, abg.endMs)
        val b = l.lines[1]
        assertTrue(b.duet)
        assertEquals("v2", b.agentId)
        assertEquals("never let go", b.background!!.text)
        assertTrue(b.background!!.duet)
        assertTrue(l.lines[2].isBreak)
        assertEquals("v1", l.lines[3].agentId) // unprefixed lines are the main voice once voices appear
    }

    @Test fun legacyVoiceLettersAndOrphanBackground() {
        val l = LrcParser.parse("[00:01.00]bg: (echo)\n[00:02.00]M: one\n[00:03.00]F: two")!!
        assertEquals(listOf("(echo)", "one", "two"), l.lines.map { it.text })
        assertEquals(listOf(false, false, true), l.lines.map { it.duet })
        assertNull(l.lines[1].background)
    }
}

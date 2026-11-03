package com.mrgnstr.harmony.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {
    @Test fun timeTagFormats() {
        val lyrics = LrcParser.parse("[00:01.5]a\n[00:02.25]b\n[00:03.125]c\n[01:04]d\n[02:05:50]e")!!
        assertEquals(true, lyrics.synced)
        assertEquals(listOf(1_500L, 2_250L, 3_125L, 64_000L, 125_500L), lyrics.lines.map { it.startMs })
        assertEquals(listOf("a", "b", "c", "d", "e"), lyrics.lines.map { it.text })
    }

    @Test fun headersOffsetAndRepeatedTags() {
        val lyrics = LrcParser.parse("﻿[ar:Mili]\n[ti:Compass]\n[offset:+500]\n[00:10.00][00:30.00]Chorus\n[00:20.00]Verse")!!
        assertEquals(listOf(LyricLine(9_500, "Chorus"), LyricLine(19_500, "Verse"), LyricLine(29_500, "Chorus")), lyrics.lines)
    }

    @Test fun wordTimesAreDropped() {
        assertEquals("one two", LrcParser.parse("[00:01.00]<00:01.00>one <00:01.50>two")!!.lines.single().text)
    }

    @Test fun breaksOnlyBetweenLines() {
        val lyrics = LrcParser.parse("[00:00.00]\n[00:05.00]a\n[00:10.00]\n[00:11.00]\n[00:20.00]b\n[00:30.00]")!!
        assertEquals(listOf(LyricLine(5_000, "a"), LyricLine(10_000, ""), LyricLine(20_000, "b")), lyrics.lines)
    }

    @Test fun plainText() {
        val lyrics = LrcParser.parse("\nfirst\n\nsecond\n")!!
        assertEquals(false, lyrics.synced)
        assertEquals(listOf("first", "", "second"), lyrics.lines.map { it.text })
        assertEquals(-1, lyrics.activeIndex(5_000))
    }

    @Test fun empty() {
        assertNull(LrcParser.parse(""))
        assertNull(LrcParser.parse("[ar:Nobody]\n[00:01.00]"))
    }

    @Test fun activeIndex() {
        val lyrics = LrcParser.parse("[00:05.00]a\n[00:10.00]b\n[00:15.00]c")!!
        assertEquals(-1, lyrics.activeIndex(4_999))
        assertEquals(0, lyrics.activeIndex(5_000))
        assertEquals(1, lyrics.activeIndex(14_999))
        assertEquals(2, lyrics.activeIndex(99_000))
    }
}

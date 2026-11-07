package com.mrgnstr.harmony.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlParserTest {
    private val wordLevel = """<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="ja">
  <head>
    <metadata>
      <ttm:agent type="person" xml:id="v1"><ttm:name>Lead</ttm:name></ttm:agent>
      <ttm:agent type="person" xml:id="v2"/>
      <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
        <songwriters><songwriter>Someone</songwriter></songwriters>
        <translations>
          <translation type="subtitle" xml:lang="en"><text for="L1">Hello world</text><text for="L2">Second line</text></translation>
          <translation type="subtitle" xml:lang="de"><text for="L1">Hallo Welt</text></translation>
        </translations>
        <transliterations>
          <transliteration xml:lang="ja-Latn">
            <text for="L1"><span begin="0:01.000" end="0:01.500">kon</span><span begin="0:01.500" end="0:02.000">nichi</span> <span begin="0:02.000" end="0:02.500">wa</span></text>
          </transliteration>
        </transliterations>
      </iTunesMetadata>
    </metadata>
  </head>
  <body dur="0:09.000">
    <div begin="0:01.000" end="0:09.000" itunes:songPart="Verse">
      <p begin="0:01.000" end="0:02.500" itunes:key="L1" ttm:agent="v1"><span begin="0:01.000" end="0:01.500">Hel</span><span begin="0:01.500" end="0:02.000">lo</span> <span begin="0:02.000" end="0:02.500">world</span><span ttm:role="x-bg"><span begin="0:01.800" end="0:02.300">(ooh</span> <span begin="0:02.300" end="0:02.600">yeah)</span></span></p>
      <p begin="0:03.000" end="0:04.000" itunes:key="L2" ttm:agent="v2">
        <span ttm:role="x-bg"><span begin="0:03.200" end="0:03.600">(back)</span></span>
        <span begin="0:03.000" end="0:03.500">Second</span>
        <span begin="0:03.500" end="0:04.000">line</span>
        <span ttm:role="x-translation" xml:lang="en">Second line (inline)</span>
        <span ttm:role="x-roman">daini gyo</span>
      </p>
      <p begin="0:05.000" end="0:06.000" ttm:agent="v1"><span ttm:role="x-bg"><span begin="0:05.000" end="0:06.000">(aah)</span></span></p>
    </div>
  </body>
</tt>"""

    @Test fun wordLevelDuetBackgroundTranslationRoman() {
        val l = TtmlParser.parse(wordLevel)!!
        assertTrue(l.synced)
        assertTrue(l.hasWordCues)
        assertTrue(l.hasTranslations)
        assertTrue(l.hasPronunciations)
        assertEquals("ja", l.lang)
        assertEquals(listOf("v1", "v2"), l.agents.map { it.id })
        assertEquals("main", l.agents[0].role)
        assertEquals("Lead", l.agents[0].name)
        assertEquals(3, l.lines.size)

        val a = l.lines[0]
        assertEquals("Hello world", a.text)
        assertEquals(listOf("Hel", "lo ", "world"), a.cues.map { it.text })
        assertEquals(1_000L, a.startMs)
        assertEquals(2_500L, a.endMs)
        assertFalse(a.duet)
        assertEquals("Hello world", a.translation) // first translation wins
        assertEquals("konnichi wa", a.pronunciation)
        assertEquals(listOf("kon", "nichi ", "wa"), a.pronunciationCues.map { it.text })
        assertEquals("ooh yeah", a.background!!.text)
        assertEquals(listOf("ooh ", "yeah"), a.background!!.cues.map { it.text })
        assertEquals(1_800L, a.background!!.startMs)

        val b = l.lines[1]
        assertTrue(b.duet)
        assertEquals("v2", b.agentId)
        assertEquals("Second line", b.text)
        assertEquals("Second line (inline)", b.translation)
        assertEquals("daini gyo", b.pronunciation)
        assertTrue(b.pronunciationCues.isEmpty())
        assertEquals("back", b.background!!.text) // before the main spans
        assertEquals(3_200L, b.background!!.startMs)
    }

    @Test fun bgOnlyParagraphHangsOrIsPromoted() {
        val l = TtmlParser.parse(wordLevel)!!
        // The line before already has a background, so the bg-only paragraph stands as a line (parentheses stripped).
        assertEquals("aah", l.lines[2].text)
        assertEquals(5_000L, l.lines[2].startMs)
        val src = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
<p begin="1s" end="2s"><span begin="1s" end="2s">Main</span></p>
<p begin="1.5s" end="2.5s"><span ttm:role="x-bg"><span begin="1.5s" end="2.5s">(echo)</span></span></p>
</div></body></tt>"""
        val h = TtmlParser.parse(src)!!
        assertEquals(1, h.lines.size)
        assertEquals("echo", h.lines[0].background!!.text)
    }

    @Test fun lineLevel() {
        val src = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Line" xml:lang="en">
<body><div>
<p begin="00:00:01.200" end="00:00:03.000">First line</p>
<p begin="3.5s" dur="1500ms">Second &amp; last</p>
<p begin="00:05.000" end="00:05.500"></p>
</div></body></tt>"""
        val l = TtmlParser.parse(src)!!
        assertFalse(l.hasWordCues)
        assertEquals(listOf("First line", "Second & last"), l.lines.map { it.text })
        assertEquals(listOf(1_200L, 3_500L), l.lines.map { it.startMs })
        assertEquals(listOf<Long?>(3_000L, 5_000L), l.lines.map { it.endMs })
        assertTrue(l.lines.all { it.cues.isEmpty() })
    }

    @Test fun unparseableOrEmpty() {
        assertNull(TtmlParser.parse("not xml"))
        assertNull(TtmlParser.parse("<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div/></body></tt>"))
        assertNull(TtmlParser.parse("<!DOCTYPE tt [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><tt><body><div><p begin=\"1s\">&x;</p></div></body></tt>"))
    }

    @Test fun timeFormats() {
        assertEquals(3_723_450L, TtmlParser.time("01:02:03.45"))
        assertEquals(62_500L, TtmlParser.time("1:02.5"))
        assertEquals(12_500L, TtmlParser.time("12.5"))
        assertEquals(12_500L, TtmlParser.time("12.5s"))
        assertEquals(1_500L, TtmlParser.time("1500ms"))
        assertNull(TtmlParser.time("soon"))
    }
}

package com.mrgnstr.harmony.core.model

import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * Parses TTML lyrics: W3C TTML 1.0 with the Apple Music extensions (`itunes:timing`, `itunes:key`, `ttm:role` x-bg / x-translation /
 * x-roman, `<iTunesMetadata>` translations and transliterations) as written by Apple and AMLL. Times are absolute on the song's
 * timeline (as Apple writes them). Gaps are not turned into breaks: the UI derives those from the timing. Returns null when the
 * file does not parse or has no timed line. Pure JVM (SAX), so it runs in unit tests too.
 */
object TtmlParser {
    private class Node(val name: String, val attrs: Map<String, String>) {
        val children = ArrayList<Any>() // Node or String

        fun fullText(): String = buildString { for (c in children) if (c is Node) append(c.fullText()) else append(c as String) }
        fun descendants(name: String): List<Node> = children.filterIsInstance<Node>().flatMap { (if (it.name == name) listOf(it) else emptyList()) + it.descendants(name) }
    }

    private class Collected(val text: String, val cues: List<LyricWordCue>)

    private val ws = Regex("""\s+""")
    private val offsetTime = Regex("""^(\d+(?:\.\d+)?)(ms|h|m|s)$""")

    fun parse(source: String): Lyrics? {
        val root = try { readTree(source) } catch (e: Exception) { null } ?: return null
        val agents = root.descendants("agent").mapNotNull { a -> a.attrs["id"]?.let { it to a } }
        val agentName = agents.associate { (id, a) -> id to a.descendants("name").firstOrNull()?.fullText()?.trim()?.takeIf { it.isNotEmpty() } }
        val declared = agents.map { it.first }
        val mainId = if ("v1" in declared) "v1" else declared.firstOrNull()

        // Apple's side tables, by itunes:key. The first translation wins.
        val translations = root.descendants("translation").firstOrNull()?.descendants("text")
            ?.mapNotNull { t -> t.attrs["for"]?.let { it to t.fullText().replace(ws, " ").trim() } }?.toMap().orEmpty()
        val transliterations = root.descendants("transliteration").firstOrNull()?.descendants("text")
            ?.mapNotNull { t -> t.attrs["for"]?.let { it to collect(t, 0L) } }?.toMap().orEmpty()

        val body = root.descendants("body").firstOrNull() ?: return null
        val used = LinkedHashSet<String>()
        val lines = ArrayList<LyricLine>()
        for (p in body.descendants("p")) {
            val pStart = time(p.attrs["begin"])
            val pEnd = time(p.attrs["end"]) ?: time(p.attrs["dur"])?.let { d -> pStart?.plus(d) }
            val agent = p.attrs["agent"]
            if (agent != null) used += agent
            val agentId = agent ?: mainId
            val duet = agentId != null && mainId != null && agentId != mainId

            val main = collect(p, pStart ?: 0L, agentId)
            var bg: LyricLine? = null
            var translation: String? = null
            var roman: Collected? = null
            for (span in p.descendants("span")) {
                when (span.attrs["role"]) {
                    "x-bg" -> if (bg == null) bg = background(span, pStart, agentId, duet)
                    "x-translation" -> if (translation == null) translation = span.fullText().replace(ws, " ").trim().takeIf { it.isNotEmpty() }
                    "x-roman" -> if (roman == null) roman = collect(span, pStart ?: 0L, agentId)
                }
            }
            val key = p.attrs["key"]
            translation = translation ?: key?.let { translations[it] }?.takeIf { it.isNotEmpty() }
            val pron = roman?.takeIf { it.text.isNotEmpty() } ?: key?.let { transliterations[it] }?.takeIf { it.text.isNotEmpty() }

            if (main.text.isEmpty()) {
                // A background-only paragraph hangs on the line before it; with none, it is promoted to a line.
                val b = bg ?: continue
                val prev = lines.lastOrNull()
                if (prev != null && prev.background == null && !prev.isBreak) lines[lines.lastIndex] = prev.copy(background = b)
                else lines += b
                continue
            }
            val start = pStart ?: main.cues.firstOrNull()?.startMs ?: continue
            lines += LyricLine(
                startMs = start,
                text = main.text,
                endMs = pEnd ?: main.cues.lastOrNull()?.endMs,
                agentId = agentId,
                cues = main.cues,
                translation = translation,
                pronunciation = pron?.text,
                duet = duet,
                background = bg,
                pronunciationCues = pron?.cues.orEmpty(),
            )
        }
        if (lines.isEmpty()) return null
        val ids = (declared + used).distinct()
        val sorted = lines.sortedBy { it.startMs }
        return Lyrics(
            lines = sorted,
            synced = true,
            lang = root.attrs["lang"]?.takeIf { it.isNotBlank() } ?: "und",
            agents = ids.map { LyricAgent(it, if (it == mainId) "main" else "voice", agentName[it]) },
            hasTranslations = sorted.any { it.translation != null },
            hasPronunciations = sorted.any { it.pronunciation != null },
            hasWordCues = sorted.any { it.cues.isNotEmpty() || it.background?.cues?.isNotEmpty() == true },
        )
    }

    private fun background(span: Node, pStart: Long?, agentId: String?, duet: Boolean): LyricLine? {
        val c = collect(span, time(span.attrs["begin"]) ?: pStart ?: 0L, agentId)
        // Backing vocals are usually written in parentheses.
        val cues = c.cues.toMutableList()
        val text = if (cues.isNotEmpty()) {
            cues[0] = cues[0].copy(text = cues[0].text.removePrefix("("))
            cues[cues.lastIndex] = cues.last().let { it.copy(text = it.text.trimEnd().removeSuffix(")")) }
            cues.removeAll { it.text.isEmpty() }
            cues.joinToString("") { it.text }.trim()
        } else c.text.removeSurrounding("(", ")").trim()
        if (text.isEmpty()) return null
        val start = time(span.attrs["begin"]) ?: cues.firstOrNull()?.startMs ?: pStart ?: return null
        val end = time(span.attrs["end"]) ?: time(span.attrs["dur"])?.let { start + it } ?: cues.lastOrNull()?.endMs
        return LyricLine(start, text, end, agentId, cues, duet = duet)
    }

    /**
     * The text and word cues under [node]: timed spans are cues, text between them is real text (whitespace becomes a space kept
     * with the word before it), background / translation / roman spans are left to their own readers.
     */
    private fun collect(node: Node, defaultStart: Long, agentId: String? = null): Collected {
        val cues = ArrayList<LyricWordCue>()
        val pending = StringBuilder()
        val plain = StringBuilder()
        fun addText(t: String) {
            plain.append(t)
            if (cues.isEmpty()) { pending.append(t); return }
            val last = cues.last()
            if (t.isNotBlank()) cues[cues.lastIndex] = last.copy(text = last.text + t)
            else if (last.text.isNotEmpty() && !last.text.last().isWhitespace()) cues[cues.lastIndex] = last.copy(text = last.text + " ")
        }
        fun walk(n: Node) {
            for (c in n.children) when {
                c is String -> addText(c.replace(ws, " "))
                c !is Node -> {}
                c.name == "br" -> addText(" ")
                c.attrs["role"] in skipRoles -> {}
                c.attrs["begin"] != null -> {
                    val text = c.fullText().replace(ws, " ")
                    if (text.isBlank()) { if (cues.isNotEmpty()) addText(" ") } else {
                        val start = time(c.attrs["begin"]) ?: defaultStart
                        val end = time(c.attrs["end"]) ?: time(c.attrs["dur"])?.let { start + it }
                        plain.append(text)
                        cues += LyricWordCue(start, end, text = pending.toString() + text, agentId = agentId)
                        pending.clear()
                    }
                }
                else -> walk(c) // an untimed wrapper span
            }
        }
        walk(node)
        if (cues.isNotEmpty()) {
            cues[0] = cues[0].copy(text = cues[0].text.trimStart())
            cues[cues.lastIndex] = cues.last().let { it.copy(text = it.text.trimEnd()) }
            val kept = cues.filter { it.text.isNotEmpty() }
            return Collected(kept.joinToString("") { it.text }, kept)
        }
        return Collected(plain.toString().trim(), emptyList())
    }

    private val skipRoles = setOf("x-bg", "x-translation", "x-roman")

    /** Clock (`hh:mm:ss.fff`, `mm:ss.fff`, `ss.fff`) or offset (`12.5s`, `1500ms`, `2m`, `1h`) time in ms; null if unreadable. */
    internal fun time(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        offsetTime.matchEntire(s)?.let { m ->
            val v = m.groupValues[1].toDouble()
            val unit = when (m.groupValues[2]) { "h" -> 3_600_000.0; "m" -> 60_000.0; "s" -> 1_000.0; else -> 1.0 }
            return Math.round(v * unit)
        }
        val parts = s.split(':')
        if (parts.size > 4) return null
        // A fourth part would be frames: not used by lyrics files.
        val nums = parts.take(3).map { it.replace(',', '.').toDoubleOrNull() ?: return null }
        val ms = nums.fold(0.0) { acc, v -> acc * 60 + v }
        return Math.round(ms * 1_000)
    }

    private fun readTree(source: String): Node? {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // Lyrics files come from anywhere: no DOCTYPE, no external entities.
            for ((f, v) in listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            )) try { setFeature(f, v) } catch (_: Exception) { /* not every parser knows every feature */ }
        }
        var root: Node? = null
        val stack = ArrayList<Node>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) { stack.lastOrNull()?.children?.add(text.toString()); text.setLength(0) }
        }
        val handler = object : DefaultHandler() {
            override fun resolveEntity(publicId: String?, systemId: String?) = InputSource(StringReader(""))
            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes) {
                flush()
                val attrs = HashMap<String, String>()
                for (i in 0 until atts.length) {
                    val name = atts.getLocalName(i).takeIf { !it.isNullOrEmpty() } ?: atts.getQName(i).substringAfter(':')
                    attrs[name] = atts.getValue(i)
                }
                val name = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')
                val node = Node(name, attrs)
                stack.lastOrNull()?.children?.add(node)
                if (root == null) root = node
                stack += node
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                flush()
                if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { text.append(ch, start, length) }
        }
        factory.newSAXParser().parse(InputSource(StringReader(source.removePrefix("﻿").trimStart())), handler)
        return root
    }
}

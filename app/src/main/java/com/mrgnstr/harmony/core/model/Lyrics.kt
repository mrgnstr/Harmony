package com.mrgnstr.harmony.core.model

/**
 * One word/syllable timing cue inside a lyric line. [text] is verbatim, including any space that follows it, so the cues of a
 * line concatenate to the line's text. [endMs] is null when the source gives no end (the next cue's start then ends it).
 */
data class LyricWordCue(
    val startMs: Long,
    val endMs: Long? = null,
    val byteStart: Int = 0,
    val byteEnd: Int = 0,
    val text: String,
    val agentId: String? = null,
)

/** A vocal agent in multi-vocal lyrics (e.g. role="main" vs backing/duet). */
data class LyricAgent(
    val id: String,
    val role: String,
    val name: String? = null,
)

/**
 * One lyric line. [startMs] is -1 in unsynced lyrics; an empty [text] in synced lyrics marks an instrumental break.
 * [duet]: sung by the second voice, drawn right-aligned. [background]: the backing vocal that belongs to this line (shown under
 * it while it is active); it is never a line of its own. [pronunciationCues] time [pronunciation] syllable by syllable when the
 * source does so.
 */
data class LyricLine(
    val startMs: Long,
    val text: String,
    val endMs: Long? = null,
    val agentId: String? = null,
    val cues: List<LyricWordCue> = emptyList(),
    val translation: String? = null,
    val pronunciation: String? = null,
    val duet: Boolean = false,
    val background: LyricLine? = null,
    val pronunciationCues: List<LyricWordCue> = emptyList(),
) {
    val isBreak: Boolean get() = text.isEmpty()
}

/** A song's lyrics: [synced] when every line has a start time (sorted by it), else plain text in file order. */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val lang: String = "und",
    val kind: String = "main",
    val agents: List<LyricAgent> = emptyList(),
    val hasTranslations: Boolean = false,
    val hasPronunciations: Boolean = false,
    val hasWordCues: Boolean = false,
) {
    /** The line playing at [positionMs]: the last one started, -1 before the first (and always in unsynced lyrics). */
    fun activeIndex(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }

    /** When line [i] ends: its own end, else the next line's start, else a few seconds after it starts. */
    fun endOf(i: Int): Long {
        val line = lines[i]
        return line.endMs ?: lines.getOrNull(i + 1)?.startMs ?: (line.startMs + 4_000)
    }
}

/**
 * Parses LRC: `[mm:ss.xx]` / `[mm:ss.xxx]` / `[mm:ss]` time tags (several on one line repeat it), `[offset:±ms]`, and other
 * `[tag:value]` headers (ignored). Also enhanced LRC (A2) word times `<mm:ss.xx>` and the informal Apple Music / QQ extension:
 * `v1:` / `v2:` voice prefixes (`M:` `F:` `D:` too; any voice but v1 is a duet line) and backing vocals, either a `bg:` line or an
 * inline `[bg: ...]`, which attach to the nearest preceding line as its background. A file with no time tags is read as plain
 * lyrics. Returns null when nothing is left.
 */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val headerTag = Regex("""^\[([A-Za-z#]+):(.*)]$""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val voicePrefix = Regex("""^(?:([vV]\d+)|([MFD])):\s?""")
    private val bgPrefix = Regex("""^bg\s*:\s*""", RegexOption.IGNORE_CASE)
    private val bgInline = Regex("""\[bg:\s*(.*?)]""", RegexOption.IGNORE_CASE)

    /** A line's text with its word cues (times before the offset) and the end a trailing word tag gave it. */
    private class Words(val text: String, val cues: List<LyricWordCue>, val endMs: Long?)

    fun parse(source: String): Lyrics? {
        var offsetMs = 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        val voices = LinkedHashSet<String>()
        var lastMain = -1 // index in [timed] of the line a background attaches to
        for (raw in source.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) { plain += ""; continue }
            val header = headerTag.matchEntire(line)
            if (header != null) {
                val key = header.groupValues[1]
                if (key.equals("offset", ignoreCase = true)) offsetMs = header.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0L
                // An inline background on a line of its own belongs to the line above.
                else if (key.equals("bg", ignoreCase = true) && lastMain >= 0) attachBackground(timed, lastMain, header.groupValues[2])
                continue
            }
            // Leading time tags; the text is what follows the last one.
            var rest = line
            val starts = ArrayList<Long>(1)
            while (true) {
                val m = timeTag.matchAt(rest, 0) ?: break
                starts += timeOf(m)
                rest = rest.substring(m.range.last + 1)
            }
            if (starts.isEmpty()) { plain += wordTag.replace(rest, "").trim(); continue }

            if (bgPrefix.containsMatchIn(rest)) {
                val body = rest.replace(bgPrefix, "")
                if (lastMain >= 0) { attachBackground(timed, lastMain, body, starts.first()); continue }
                // Nothing to attach to: it is an ordinary line.
                val w = words(body, starts.first())
                for (s in starts) timed += LyricLine(s, w.text, w.endMs, cues = w.cues)
                lastMain = timed.lastIndex
                continue
            }
            var agent: String? = null
            voicePrefix.find(rest)?.let { m ->
                agent = when (m.groupValues[2]) { "M" -> "v1"; "F", "D" -> "v2"; else -> m.groupValues[1].lowercase() }
                voices += agent!!
                rest = rest.substring(m.range.last + 1)
            }
            val inline = bgInline.find(rest)
            if (inline != null) rest = rest.removeRange(inline.range)
            val w = words(rest, starts.first())
            val duet = agent != null && agent != "v1"
            for (s in starts) timed += LyricLine(s, w.text, w.endMs, agentId = agent, cues = w.cues, duet = duet)
            lastMain = if (w.text.isEmpty()) -1 else timed.lastIndex
            if (inline != null && lastMain >= 0) attachBackground(timed, lastMain, inline.groupValues[1])
        }
        if (timed.isNotEmpty()) {
            // A positive offset makes the lyrics come sooner.
            val sorted = timed.map { shift(it, -offsetMs) }.sortedBy { it.startMs }
            // Breaks only mean something between sung lines: drop leading, trailing and repeated ones.
            val cleaned = ArrayList<LyricLine>(sorted.size)
            for (l in sorted) if (!(l.isBreak && (cleaned.isEmpty() || cleaned.last().isBreak))) cleaned += l
            while (cleaned.isNotEmpty() && cleaned.last().isBreak) cleaned.removeAt(cleaned.lastIndex)
            if (cleaned.isEmpty()) return null
            val lines = cleaned.mapIndexed { i, l ->
                val next = cleaned.getOrNull(i + 1)?.startMs
                val withAgent = if (voices.isNotEmpty() && l.agentId == null && !l.isBreak) l.copy(agentId = "v1") else l
                closeCues(withAgent, next)
            }
            val agents = voices.takeIf { it.isNotEmpty() }?.let { v ->
                (listOf("v1") + v.filter { it != "v1" }).map { LyricAgent(it, if (it == "v1") "main" else "voice") }
            }.orEmpty()
            return Lyrics(lines, synced = true, agents = agents, hasWordCues = lines.any { it.cues.isNotEmpty() })
        }
        val text = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        return text.takeIf { it.isNotEmpty() }?.let { lines -> Lyrics(lines.map { LyricLine(-1, it) }, synced = false) }
    }

    private fun attachBackground(timed: MutableList<LyricLine>, index: Int, body: String, lineStart: Long? = null) {
        val main = timed[index]
        if (main.background != null) return
        var w = words(body, lineStart ?: main.startMs)
        // Backing vocals are usually written in parentheses.
        var cues = w.cues.toMutableList()
        if (cues.isNotEmpty()) {
            cues[0] = cues[0].copy(text = cues[0].text.removePrefix("("))
            val l = cues.last()
            val t = l.text.trimEnd()
            cues[cues.lastIndex] = l.copy(text = t.removeSuffix(")"))
            cues = cues.filter { it.text.isNotEmpty() }.toMutableList()
            w = Words(cues.joinToString("") { it.text }.trim(), cues, w.endMs)
        } else w = Words(w.text.removeSurrounding("(", ")").trim(), emptyList(), w.endMs)
        if (w.text.isEmpty()) return
        val start = w.cues.firstOrNull()?.startMs ?: lineStart ?: main.startMs
        timed[index] = main.copy(
            background = LyricLine(start, w.text, w.endMs ?: w.cues.lastOrNull()?.endMs, main.agentId, w.cues, duet = main.duet),
        )
    }

    /** Splits [rest] at its `<mm:ss.xx>` tags: each starts the text after it, the next tag (or a trailing one) ends it. */
    private fun words(rest: String, lineStart: Long): Words {
        val tags = wordTag.findAll(rest).toList()
        if (tags.isEmpty()) return Words(rest.trim(), emptyList(), null)
        val cues = ArrayList<LyricWordCue>()
        var trailingEnd: Long? = null
        fun closePrev(t: Long) {
            val p = cues.lastOrNull() ?: return
            if (p.endMs == null) cues[cues.lastIndex] = p.copy(endMs = t)
        }
        val lead = rest.substring(0, tags[0].range.first)
        if (lead.isNotBlank()) cues += LyricWordCue(lineStart, text = lead)
        tags.forEachIndexed { i, m ->
            val t = timeOf(m)
            val seg = rest.substring(m.range.last + 1, tags.getOrNull(i + 1)?.range?.first ?: rest.length)
            closePrev(t)
            if (seg.isNotBlank()) cues += LyricWordCue(t, text = seg)
            else {
                // A gap between words keeps its space with the word before it.
                if (seg.isNotEmpty() && cues.isNotEmpty()) cues[cues.lastIndex] = cues.last().let { it.copy(text = it.text + seg) }
                if (i == tags.lastIndex) trailingEnd = t
            }
        }
        if (cues.isNotEmpty()) {
            cues[0] = cues[0].copy(text = cues[0].text.trimStart())
            cues[cues.lastIndex] = cues.last().let { it.copy(text = it.text.trimEnd()) }
        }
        val kept = cues.filter { it.text.isNotEmpty() }
        return Words(kept.joinToString("") { it.text }, kept, trailingEnd)
    }

    private fun shift(l: LyricLine, by: Long): LyricLine {
        fun t(v: Long) = (v + by).coerceAtLeast(0)
        fun c(cs: List<LyricWordCue>) = cs.map { it.copy(startMs = t(it.startMs), endMs = it.endMs?.let(::t)) }
        return l.copy(
            startMs = t(l.startMs), endMs = l.endMs?.let(::t), cues = c(l.cues),
            background = l.background?.let { shift(it, by) },
        )
    }

    /** The last word of a line (and of its background) ends where the next line starts when nothing says otherwise. */
    private fun closeCues(l: LyricLine, nextStart: Long?): LyricLine {
        fun close(x: LyricLine): LyricLine {
            val last = x.cues.lastOrNull() ?: return x
            if (last.endMs != null || nextStart == null || nextStart <= last.startMs) return x.copy(endMs = x.endMs ?: last.endMs)
            return x.copy(cues = x.cues.dropLast(1) + last.copy(endMs = nextStart), endMs = x.endMs)
        }
        val main = close(l)
        return main.copy(background = main.background?.let { b ->
            val cb = close(b)
            cb.copy(endMs = cb.endMs ?: cb.cues.lastOrNull()?.endMs)
        })
    }

    private fun timeOf(m: MatchResult): Long {
        val (min, sec, frac) = m.destructured
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
    }
}

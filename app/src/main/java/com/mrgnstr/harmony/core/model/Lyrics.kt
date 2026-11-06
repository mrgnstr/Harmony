package com.mrgnstr.harmony.core.model

/** One word/syllable timing cue inside a lyric line (OpenSubsonic songLyrics v2). */
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

/** One lyric line. [startMs] is -1 in unsynced lyrics; an empty [text] in synced lyrics marks an instrumental break. */
data class LyricLine(
    val startMs: Long,
    val text: String,
    val endMs: Long? = null,
    val agentId: String? = null,
    val cues: List<LyricWordCue> = emptyList(),
    val translation: String? = null,
    val pronunciation: String? = null,
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
}

/**
 * Parses LRC: `[mm:ss.xx]` / `[mm:ss.xxx]` / `[mm:ss]` time tags (several on one line repeat it), `[offset:±ms]`, and other
 * `[tag:value]` headers (ignored). Enhanced-LRC word times (`<mm:ss.xx>`) are dropped for now. A file with no time tags is
 * read as plain lyrics. Returns null when nothing is left.
 */
object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val headerTag = Regex("""^\[([A-Za-z#]+):(.*)]$""")
    private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(source: String): Lyrics? {
        var offsetMs = 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (raw in source.removePrefix("﻿").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) { plain += ""; continue }
            val header = headerTag.matchEntire(line)
            if (header != null) {
                if (header.groupValues[1].equals("offset", ignoreCase = true)) offsetMs = header.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0L
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
            val text = wordTag.replace(rest, "").trim()
            if (starts.isEmpty()) plain += text
            else for (s in starts) timed += LyricLine(s, text)
        }
        if (timed.isNotEmpty()) {
            // A positive offset makes the lyrics come sooner.
            val lines = timed.map { it.copy(startMs = (it.startMs - offsetMs).coerceAtLeast(0)) }.sortedBy { it.startMs }
            // Breaks only mean something between sung lines: drop leading, trailing and repeated ones.
            val cleaned = ArrayList<LyricLine>(lines.size)
            for (l in lines) if (!(l.isBreak && (cleaned.isEmpty() || cleaned.last().isBreak))) cleaned += l
            while (cleaned.isNotEmpty() && cleaned.last().isBreak) cleaned.removeAt(cleaned.lastIndex)
            return cleaned.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = true) }
        }
        val text = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        return text.takeIf { it.isNotEmpty() }?.let { lines -> Lyrics(lines.map { LyricLine(-1, it) }, synced = false) }
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

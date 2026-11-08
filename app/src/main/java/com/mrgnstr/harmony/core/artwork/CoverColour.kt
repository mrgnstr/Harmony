package com.mrgnstr.harmony.core.artwork

import com.materialkolor.hct.Cam16
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score

/**
 * What the colour pickers know about a cover: the seed the page is themed from, two more colours of it, the colour that covers most of
 * it, and whether it has any real colour at all. A cover with none gets a grey [seed] (so a theme made from it is grey), not whatever hue
 * its noise happens to have: Material reads only a seed's hue, and every grey has one (pure greys are all 209.5°, a cyan-blue).
 */
class CoverAnalysis(val seed: Int, val secondary: Int, val tertiary: Int, val dominant: Int, val colourful: Boolean)

/** How a theme made from a seed should look; the page's [PaletteStyle] and Now Playing's tint both follow from the seed's chroma. */
enum class SeedLook { Monochrome, Neutral, Vivid }

/** The one place that decides what counts as colour in a cover. Chroma is HCT chroma (0 grey, ~120 the most vivid there is). */
object CoverColour {
    /** Below this a colour reads as grey: JPEG noise, a sepia or blue cast, white and black all sit here. */
    const val GreyBelow = 8.0

    /** From [GreyBelow] to here a colour is muted: a theme gets a hint of it instead of the full TonalSpot colourfulness. */
    const val MutedBelow = 20.0

    /** The share of a cover that has to be coloured (chroma at least [GreyBelow]) before its colours count: a grey photo with a red caption is grey. */
    const val MinColourShare = 0.12

    private const val Colours = 24
    private const val SameColour = 12.0
    private const val OPAQUE = 0xFF000000.toInt()

    fun lookOf(seedChroma: Double): SeedLook = when {
        seedChroma < GreyBelow -> SeedLook.Monochrome
        seedChroma < MutedBelow -> SeedLook.Neutral
        else -> SeedLook.Vivid
    }

    /**
     * How much of a tint Now Playing's controls take from a cover whose most common colour has [sourceChroma]: none for a grey one,
     * fading in over the muted range, all of it from [MutedBelow] up. Continuous, so a faint cast never jumps to a visible colour.
     */
    fun tintFade(sourceChroma: Double): Double = ((sourceChroma - GreyBelow) / (MutedBelow - GreyBelow)).coerceIn(0.0, 1.0)

    /**
     * A seed from one average colour of a cover (Navidrome's `dominantColor` / thumbhash), the only colour a remote cover gives before it is
     * decoded. An average of a busy picture is always muted, so it says little about how vivid the cover is: a grey average stays grey (a
     * grey cover), anything with colour in it is lifted to a vivid seed of that hue.
     */
    fun fromAverage(argb: Int): Int = liftToVivid(argb) or OPAQUE

    /** [pixels] are opaque ARGB. Empty input has no colour: a mid grey. */
    fun analyze(pixels: IntArray): CoverAnalysis {
        if (pixels.isEmpty()) return grey(0x80)
        val counts = QuantizerCelebi.quantize(pixels, Colours)
        if (counts.isEmpty()) return grey(0x80)
        val dominant = dominantOf(counts)
        val total = counts.values.sum().toDouble()
        val colourShare = counts.entries.sumOf { (c, n) -> if (Hct.fromInt(c).chroma >= GreyBelow) n else 0 } / total
        // Material's own ranking (population and chroma, hues with under 1% of the picture dropped); null: no colour is a result, not blue.
        val ranked = if (colourShare >= MinColourShare) Score.score(counts, 4, null, true).filter { Hct.fromInt(it).chroma >= GreyBelow } else emptyList()
        val seed = ranked.firstOrNull() ?: return grey(luminanceOf(dominant), dominant)
        // Cluster colours are averages of what falls in them, so a busy picture of vivid colours has muted ones. Whether the cover as a
        // whole is vivid is read from its pixels, and only then is a washed-out seed (or dominant colour) lifted to a vivid one.
        val vivid = meanColourChroma(pixels) >= MutedBelow
        fun lift(c: Int) = if (vivid) liftToVivid(c) else c
        return CoverAnalysis(lift(seed) or OPAQUE, lift(ranked.getOrNull(1) ?: seed) or OPAQUE, lift(ranked.getOrNull(2) ?: seed) or OPAQUE, lift(dominant), colourful = true)
    }

    /** The mean chroma of the pixels that have colour in them (chroma at least [GreyBelow]); 0 when none do. Every few pixels is plenty. */
    private fun meanColourChroma(pixels: IntArray): Double {
        var sum = 0.0
        var n = 0
        var i = 0
        while (i < pixels.size) {
            val c = Hct.fromInt(pixels[i]).chroma
            if (c >= GreyBelow) { sum += c; n++ }
            i += 3
        }
        return if (n == 0) 0.0 else sum / n
    }

    /** A colour with chroma in the muted range (never a grey) given a vivid one, same hue and tone. */
    private fun liftToVivid(argb: Int): Int {
        val h = Hct.fromInt(argb)
        return if (h.chroma < GreyBelow || h.chroma >= MutedBelow) argb else Hct.from(h.hue, MutedBelow + 10.0, h.tone).toInt() or OPAQUE
    }

    /**
     * The colour that covers most of the picture. The quantizer may split one big flat area into several clusters, so clusters that look
     * alike (CAM16 distance under [SameColour]) count together; the biggest cluster of the biggest group speaks for it.
     */
    private fun dominantOf(counts: Map<Int, Int>): Int {
        val groups = ArrayList<Triple<Cam16, Int, IntArray>>() // the group's lead colour, its biggest cluster, and its total in [0]
        for ((c, n) in counts.entries.sortedByDescending { it.value }) {
            val cam = Cam16.fromInt(c)
            val group = groups.firstOrNull { it.first.distance(cam) < SameColour }
            if (group != null) group.third[0] += n else groups += Triple(cam, c, intArrayOf(n))
        }
        return groups.maxBy { it.third[0] }.second or OPAQUE
    }

    private fun grey(level: Int, dominant: Int = OPAQUE or (level * 0x010101)): CoverAnalysis {
        val g = OPAQUE or (level * 0x010101)
        return CoverAnalysis(g, g, g, dominant, colourful = false)
    }

    private fun luminanceOf(argb: Int): Int =
        ((0.299 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)).toInt()).coerceIn(0, 255)
}

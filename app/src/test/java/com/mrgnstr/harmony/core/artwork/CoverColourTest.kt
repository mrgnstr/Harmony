package com.mrgnstr.harmony.core.artwork

import com.materialkolor.hct.Hct
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverColourTest {
    private val opaque = 0xFF000000.toInt()
    private fun rgb(r: Int, g: Int, b: Int) = opaque or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** A picture of [n] pixels: [share] of them [colour], the rest [base], all with a little noise like a compressed JPEG has. */
    private fun cover(base: Int, colour: Int = base, share: Double = 0.0, n: Int = 4096, noise: Int = 4, seed: Int = 1): IntArray {
        val rnd = Random(seed)
        return IntArray(n) { i ->
            val c = if (i < n * share) colour else base
            fun ch(shift: Int) = ((c shr shift) and 0xFF) + rnd.nextInt(-noise, noise + 1)
            rgb(ch(16), ch(8), ch(0))
        }
    }

    private fun chroma(argb: Int) = Hct.fromInt(argb).chroma
    private fun hue(argb: Int) = Hct.fromInt(argb).hue

    private fun assertGrey(a: CoverAnalysis) {
        assertFalse(a.colourful)
        assertTrue("seed chroma ${chroma(a.seed)}", chroma(a.seed) < CoverColour.GreyBelow)
        assertEquals(SeedLook.Monochrome, CoverColour.lookOf(chroma(a.seed)))
    }

    @Test fun `a grey cover gets a grey seed`() = assertGrey(CoverColour.analyze(cover(rgb(128, 128, 128))))

    @Test fun `a dark and a light grey cover get grey seeds`() {
        assertGrey(CoverColour.analyze(cover(rgb(40, 40, 40))))
        assertGrey(CoverColour.analyze(cover(rgb(210, 210, 210))))
    }

    @Test fun `black and white covers are grey, not red or blue`() {
        assertGrey(CoverColour.analyze(cover(rgb(0, 0, 0), noise = 0)))
        assertGrey(CoverColour.analyze(cover(rgb(255, 255, 255), noise = 0)))
    }

    @Test fun `a faint cast in any direction stays grey`() {
        for (c in listOf(rgb(144, 138, 138), rgb(138, 144, 138), rgb(138, 138, 144), rgb(150, 142, 128))) assertGrey(CoverColour.analyze(cover(c)))
    }

    @Test fun `a grey photo with a small coloured caption is still grey`() =
        assertGrey(CoverColour.analyze(cover(rgb(110, 110, 110), colour = rgb(220, 30, 40), share = 0.03)))

    @Test fun `a vivid cover keeps its colour`() {
        val red = CoverColour.analyze(cover(rgb(200, 30, 40)))
        assertTrue(red.colourful)
        assertTrue("red hue ${hue(red.seed)}", hue(red.seed) < 40 || hue(red.seed) > 340)
        assertEquals(SeedLook.Vivid, CoverColour.lookOf(chroma(red.seed)))
        val blue = CoverColour.analyze(cover(rgb(30, 80, 210)))
        assertTrue("blue hue ${hue(blue.seed)}", hue(blue.seed) in 240.0..285.0)
    }

    @Test fun `a grey cover with a big coloured part takes that colour`() {
        val a = CoverColour.analyze(cover(rgb(110, 110, 110), colour = rgb(30, 80, 210), share = 0.4))
        assertTrue(a.colourful)
        assertTrue("hue ${hue(a.seed)}", hue(a.seed) in 240.0..285.0)
    }

    @Test fun `a busy cover of vivid colours is vivid even though its clusters average out muted`() {
        val palette = intArrayOf(rgb(20, 60, 200), rgb(150, 40, 200), rgb(255, 120, 190), rgb(60, 200, 90), rgb(250, 220, 40), rgb(40, 190, 230), rgb(220, 40, 60), rgb(250, 250, 250))
        val rnd = Random(7)
        val px = IntArray(4096) { palette[rnd.nextInt(palette.size)] }
        val a = CoverColour.analyze(px)
        assertTrue(a.colourful)
        assertEquals("seed chroma ${chroma(a.seed)}", SeedLook.Vivid, CoverColour.lookOf(chroma(a.seed)))
    }

    @Test fun `a muted cover is neither grey nor fully vivid`() {
        // Dusty rose: colourful enough to count, soft enough for a hint of colour only.
        val a = CoverColour.analyze(cover(rgb(170, 130, 135)))
        assertTrue(a.colourful)
        assertEquals(SeedLook.Neutral, CoverColour.lookOf(chroma(a.seed)))
    }

    @Test fun `the dominant colour is the one that covers most`() {
        val a = CoverColour.analyze(cover(rgb(20, 20, 20), colour = rgb(200, 30, 40), share = 0.3))
        assertTrue("dominant chroma ${chroma(a.dominant)}", chroma(a.dominant) < CoverColour.GreyBelow)
    }

    @Test fun `an average colour of a remote cover is grey when grey and vivid when coloured`() {
        assertEquals(SeedLook.Monochrome, CoverColour.lookOf(chroma(CoverColour.fromAverage(rgb(120, 120, 122)))))
        val muted = rgb(150, 125, 165) // what averaging a busy purple cover leaves
        assertEquals(SeedLook.Vivid, CoverColour.lookOf(chroma(CoverColour.fromAverage(muted))))
        assertEquals(hue(muted), hue(CoverColour.fromAverage(muted)), 6.0)
    }

    @Test fun `empty input is grey`() = assertGrey(CoverColour.analyze(IntArray(0)))

    @Test fun `tint fade is continuous from nothing to everything`() {
        assertEquals(0.0, CoverColour.tintFade(0.0), 0.0)
        assertEquals(0.0, CoverColour.tintFade(CoverColour.GreyBelow), 0.0)
        assertEquals(1.0, CoverColour.tintFade(CoverColour.MutedBelow), 0.0)
        assertEquals(1.0, CoverColour.tintFade(60.0), 0.0)
        var last = 0.0
        var c = 0.0
        while (c <= 30.0) {
            val f = CoverColour.tintFade(c)
            assertTrue(f >= last)
            assertTrue(f - last < 0.1)
            last = f
            c += 0.5
        }
    }
}

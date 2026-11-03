package com.mrgnstr.harmony.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackBlurTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun flatColourStaysFlat() {
        val c = argb(255, 40, 120, 200)
        val px = IntArray(16 * 16) { c }
        StackBlur.blur(px, 16, 16, 4)
        assertTrue(px.all { it == c })
    }

    @Test fun blurSpreadsSymmetrically() {
        val w = 16; val h = 16
        val px = IntArray(w * h) { argb(255, 0, 0, 0) }
        px[8 * w + 8] = argb(255, 255, 255, 255)
        StackBlur.blur(px, w, h, 3)
        val centre = px[8 * w + 8] and 0xFF
        val neighbour = px[8 * w + 9] and 0xFF
        assertTrue("centre should have dimmed", centre < 255)
        assertTrue("neighbour should have picked up light", neighbour > 0)
        // A point source blurs symmetrically (integer truncation makes totals unreliable at this scale).
        assertEquals(px[8 * w + 7], px[8 * w + 9])
        assertEquals(px[7 * w + 8], px[9 * w + 8])
    }

    @Test fun radiusZeroIsNoOp() {
        val px = IntArray(9) { argb(255, it * 20, 0, 0) }
        val copy = px.copyOf()
        StackBlur.blur(px, 3, 3, 0)
        assertTrue(px.contentEquals(copy))
    }
}

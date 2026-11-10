package com.mrgnstr.harmony.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnsShelfTest {
    private val min = 320f
    private val gap = 12f

    @Test fun a_phone_upright_gets_one_column_that_fills_the_width() {
        val usable = 393f - 32f
        assertEquals(1, columnCount(usable, min, gap))
        assertEquals(usable, columnWidth(usable, 1, gap), 0.001f)
    }

    @Test fun a_column_is_added_only_once_two_minimum_widths_and_the_gap_fit() {
        assertEquals(1, columnCount(2 * min + gap - 0.5f, min, gap))
        assertEquals(2, columnCount(2 * min + gap, min, gap))
        assertEquals(3, columnCount(3 * min + 2 * gap, min, gap))
    }

    @Test fun columns_stretch_to_fill_what_is_left() {
        val usable = 800f
        val count = columnCount(usable, min, gap)
        assertEquals(2, count)
        val width = columnWidth(usable, count, gap)
        assertEquals(usable, width * count + gap * (count - 1), 0.001f)
        assert(width >= min)
    }

    @Test fun never_fewer_than_one_column_even_when_narrower_than_the_minimum() {
        assertEquals(1, columnCount(200f, min, gap))
    }
}

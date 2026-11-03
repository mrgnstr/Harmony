package com.mrgnstr.harmony.core.mediastore

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnMappingTest {
    @Test fun extendedColumnsOnlyFromApi30() {
        assertFalse(ColumnMapping.hasExtendedColumns(25))
        assertFalse(ColumnMapping.hasExtendedColumns(29))
        assertTrue(ColumnMapping.hasExtendedColumns(30))
    }

    @Test fun projectionGrowsOnApi30() {
        assertArrayEquals(ColumnMapping.BASE, ColumnMapping.projection(25))
        assertEquals(ColumnMapping.BASE.size + 3, ColumnMapping.projection(34).size)
    }

    @Test fun trackDecoding() {
        assertEquals(1 to 7, ColumnMapping.decodeTrack(7))
        assertEquals(2 to 3, ColumnMapping.decodeTrack(2003))
        assertEquals(1 to 0, ColumnMapping.decodeTrack(0))
    }

    @Test fun discParsing() {
        assertEquals(1, ColumnMapping.parseDisc("1/2"))
        assertEquals(3, ColumnMapping.parseDisc(" 3 "))
        assertNull(ColumnMapping.parseDisc(null))
        assertNull(ColumnMapping.parseDisc("x"))
    }

    @Test fun unknownTagsFallBack() {
        assertEquals("Unknown Artist", ColumnMapping.clean("<unknown>", "Unknown Artist"))
        assertEquals("Unknown Artist", ColumnMapping.clean("  ", "Unknown Artist"))
        assertEquals("Muse", ColumnMapping.clean("Muse", "Unknown Artist"))
    }
}

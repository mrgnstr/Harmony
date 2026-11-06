package com.mrgnstr.harmony.core.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [ThumbHash] against Navidrome's golden vectors from `core/artwork/thumbhash/testdata/golden.json`.
 */
class ThumbHashTest {
    private val goldenVectors = mapOf(
        "alpha.png" to "JOiFBQ4nkIexh3p4iA8uB+lYhIeAh3d4dw==",
        "landscape.png" to "3wcOFJpwh4eBh3d4iIePgAj3hw==",
        "portrait.png" to "3/cNFBqBB4iId4d3d4iAjwj4hw==",
        "solid.png" to "HoUBBwB4eHeHd3hweId3h3h4B2+Ih4gA",
        "square.png" to "H/gNBxpwh4dwd3eIiHd3iHeHeJ+dcH8I",
        "tiny.png" to "HoU9tx4I9wiIh4hwj3CI+AiIcH/494cP",
    )

    @Test
    fun decodesAllNavidromeGoldenVectors() {
        for ((name, b64) in goldenVectors) {
            val bytes = ThumbHash.decodeBase64(b64)
            assertNotNull("Failed base64 decode for $name", bytes)
            val decoded = ThumbHash.decodeToRgba(bytes!!)
            assertNotNull("Failed RGBA decode for $name", decoded)
            decoded!!
            assertTrue("$name width should be in 1..32", decoded.width in 1..32)
            assertTrue("$name height should be in 1..32", decoded.height in 1..32)
            assertEquals(decoded.width * decoded.height * 4, decoded.rgba.size)
            assertEquals(decoded.width * decoded.height, decoded.argbPixels.size)
        }
    }

    @Test
    fun aspectRatiosMatchOrientation() {
        val landscape = ThumbHash.decodeBase64(goldenVectors.getValue("landscape.png"))!!
        val portrait = ThumbHash.decodeBase64(goldenVectors.getValue("portrait.png"))!!
        val square = ThumbHash.decodeBase64(goldenVectors.getValue("square.png"))!!

        assertTrue(ThumbHash.approximateAspectRatio(landscape) > 1.0f)
        assertTrue(ThumbHash.approximateAspectRatio(portrait) < 1.0f)
        assertEquals(1.0f, ThumbHash.approximateAspectRatio(square), 0.01f)
    }

    @Test
    fun parsesDominantColorHex() {
        assertEquals(0xFF2A4B7C.toInt(), ThumbHash.parseHexColor("#2a4b7c"))
        assertEquals(0xFFFF0055.toInt(), ThumbHash.parseHexColor("FF0055"))
        assertNull(ThumbHash.parseHexColor(""))
        assertNull(ThumbHash.parseHexColor("#12345"))
    }
}

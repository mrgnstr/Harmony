package com.mrgnstr.harmony.core.device

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualTierTest {
    private val gib = 1024L * 1024L * 1024L

    @Test fun lowRamFlagAlwaysMeansLow() =
        assertEquals(VisualTier.LOW, VisualTier.resolve(sdk = 34, isLowRam = true, totalMemBytes = 12 * gib, cores = 8))

    @Test fun smallMemoryIsLow() =
        assertEquals(VisualTier.LOW, VisualTier.resolve(sdk = 30, isLowRam = false, totalMemBytes = gib, cores = 8))

    @Test fun twoGbPhoneIsNotLow() =
        assertEquals(VisualTier.MID, VisualTier.resolve(sdk = 30, isLowRam = false, totalMemBytes = (1.8 * gib).toLong(), cores = 8))

    @Test fun fourGbTabletReportingLessIsHigh() =
        assertEquals(VisualTier.HIGH, VisualTier.resolve(sdk = 36, isLowRam = false, totalMemBytes = 3485904L * 1024, cores = 8))

    @Test fun threeGbPhoneIsMid() =
        assertEquals(VisualTier.MID, VisualTier.resolve(sdk = 34, isLowRam = false, totalMemBytes = (2.7 * gib).toLong(), cores = 8))

    @Test fun api25IsLow() =
        assertEquals(VisualTier.LOW, VisualTier.resolve(sdk = 25, isLowRam = false, totalMemBytes = 8 * gib, cores = 8))

    @Test fun fastModernPhoneIsHigh() =
        assertEquals(VisualTier.HIGH, VisualTier.resolve(sdk = 34, isLowRam = false, totalMemBytes = 8 * gib, cores = 8))

    @Test fun blurNeedsApi31ForHigh() =
        assertEquals(VisualTier.MID, VisualTier.resolve(sdk = 30, isLowRam = false, totalMemBytes = 8 * gib, cores = 8))

    @Test fun fewCoresCapAtMid() =
        assertEquals(VisualTier.MID, VisualTier.resolve(sdk = 34, isLowRam = false, totalMemBytes = 8 * gib, cores = 4))
}

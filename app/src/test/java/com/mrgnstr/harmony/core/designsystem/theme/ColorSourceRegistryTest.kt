package com.mrgnstr.harmony.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSourceRegistryTest {
    @Test fun inheritedColorIsAvailableBeforePageClaimsAndWhileLoading() {
        val registry = ColorSourceRegistry()
        val entry = "artist-info"
        val inherited = ColorSource.Seed(0xff556699.toInt())
        assertEquals(inherited, registry.sourceOf(entry, inherited))
        // PageColorSource(null) releases a claim while a fresh ViewModel loads.
        registry.release(entry)
        assertEquals(inherited, registry.sourceOf(entry, inherited))
    }

    @Test fun pageClaimOverridesInheritedColorAndUnseededPagesKeepWallpaper() {
        val registry = ColorSourceRegistry()
        val inherited = ColorSource.Seed(0xff556699.toInt())
        val updated = ColorSource.Seed(0xff995566.toInt())
        registry.claim("artist-info", updated)
        assertEquals(updated, registry.sourceOf("artist-info", inherited))
        assertEquals(ColorSource.Wallpaper, registry.sourceOf("library"))
    }
}

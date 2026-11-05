package com.mrgnstr.harmony

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import com.mrgnstr.harmony.core.artwork.ArtworkColorStore
import com.mrgnstr.harmony.core.database.HarmonyDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mrgnstr.harmony.core.data.ArtistImageStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the photo-picker save path without changing any real artist's image or logo. */
@RunWith(AndroidJUnit4::class)
class ArtistImageDeviceTest {
    @Test fun customImageAndTransparentLogoSurviveReloadAndCanBeCleared() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "Harmony M7 image test ${System.nanoTime()}"
        val source = File(context.cacheDir, "artist-image-test.png")
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
        for (y in 30 until 150) for (x in 20 until 100) bitmap.setPixel(x, y, Color.MAGENTA)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val store = ArtistImageStore(context)
        try {
            assertTrue(store.set(name, Uri.fromFile(source).toString()))
            assertTrue(store.setLogo(name, Uri.fromFile(source).toString()))
            val reloaded = ArtistImageStore(context)
            val photo = reloaded.images.value[name.uppercase()]!!
            val logo = reloaded.logos.value[name]!!
            assertEquals(1.5f, photo.ratio, 0.02f)
            // Transparent margins are removed from a logo; the remaining 80 x 120 image keeps PNG alpha.
            val decoded = android.graphics.BitmapFactory.decodeFile(logo.path)
            assertEquals(80, decoded.width)
            assertEquals(120, decoded.height)
            assertTrue(decoded.hasAlpha())
            store.clear(name)
            store.clearLogo(name)
            assertNull(ArtistImageStore(context).images.value[name])
            assertNull(ArtistImageStore(context).logos.value[name])
            assertFalse(File(photo.path).exists())
            assertFalse(File(logo.path).exists())
        } finally {
            store.clear(name)
            store.clearLogo(name)
            source.delete()
            bitmap.recycle()
        }
    }
    @Test fun photoPaletteFollowsReplacementAndIgnoresTransparentMargins() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "Harmony palette test ${System.nanoTime()}"
        val source = File(context.cacheDir, "artist-palette-test.png")
        val images = ArtistImageStore(context)
        val db = Room.inMemoryDatabaseBuilder(context, HarmonyDatabase::class.java).build()
        val colors = ArtworkColorStore(context, db.albumColorDao())
        fun writePhoto(color: Int) {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            for (y in 24 until 40) for (x in 24 until 40) bitmap.setPixel(x, y, color)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            writePhoto(Color.RED)
            assertTrue(images.set(name, Uri.fromFile(source).toString()))
            val redPath = images.images.value[name]!!.path
            val red = colors.accentForImage(redPath)!!
            assertTrue(Color.red(red) > Color.blue(red))
            writePhoto(Color.BLUE)
            assertTrue(images.set(name, Uri.fromFile(source).toString()))
            val bluePath = images.images.value[name]!!.path
            assertNotEquals(redPath, bluePath)
            val blue = colors.accentForImage(bluePath)!!
            assertTrue(Color.blue(blue) > Color.red(blue))
            assertNotEquals(red, blue)
            assertEquals(blue, colors.accentForImage(bluePath))
            images.clear(name)
            assertNull(images.images.value[name])
        } finally {
            images.clear(name)
            source.delete()
            db.close()
        }
    }
}

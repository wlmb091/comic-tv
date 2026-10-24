package com.pelican.comictv.reader

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ReaderTileCacheTest {
    @Test fun originalRegionRetainsNativePixelAndReusesDecodedBlock() = runBlocking {
        val file = File.createTempFile("reader-tile", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        val source = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        source.eraseColor(-1); source.setPixel(700, 700, 0xFF123456.toInt())
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }; source.recycle()
        val cache = ReaderTileCache(4 * 1024 * 1024)
        try {
            val key = TileKey(file, "test", TileRegion(512, 512, 1024, 1024, 1), ReaderImageOptions())
            val tile = cache.load(key)
            assertEquals(0xFF123456.toInt(), tile.bitmap.getPixel(700 - tile.region.left, 700 - tile.region.top))
            assertSame(tile.bitmap, cache.load(key).bitmap)
            assertFalse(tile.bitmap.isRecycled)
        } finally { cache.close(); file.delete() }
    }
    @Test fun vintageFilterDarkensInkWithoutWritingOriginalFile() = runBlocking {
        val file = File.createTempFile("reader-filter", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xFF606060.toInt())
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }; source.recycle()
        fun digest() = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val before = digest()
        val cache = ReaderTileCache(1024 * 1024)
        try {
            val tile = cache.load(TileKey(file, "test", TileRegion(0, 0, 64, 64, 1), ReaderImageOptions(ImagePreset.Vintage, 1)))
            assertTrue((tile.bitmap.getPixel(32, 32) and 255) < 96)
            assertArrayEquals(before, digest())
        } finally { cache.close(); file.delete() }
    }
}

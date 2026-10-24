package com.pelican.comictv.reader
import org.junit.Assert.*
import org.junit.Test

class ImageEnhancementTest {
    @Test fun originalIsExactAndDoesNotMutateInput() {
        val input = intArrayOf(0x80404040.toInt(), 0xFFFFFFFF.toInt(), 0xFF203040.toInt(), 0)
        val copy = input.copyOf()
        assertArrayEquals(copy, enhancePixels(input, 2, 2, ReaderImageOptions()))
        enhancePixels(input, 2, 2, ReaderImageOptions(ImagePreset.Vintage, 2))
        assertArrayEquals(copy, input)
    }
    @Test fun vintageDarkensWashedInkButPreservesWhitePaperAndAlpha() {
        val ink = IntArray(9) { 0x80606060.toInt() }
        val result = enhancePixels(ink, 3, 3, ReaderImageOptions(ImagePreset.Vintage, 1))
        assertEquals(0x80, result[4] ushr 24)
        assertTrue((result[4] and 255) < 96)
        val white = IntArray(9) { -1 }
        assertArrayEquals(white, enhancePixels(white, 3, 3, ReaderImageOptions(ImagePreset.Vintage, 2)))
    }
    @Test fun sharpeningIsBoundedAndUniformAreasStayUniform() {
        val uniform = IntArray(25) { 0xFFAAAAAA.toInt() }
        assertArrayEquals(uniform, enhancePixels(uniform, 5, 5, ReaderImageOptions(ImagePreset.Ink, 2)))
        val edge = uniform.copyOf().apply { this[12] = 0xFF505050.toInt() }
        val sharpened = enhancePixels(edge, 5, 5, ReaderImageOptions(ImagePreset.Ink, 2))
        assertTrue((sharpened[12] and 255) < 80)
        assertTrue((sharpened[12] and 255) >= 62)
    }
    @Test fun invalidStoredOptionsUseSafeOriginal() {
        assertEquals(ReaderImageOptions(), ReaderImageOptions.restore("unknown", -3))
        assertEquals(2, ReaderImageOptions.restore("Vintage", 99).intensity)
    }
}

package com.pelican.comictv.image
import org.junit.Assert.*
import org.junit.Test

class BackdropBlurTest {
    @Test fun uniformColorsAndAlphaArePreservedWithoutMutatingInput() {
        val source = IntArray(48) { 0x80604020.toInt() }
        val result = blurBackdropPixels(source, 8, 6, 3)
        assertArrayEquals(source, result)
        assertNotSame(source, result)
    }
    @Test fun brightDetailIsSoftenedAndSpreadIntoNeighbors() {
        val source = IntArray(81) { 0xFF000000.toInt() }.apply { this[40] = 0xFFFFFFFF.toInt() }
        val result = blurBackdropPixels(source, 9, 9, 1)
        assertTrue((result[40] and 255) in 1..254)
        assertTrue((result[39] and 255) > 0)
        assertTrue(result.all { (it ushr 24) == 255 })
        assertEquals(0xFFFFFFFF.toInt(), source[40])
    }
    @Test fun invalidOrOversizedImagesAreRejectedBeforeAllocation() {
        for ((w, h) in listOf(0 to 1, 1 to -1, 100_000 to 100_000, 300 to 300, 2 to 2)) {
            try {
                blurBackdropPixels(intArrayOf(0), w, h)
                fail("Invalid dimensions accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}

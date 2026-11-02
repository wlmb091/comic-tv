package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ImageAdjustmentsTest {
    private fun values(set: Map<Int, Int>) = List(ImageAdjustments.SIZE) { set[it] ?: 0 }
    // The same table is in tools/companion-ui.test.mjs: the phone preview and the TV must agree.
    private val vectors = listOf<Triple<Map<Int, Int>, FloatArray, FloatArray>>(
        Triple(mapOf(), floatArrayOf(0.95f, 0.9f, 0.7f), floatArrayOf(0.95f, 0.9f, 0.7f)),
        Triple(mapOf(2 to 100), floatArrayOf(0.1f, 0.1f, 0.1f), floatArrayOf(0.1519f, 0.1519f, 0.1519f)),
        Triple(mapOf(2 to -150), floatArrayOf(0.8f, 0.2f, 0.15f), floatArrayOf(0.499399f, 0.110347f, 0.077926f)),
        Triple(mapOf(0 to 60, 1 to -40), floatArrayOf(0.2f, 0.6f, 0.9f), floatArrayOf(0.229457f, 0.641785f, 0.768116f)),
        Triple(mapOf(3 to 70), floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f)),
        Triple(mapOf(3 to -50), floatArrayOf(0.3f, 0.75f, 0.3f), floatArrayOf(0.36f, 0.675f, 0.36f)),
        Triple(mapOf(4 to -60, 5 to 50), floatArrayOf(0.95f, 0.9f, 0.7f), floatArrayOf(0.927168f, 0.861703f, 0.650531f)),
        Triple(mapOf(6 to 40, 7 to -30), floatArrayOf(0.1f, 0.1f, 0.1f), floatArrayOf(0.030303f, 0.030303f, 0.030303f)),
        Triple(mapOf(8 to 60), floatArrayOf(0.8f, 0.2f, 0.15f), floatArrayOf(1f, 0.12563f, 0.04563f)),
        Triple(mapOf(8 to -100), floatArrayOf(0.2f, 0.6f, 0.9f), floatArrayOf(0.53662f, 0.53662f, 0.53662f)),
        Triple(mapOf(9 to 80), floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f)),
        Triple(mapOf(9 to -40), floatArrayOf(0.3f, 0.75f, 0.3f), floatArrayOf(0.428736f, 0.698736f, 0.428736f)),
        Triple(mapOf(10 to 50, 11 to 60, 12 to 30), floatArrayOf(0.95f, 0.9f, 0.7f), floatArrayOf(0.95f, 0.9f, 0.7f)),
        Triple(mapOf(19 to -70, 20 to 50, 21 to -40), floatArrayOf(0.1f, 0.1f, 0.1f), floatArrayOf(0.1f, 0.1f, 0.1f)),
        Triple(mapOf(25 to 100, 26 to -60, 27 to 80), floatArrayOf(0.8f, 0.2f, 0.15f), floatArrayOf(0.8f, 0.2f, 0.15f)),
        Triple(mapOf(0 to -30, 1 to 15, 2 to 35, 3 to 25, 4 to -20, 5 to 30, 6 to 20, 7 to -25, 8 to 10, 9 to 25, 10 to 20, 11 to 30, 16 to -15, 17 to 40, 18 to 10), floatArrayOf(0.2f, 0.6f, 0.9f), floatArrayOf(0.120988f, 0.683779f, 1f)),
    )
    @Test fun matchesThePhoneMath() {
        for ((set, input, expected) in vectors) {
            val out = ImageAdjustments.adjustRgb(values(set), input[0], input[1], input[2])
            for (i in 0..2) assertEquals("$set channel $i", expected[i], out[i], .001f)
        }
    }
    @Test fun zeroKeepsEveryPixelAndAlpha() {
        val pixels = intArrayOf(0x80C87828.toInt(), 0xFF03FA5A.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        val zero = ImageAdjustments()
        assertTrue(zero.identity)
        assertArrayEquals(pixels, zero.adjust(pixels))
        assertNotSame(pixels, zero.adjust(pixels))
    }
    @Test fun theTableFollowsTheDirectMathAndKeepsAlpha() {
        val tones = ImageAdjustments(values(mapOf(2 to 50, 3 to 30, 8 to 20, 16 to 40)))
        val pixel = 0x80C87828.toInt()
        val out = tones.adjust(intArrayOf(pixel))[0]
        val direct = ImageAdjustments.adjustRgb(tones.values, 200 / 255f, 120 / 255f, 40 / 255f)
        assertEquals(0x80, out ushr 24)
        assertTrue(abs(((out ushr 16) and 255) - direct[0] * 255) < 4f)
        assertTrue(abs(((out ushr 8) and 255) - direct[1] * 255) < 4f)
        assertTrue(abs((out and 255) - direct[2] * 255) < 4f)
    }
    @Test fun readsOnlyItsOwnFormat() {
        val tones = ImageAdjustments(values(mapOf(2 to -300, 33 to 100)))
        assertEquals(tones, ImageAdjustments.parse(tones.encode()))
        assertEquals(tones, ImageAdjustments.restore(tones.encode()))
        assertNull(ImageAdjustments.parse("2:" + tones.values.joinToString(",")))
        assertNull(ImageAdjustments.parse("1:1,2,3"))
        assertNull(ImageAdjustments.parse(tones.encode().replace("-300", "-301")))
        assertNull(ImageAdjustments.parse(tones.encode().replace("100", "101")))
        assertTrue(ImageAdjustments.restore("lixo").identity)
        assertTrue(ImageAdjustments.restore(null).identity)
    }
    @Test fun optionsAreKeptWithTheFilter() {
        val tones = ImageAdjustments(values(mapOf(3 to 10)))
        val options = ReaderImageOptions.restore("Vintage", 2, tones.encode())
        assertEquals(tones, options.adjustments); assertEquals(ImagePreset.Vintage, options.preset)
        assertEquals(tones, ReaderImageOptions.restore("unknown", 0, tones.encode()).adjustments)
        assertTrue(ReaderImageOptions().plain)
        assertFalse(ReaderImageOptions(adjustments = tones).plain)
        val input = intArrayOf(0xFF808080.toInt())
        assertNotEquals(input[0], enhancePixels(input, 1, 1, ReaderImageOptions(adjustments = ImageAdjustments(values(mapOf(2 to 100)))))[0])
    }
    @Test fun centerPanPutsThePagePointInTheMiddle() {
        assertEquals(0f, centerPan(.5f, 1f, .9f), 0f)
        assertEquals(0f, centerPan(1f, 2f, .5f), .0001f)
        assertEquals(1f, centerPan(1f, 2f, 1f), .0001f)
        assertEquals(-1f, centerPan(1f, 2f, .25f), .0001f)
        assertEquals(-.5f, centerPan(1f, 2f, .375f), .0001f)
        assertEquals(1f, centerPan(1f, 1.2f, .99f), .0001f)
    }
}

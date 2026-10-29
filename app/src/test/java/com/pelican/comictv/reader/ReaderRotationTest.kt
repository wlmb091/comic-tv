package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderRotationTest {
    @Test fun portraitSurfaceUsesEveryPixelWithoutScaling() {
        val placement = readerSurfaceGeometry(1920, 1080, true)
        assertEquals(1080, placement.width)
        assertEquals(1920, placement.height)
        assertEquals(420, placement.x)
        assertEquals(-420, placement.y)
        // Rotating this centered rectangle yields exactly [0,1920] x [0,1080].
        assertEquals(0f, placement.x + placement.width / 2f - placement.height / 2f, .001f)
        assertEquals(0f, placement.y + placement.height / 2f - placement.width / 2f, .001f)
    }

    @Test fun normalSurfaceAndOddPixelDimensionsRemainCentered() {
        assertEquals(ReaderSurfaceGeometry(1920, 1080, 0, 0), readerSurfaceGeometry(1920, 1080, false))
        val odd = readerSurfaceGeometry(1919, 1080, true)
        assertTrue(kotlin.math.abs((odd.x + odd.width / 2f) - 1919 / 2f) <= .5f)
        assertTrue(kotlin.math.abs((odd.y + odd.height / 2f) - 1080 / 2f) <= .5f)
    }

    @Test fun remoteDirectionsFollowClockwiseSurface() {
        assertEquals(ToolDirection.Left, readerLogicalDirection(ToolDirection.Up, true))
        assertEquals(ToolDirection.Up, readerLogicalDirection(ToolDirection.Right, true))
        assertEquals(ToolDirection.Right, readerLogicalDirection(ToolDirection.Down, true))
        assertEquals(ToolDirection.Down, readerLogicalDirection(ToolDirection.Left, true))
        ToolDirection.entries.forEach { assertEquals(it, readerLogicalDirection(it, false)) }
    }
}

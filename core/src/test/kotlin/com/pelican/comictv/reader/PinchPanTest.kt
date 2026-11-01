package com.pelican.comictv.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PinchPanTest {
    /** Where a page point lands in the view, using the same geometry as zoomPlacements. */
    private fun viewPoint(page: Float, fill: Float, zoom: Float, pan: Float): Float {
        val size = fill * zoom
        return (1f - size) / 2f - pan * ((size - 1f) / 2f).coerceAtLeast(0f) + page * size
    }

    @Test fun pointUnderTheFingersStaysThereWhileZooming() {
        val pan = pinchPan(1f, 2f, 2.5f, .1f, .8f, .8f)
        val size = 2f
        val page = (.8f - (1f - size) / 2f + .1f * ((size - 1f) / 2f)) / size
        assertEquals(.8f, viewPoint(page, 1f, 2.5f, pan), .001f)
    }

    @Test fun movingFingersDragTheImageAlongWithThem() {
        val before = viewPoint(.5f, 1f, 2f, 0f)
        val pan = pinchPan(1f, 2f, 2f, 0f, .4f, .5f)
        assertEquals(before + .1f, viewPoint(.5f, 1f, 2f, pan), .001f)
    }

    @Test fun zoomingFromFitTowardACornerRevealsThatCorner() {
        assertEquals(-1f, pinchPan(1f, 1f, 2f, 0f, 0f, 0f), .001f)
        assertEquals(1f, pinchPan(1f, 1f, 2f, 0f, 1f, 1f), .001f)
    }

    @Test fun anAxisWithoutOverflowCannotPanAndResultsStayBounded() {
        assertEquals(0f, pinchPan(.4f, 1f, 2f, 0f, .9f, .1f), .001f)
        assertEquals(0f, pinchPan(1f, 2f, 1f, .7f, .5f, .5f), .001f)
        assertEquals(1f, pinchPan(1f, 2f, 2f, 1f, .1f, .0f), .001f)
    }
}

package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class HeroCarouselPolicyTest {
    @Test fun elapsedClockWaitsTenSecondsAndNeverTicksWhilePaused() {
        var clockMs = 0L
        val started = clockMs
        clockMs = 9_999L
        assertFalse(HeroCarouselPolicy.due(clockMs - started, true, 3))
        clockMs = 10_000L
        assertTrue(HeroCarouselPolicy.due(clockMs - started, true, 3))
        assertFalse(HeroCarouselPolicy.due(clockMs - started, false, 3))
        assertFalse(HeroCarouselPolicy.due(clockMs - started, true, 1))
    }
    @Test fun stableIdsWrapAndRecoverFromRemoval() {
        val ids = listOf("a", "b", "c")
        assertEquals("b", HeroCarouselPolicy.next(ids, "a"))
        assertEquals("a", HeroCarouselPolicy.next(ids, "c"))
        assertEquals("b", HeroCarouselPolicy.current(ids, "removed", "b"))
        assertEquals("a", HeroCarouselPolicy.current(ids, "removed", "also removed"))
        assertNull(HeroCarouselPolicy.current(emptyList(), "a", "b"))
        assertNull(HeroCarouselPolicy.next(emptyList(), "a"))
        assertEquals("a", HeroCarouselPolicy.next(listOf("a"), "a"))
    }
    @Test fun reorderingDoesNotChangeTheSelectedEdition() {
        assertEquals("b", HeroCarouselPolicy.current(listOf("c", "a", "b"), "b", "a"))
    }
    @Test fun panAndMarkerUseOneBoundedTenSecondProgress() {
        assertEquals(0f, HeroCarouselPolicy.progress(-1L), 0f)
        assertEquals(.5f, HeroCarouselPolicy.progress(5_000L), 0f)
        assertEquals(1f, HeroCarouselPolicy.progress(12_000L), 0f)
        assertEquals(0f, HeroCarouselPolicy.verticalBias(0f), 0f)
        assertEquals(-.5f, HeroCarouselPolicy.verticalBias(HeroCarouselPolicy.progress(5_000L)), 0f)
        assertEquals(-1f, HeroCarouselPolicy.verticalBias(2f), 0f)
    }
    @Test fun markersStayBoundedAndAlwaysIncludeTheActiveEdition() {
        assertTrue(HeroCarouselPolicy.indicatorIndices(0, 0).isEmpty())
        assertEquals(listOf(0, 1, 2), HeroCarouselPolicy.indicatorIndices(3, 2))
        for (active in 0 until 100) {
            val markers = HeroCarouselPolicy.indicatorIndices(100, active)
            assertEquals(7, markers.size)
            assertTrue(active in markers)
            assertTrue(markers.all { it in 0 until 100 })
        }
    }
    @org.junit.Test fun zoomAndDynamicKeepImageCoveringViewport() {
        org.junit.Assert.assertEquals("Pan", HeroCarouselPolicy.motion("Dynamic", 0))
        org.junit.Assert.assertEquals("ZoomIn", HeroCarouselPolicy.motion("Dynamic", 1))
        org.junit.Assert.assertEquals("ZoomOut", HeroCarouselPolicy.motion("Dynamic", 2))
        for (style in listOf("None", "Pan", "ZoomIn", "ZoomOut")) for (step in 0..10) {
            val scale = HeroCarouselPolicy.zoom(style, step / 10f)
            org.junit.Assert.assertTrue(scale >= 1f && scale <= 1.08f)
        }
    }
}

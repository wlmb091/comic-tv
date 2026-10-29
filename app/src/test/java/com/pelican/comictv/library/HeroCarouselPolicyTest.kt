package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class HeroCarouselPolicyTest {
    @Test fun elapsedClockWaitsFiveSecondsAndNeverTicksWhilePaused() {
        var clockMs = 0L
        val started = clockMs
        clockMs = 4_999L
        assertFalse(HeroCarouselPolicy.due(clockMs - started, true, 3))
        clockMs = 5_000L
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
}

package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

class HeroCarouselPolicyTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun liveTimerSelectsNextEditionOnlyAfterEightSeconds() = kotlinx.coroutines.test.runTest {
        var selected: String? = "a"
        val timer = launch { selected = awaitHeroCarouselAdvance(listOf("a", "b"), selected, true) }
        runCurrent()
        advanceTimeBy(7_999)
        runCurrent()
        assertEquals("a", selected)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("b", selected)
        timer.join()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun pausingCancelsPendingAdvanceAndResumeWaitsFullInterval() = kotlinx.coroutines.test.runTest {
        var selected: String? = "a"
        val ids = listOf("a", "b")
        val pending = launch { selected = awaitHeroCarouselAdvance(ids, selected, true) }
        runCurrent()
        advanceTimeBy(6_000)
        pending.cancel()
        runCurrent()
        assertNull(awaitHeroCarouselAdvance(ids, selected, false))
        advanceTimeBy(20_000)
        assertEquals("a", selected)
        val resumed = launch { selected = awaitHeroCarouselAdvance(ids, selected, true) }
        runCurrent()
        advanceTimeBy(7_999)
        runCurrent()
        assertEquals("a", selected)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("b", selected)
        resumed.join()
        assertNull(awaitHeroCarouselAdvance(listOf("b"), "b", true))
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
}

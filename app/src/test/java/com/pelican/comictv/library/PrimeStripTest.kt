package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class PrimeStripTest {
    private fun comics(count: Int) = (1..count).map { ComicEntity("c$it", "Edição $it", 10) }

    @Test fun groupsOfThreeRotateAndWrapToTheFirst() {
        val groups = primeStripGroups(comics(9), 3)
        assertEquals(listOf(listOf("c1", "c2", "c3"), listOf("c4", "c5", "c6"), listOf("c7", "c8", "c9")), groups.map { g -> g.map { it.id } })
        assertEquals(1, primeStripNext(0, groups.size))
        assertEquals(2, primeStripNext(1, groups.size))
        assertEquals(0, primeStripNext(2, groups.size))
    }

    @Test fun anIncompleteLastGroupIsNotRotatedIn() {
        assertEquals(2, primeStripGroups(comics(8), 3).size)
        assertEquals(1, primeStripGroups(comics(5), 3).size)
    }

    @Test fun fewerThanOneGroupStaysStill() {
        for (count in 0..3) {
            val groups = primeStripGroups(comics(count), 3)
            assertTrue(groups.size <= 1)
            assertEquals(0, primeStripNext(0, groups.size))
        }
        assertEquals(2, primeStripGroups(comics(2), 3).single().size)
    }

    @Test fun narrowLayoutsUsePairsAndTheIntervalMatchesTheOtherCarousels() {
        assertEquals(listOf(2, 2, 2), primeStripGroups(comics(6), 2).map { it.size })
        assertEquals(HeroCarouselPolicy.INTERVAL_MS, PRIME_STRIP_INTERVAL_MS)
        assertEquals(8_000L, PRIME_STRIP_INTERVAL_MS)
    }
}

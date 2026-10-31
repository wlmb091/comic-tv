package com.pelican.comictv.ui

import org.junit.Assert.*
import org.junit.Test

class HomeViewport129Test {
    @Test fun flixHeroBudgetReservesTheReadingRowAt100And130Percent() {
        listOf(1f, 1.3f).forEach { scale ->
            val viewport = 524f
            val following = 24f * scale + 114f * scale + 8f
            val hero = homeHeroViewportBudget(viewport, viewport * .72f, 0f, following, 20f, 8f)
            assertTrue(hero + following + 20f + 8f + 20f <= viewport)
        }
    }

    @Test fun comicBrandAndFirstReadingRowAreOutsideTheHeroBudget() {
        val viewport = 524f
        val hero = homeHeroViewportBudget(viewport, viewport * .63f, 38f, 122f, 20f, 8f)
        assertEquals(306f, hero, .01f)
        assertTrue(hero + 38f + 10f + 122f + 20f + 8f + 20f <= viewport)
    }

    @Test fun maxReservesTheWholeExploreRowIncludingTwoLineCollectionNames() {
        listOf(1f, 1.3f).forEach { scale ->
            val viewport = 513f
            val explore = 12f + 24f * scale + 8f + 88f + 8f + 36f * scale + 8f
            val hero = homeHeroViewportBudget(viewport, viewport - 196f, 38f, explore, 20f, 0f, rowGapsDp = 10f)
            assertTrue(hero + 38f + 10f + explore + 20f + 10f <= viewport)
        }
    }

    @Test fun unusuallyTallTextCanGrowNaturallyWithoutANegativeHeroConstraint() {
        assertEquals(0f, homeHeroViewportBudget(240f, 200f, 50f, 300f, 20f, 8f), 0f)
    }

    @Test fun maxTwoLineTitleRespectsActualAvailableHeightEvenAboveLegacyMinimum() {
        val otherContent = 8f + 22f * 1.3f + (32f * 1.3f + 24f) + 4f + 12f
        val title = homeHeroTitleSizeSp(40f, 44f, 2, 1.3f, 194f, otherContent)
        assertTrue(title.first >= 24f)
        assertTrue(title.second * 2 * 1.3f + otherContent <= 194.01f)
        assertTrue(title.first < 40f)
    }
}

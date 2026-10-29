package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class HomePreviewPolicyTest {
    @Test fun visibleHeroActionDoesNotScrollTitleAway() {
        assertEquals(0f, homeBringIntoViewDistance(380f, 52f, 540f), 0f)
        assertEquals(32f, homeBringIntoViewDistance(520f, 52f, 540f), 0f)
        assertEquals(-20f, homeBringIntoViewDistance(-20f, 52f, 540f), 0f)
    }

    @Test fun headerFocusKeepsNonFocusableTitleAndPosterAtTop() {
        // Focusing an action near the viewport bottom must not align only that action.
        assertEquals(0f, homeHeaderBringIntoViewDistance(520f, 52f, 540f, true), 0f)
        assertEquals(0f, homeHeaderBringIntoViewDistance(-60f, 400f, 540f, true), 0f)
        // A real lower-row focus still receives the scroll needed to reveal its card.
        assertEquals(32f, homeHeaderBringIntoViewDistance(520f, 52f, 540f, false), 0f)
        assertEquals(-20f, homeHeaderBringIntoViewDistance(-20f, 52f, 540f, false), 0f)
    }

    @Test fun maxRecentPrecedesExplorerAndSourceRestoration() {
        assertEquals(2, libraryGridRecentRowIndex(explorer = true))
        assertEquals(3, homeExplorerRowIndex(30))
        assertEquals(1, homeExplorerRowIndex(0))
        assertEquals(5, libraryGridHeaderItems(false, 30, explorer = true))
        assertEquals(6, homeSourceRowIndex(30, explorer = true, latest = false))
        assertEquals(7, homeSourceRowIndex(30, explorer = true, latest = true))
        assertEquals(4, homeSourceRowIndex(0, explorer = true, latest = false))
        assertEquals(5, homeSourceRowIndex(30, explorer = false, latest = false))
    }

    @Test fun autoplayKeepsRunningWhileHeroIsVisibleAfterSmallFocusScroll() {
        assertTrue(homeHeroVisible(-28, 360, 0, 540))
        assertTrue(homeHeroVisible(-359, 360, 0, 540))
        assertFalse(homeHeroVisible(-360, 360, 0, 540))
        assertFalse(homeHeroVisible(540, 360, 0, 540))
        assertFalse(homeHeroVisible(0, 0, 0, 540))
    }

    @Test fun maxRowRestorationMatchesRenderedOrderWithAndWithoutRecent() {
        for (recentCount in listOf(0, 1, 12, 30)) {
            val rows = mutableListOf("hero")
            if (recentCount > 0) rows.addAll(listOf("recent-heading", "recent-row"))
            rows.addAll(listOf("explorer", "collection-heading", "collection-row", "catalog", "latest", "footer"))
            assertEquals(rows.indexOf("explorer"), homeExplorerRowIndex(recentCount))
            if (recentCount > 0) assertEquals(rows.indexOf("recent-row"), libraryGridRecentRowIndex(true))
            assertEquals(rows.indexOf("collection-row"), libraryGridHeaderItems(false, recentCount, true))
            assertEquals(rows.indexOf("catalog"), homeSourceRowIndex(recentCount, true, false))
            assertEquals(rows.indexOf("latest"), homeSourceRowIndex(recentCount, true, true))
            assertEquals(rows.indexOf("footer"), homeGridFooterIndex(recentCount, true))
        }
    }

    @Test fun comicSourceRowsFollowEveryCollectionCardAndFooterFollowsBothPreviews() {
        for (recentCount in listOf(0, 3)) for (count in listOf(0, 1, 24)) {
            val rows = mutableListOf("hero")
            if (recentCount > 0) rows.addAll(listOf("recent-heading", "recent-row"))
            rows.add("collection-heading")
            if (count == 0) rows.add("empty") else repeat(count) { rows.add("cover-$it") }
            rows.addAll(listOf("catalog", "latest", "footer"))
            assertEquals(rows.indexOf("catalog"), homeSourceRowIndex(recentCount, false, false, count))
            assertEquals(rows.indexOf("latest"), homeSourceRowIndex(recentCount, false, true, count))
            assertEquals(rows.indexOf("footer"), libraryGridFooterIndex(false, recentCount, count, sourceRows = true))
            assertEquals(rows.indexOf("catalog"), libraryGridFooterIndex(false, recentCount, count))
        }
    }

    @Test fun previewsAreBoundedAndDeduplicatedWithoutReordering() {
        assertEquals((0..11).toList(), homePreviewItems((0..30).toList() + 1) { it })
        assertEquals(listOf(2, 1), homePreviewItems(listOf(2, 2, 1)) { it })
    }
    @Test fun streamingFooterCountsRowsInsteadOfEveryCover() {
        assertEquals(8, homeGridFooterIndex(recentCount = 30, explorer = true))
        assertEquals(5, homeGridFooterIndex(recentCount = 0, explorer = false))
    }
}

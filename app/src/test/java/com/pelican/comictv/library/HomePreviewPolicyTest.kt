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

    @Test fun maxExplorerPrecedesRecentAndSourceRestoration() {
        assertEquals(3, libraryGridRecentRowIndex(explorer = true))
        assertEquals(5, libraryGridHeaderItems(false, 30, explorer = true))
        assertEquals(6, homeSourceRowIndex(30, explorer = true, latest = false))
        assertEquals(7, homeSourceRowIndex(30, explorer = true, latest = true))
        assertEquals(4, homeSourceRowIndex(0, explorer = true, latest = false))
        assertEquals(5, homeSourceRowIndex(30, explorer = false, latest = false))
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

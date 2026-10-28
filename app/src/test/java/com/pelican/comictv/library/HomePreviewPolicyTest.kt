package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class HomePreviewPolicyTest {
    @Test fun visibleHeroActionDoesNotScrollTitleAway() {
        assertEquals(0f, homeBringIntoViewDistance(380f, 52f, 540f), 0f)
        assertEquals(32f, homeBringIntoViewDistance(520f, 52f, 540f), 0f)
        assertEquals(-20f, homeBringIntoViewDistance(-20f, 52f, 540f), 0f)
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

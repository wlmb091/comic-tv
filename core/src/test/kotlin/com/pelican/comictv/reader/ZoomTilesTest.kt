package com.pelican.comictv.reader
import org.junit.Assert.*
import org.junit.Test

class ZoomTilesTest {
    @Test fun zoomRequestsOriginalPixelsInsteadOfScreenSizedCopy() {
        val page = zoomPlacements(listOf(ImageExtent(6000, 6000)), 1920, 1080, 3f, FitMode.Page, 0f, 0f).first()
        assertEquals(1, sampleSizeForScale(page.width / 6000))
        val tiles = visibleTiles(page, 1920, 1080)
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.right <= 6000 && it.bottom <= 6000 && it.left >= 0 && it.top >= 0 })
        assertTrue(tiles.maxOf { it.right } - tiles.minOf { it.left } < 6000)
    }
    @Test fun edgePanningClampsToPageAndChangesVisibleRegions() {
        val left = zoomPlacements(listOf(ImageExtent(2000, 2000)), 1000, 600, 3f, FitMode.Page, -1f, -1f).first()
        val right = zoomPlacements(listOf(ImageExtent(2000, 2000)), 1000, 600, 3f, FitMode.Page, 1f, 1f).first()
        assertEquals(0f, left.left, .01f)
        assertEquals(1000f, right.left + right.width, .01f)
        assertEquals(600f, right.top + right.height, .01f)
    }
    @Test fun spreadHasCorrectOrderAndSingleLastPageIsCentered() {
        val spread = zoomPlacements(listOf(ImageExtent(800, 1200), ImageExtent(800, 1200)), 1920, 1080, 1f, FitMode.Page, 0f, 0f, 4f)
        assertEquals(2, spread.size)
        assertEquals(4f, spread[1].left - (spread[0].left + spread[0].width), .01f)
        val single = zoomPlacements(listOf(ImageExtent(800, 1200)), 1920, 1080, 1f, FitMode.Page, 0f, 0f).single()
        assertEquals(960f, single.left + single.width / 2, .01f)
    }
    @Test fun sampleNeverDecodesBelowDisplayResolution() {
        listOf(.02f, .125f, .25f, .4f, .75f, 1f, 2f).forEach { scale ->
            val sample = sampleSizeForScale(scale)
            assertTrue(sample >= 1 && sample and (sample - 1) == 0)
            assertTrue(scale * sample <= 1f || sample == 1)
            assertTrue(scale * sample * 2 > 1f || sample == 128)
        }
    }
}

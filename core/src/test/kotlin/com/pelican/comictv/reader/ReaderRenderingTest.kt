package com.pelican.comictv.reader

import java.io.Closeable
import org.junit.Assert.*
import org.junit.Test

class ReaderRenderingTest {
    @Test fun fittedPageUsesOneBoundedRegionAtTheSameSamplingQuality() {
        for ((width, height) in listOf(1280 to 720, 720 to 1280)) {
            val page = zoomPlacements(listOf(ImageExtent(2000,3000)), width,height,1f,FitMode.Page,0f,0f).single()
            val original = visibleTiles(page,width,height)
            val plan = readerRenderRegions(page,width,height)
            assertTrue(original.size > 1)
            assertEquals(1,plan.size)
            assertEquals(original.first().sample,plan.single().sample)
            assertTrue((2000L/plan.single().sample)*(3000L/plan.single().sample)<=2_000_000)
        }
    }
    @Test fun largePageAndZoomStillUseOriginalResolutionTiles() {
        val page = zoomPlacements(listOf(ImageExtent(2000,3000)),1080,1920,1f,FitMode.Page,0f,0f).single()
        assertEquals(visibleTiles(page,1080,1920),readerRenderRegions(page,1080,1920))
        val zoom = zoomPlacements(listOf(ImageExtent(2000,3000)),720,1280,3f,FitMode.Page,0f,0f).single()
        assertEquals(visibleTiles(zoom,720,1280),readerRenderRegions(zoom,720,1280))
    }
    @Test fun smallMemoryBudgetFallsBackToTilesWithoutReducingQuality() {
        val page = zoomPlacements(listOf(ImageExtent(2000,3000)),720,1280,1f,FitMode.Page,0f,0f).single()
        assertEquals(visibleTiles(page,720,1280),readerRenderRegions(page,720,1280,500_000))
    }
    @Test fun fittedPageDoesNotSpeculativelyDecodeInternalBlocksAgain() {
        val page = zoomPlacements(listOf(ImageExtent(2000,3000)),720,1280,1f,FitMode.Page,0f,0f).single()
        assertTrue(readerNeighborRegions(page,720,1280).isEmpty())
    }
    @Test fun extremelyLongImagesStayInTextureSizedBlocksEvenWhenPixelCountIsSmall() {
        val page=zoomPlacements(listOf(ImageExtent(400,1_000_000)),720,1280,1f,FitMode.Page,0f,0f).single()
        assertEquals(visibleTiles(page,720,1280),readerRenderRegions(page,720,1280))
    }
    @Test fun decoderSessionsReuseResourcesAndCloseOnlyOnEvictionOrClear() {
        class Resource : Closeable { var closed=false; override fun close(){closed=true} }
        val cache=ReaderDecoderSessions<String,Resource>(2)
        var opens=0
        fun get(key:String)=cache.get(key){opens++;Resource()}
        val a=get("a");val b=get("b")
        assertSame(a,get("a"));assertEquals(2,opens)
        val c=get("c");assertTrue(b.closed);assertFalse(a.closed)
        cache.clear();assertTrue(a.closed);assertTrue(c.closed)
    }
}

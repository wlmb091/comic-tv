package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class ImageCachePolicyTest {
    @Test fun imageMemoryStaysBoundedAndLowRamDoesNotReceiveLargeCache() {
        assertEquals(32,imageMemoryMiB(192,false))
        assertEquals(42,imageMemoryMiB(256,false))
        assertEquals(48,imageMemoryMiB(1024,false))
        assertEquals(24,imageMemoryMiB(1024,true))
        assertEquals(8,imageMemoryMiB(48,false))
    }
    @Test fun coverWindowSkipsVisibleImagesAndClampsAtBothEnds() {
        assertEquals(listOf(10,11,12),coverWarmIndices(20,0,9,1,3))
        assertEquals(listOf(4,3,2),coverWarmIndices(20,5,14,-1,3))
        assertTrue(coverWarmIndices(5,0,4,1,3).isEmpty())
        assertTrue(coverWarmIndices(5,0,4,-1,3).isEmpty())
    }
}

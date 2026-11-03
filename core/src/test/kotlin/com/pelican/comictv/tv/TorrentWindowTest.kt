package com.pelican.comictv.tv

import org.junit.Assert.*
import org.junit.Test

class TorrentWindowTest {
    @Test fun entireLargeVideoStaysBelowThirtyTwoMiBPerWindow() {
        val size=1_516_383_963L
        for(piece in listOf(16_384,524_288,4*1024*1024,16*1024*1024)) {
            var next=0
            var covered=0L
            while(covered<size) {
                val window=TorrentWindow.range(0,size,piece,next)
                val bytes=(window.last-window.first+1).toLong()*piece
                assertTrue(bytes<=32L*1024*1024)
                assertTrue(bytes<500L*1024*1024)
                covered=(window.last+1L)*piece
                next=window.last+1
            }
        }
    }
    @Test fun boundaryPiecesAndSeekingRemainInsideSelectedFile() {
        val piece=524_288
        val offset=piece*3L+123
        val size=piece*120L+77
        val start=(offset/piece).toInt()
        val last=((offset+size-1)/piece).toInt()
        assertEquals(start,TorrentWindow.range(offset,size,piece,start).first)
        assertEquals(last..last,TorrentWindow.range(offset,size,piece,last))
        assertEquals(start,TorrentWindow.range(offset,size,piece,start).first)
        assertTrue(runCatching { TorrentWindow.range(offset,size,piece,last+1) }.isFailure)
        assertTrue(runCatching { TorrentWindow.range(offset,size,piece,start-1) }.isFailure)
    }
    @Test fun malformedSizesNeverWrapIntoAnotherFile() {
        assertTrue(runCatching { TorrentWindow.range(Long.MAX_VALUE,1,524_288,0) }.isFailure)
        assertTrue(runCatching { TorrentWindow.range(0,0,524_288,0) }.isFailure)
        assertTrue(runCatching { TorrentWindow.range(0,100,0,0) }.isFailure)
    }
}

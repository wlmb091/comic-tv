package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderStateTest {
    @Test fun `timeline selection is separate from reading and cancel preserves view`() {
        val original = ReaderState(page = 4, pageCount = 30, zoom = 2f, panX = .3f, panY = .5f)
        val timeline = original.reduce(ReaderAction.StartTimeline).reduce(ReaderAction.Right).reduce(ReaderAction.Down)
        assertEquals(4, timeline.page)
        assertEquals(15, timeline.timelinePage)
        val cancelled = timeline.reduce(ReaderAction.Back)
        assertEquals(original.copy(mode = ReaderMode.Tools), cancelled)
    }
    @Test fun `timeline confirmation resets viewport and respects reading bounds`() {
        val chosen = ReaderState(page = 4, pageCount = 30, zoom = 2f, panX = .3f)
            .reduce(ReaderAction.StartTimeline).reduce(ReaderAction.Down).reduce(ReaderAction.Select)
        assertEquals(14, chosen.page)
        assertEquals(ReaderMode.Reading, chosen.mode)
        assertEquals(1f, chosen.zoom)
        assertEquals(0f, chosen.panX)
        assertNull(chosen.timelinePage)
    }
    @Test fun `timeline clamps jumps and handles empty single and RTL books`() {
        for (count in listOf(0, 1, 3)) {
            var s = ReaderState(pageCount = count).reduce(ReaderAction.StartTimeline)
            repeat(10) { s = s.reduce(ReaderAction.Down) }
            assertEquals((count - 1).coerceAtLeast(0), s.timelinePage)
            repeat(10) { s = s.reduce(ReaderAction.Up) }
            assertEquals(0, s.timelinePage)
            assertEquals(0, s.reduce(ReaderAction.Select).page)
        }
        val rtl = ReaderState(page = 2, pageCount = 10, rtl = true).reduce(ReaderAction.StartTimeline)
        assertEquals(1, rtl.reduce(ReaderAction.Right).timelinePage)
        assertEquals(3, rtl.reduce(ReaderAction.Left).timelinePage)
    }
    @Test fun `page navigation is clamped and supports RTL`() {
        assertEquals(0, ReaderState(pageCount = 3).reduce(ReaderAction.Left).page)
        assertEquals(2, ReaderState(page = 2, pageCount = 3).reduce(ReaderAction.Right).page)
        assertEquals(0, ReaderState(page = 1, pageCount = 3, rtl = true).reduce(ReaderAction.Right).page)
    }
    @Test fun `zoom is clamped and page change preserves it at top`() {
        var state = ReaderState(pageCount = 3)
        repeat(20) { state = state.reduce(ReaderAction.Up) }
        assertEquals(3f, state.zoom)
        assertEquals(3f, state.reduce(ReaderAction.Right).zoom)
        assertEquals(-1f, state.reduce(ReaderAction.Right).panY)
        repeat(20) { state = state.reduce(ReaderAction.Down) }
        assertEquals(1f, state.zoom)
    }
    @Test fun `page turns keep zoom and open at source top in either reading direction`() {
        for (rtl in listOf(false,true)) {
            val state=ReaderState(page=3,pageCount=8,zoom=2f,panX=.5f,panY=1f,rtl=rtl)
            val next=state.reduce(if (rtl) ReaderAction.Left else ReaderAction.Right)
            assertEquals(4,next.page);assertEquals(2f,next.zoom);assertEquals(0f,next.panX);assertEquals(-1f,next.panY)
            for ((width,height) in listOf(1280 to 720,720 to 1280)) {
                val placement=zoomPlacements(listOf(ImageExtent(2000,3000)),width,height,next.zoom,next.fit,next.panX,next.panY).single()
                assertEquals(0f,placement.top,.01f)
            }
        }
    }
    @Test fun `page limits keep current viewport and normal scale stays centered`() {
        val first=ReaderState(pageCount=3,zoom=2f,panY=.5f)
        assertEquals(first,first.reduce(ReaderAction.Left))
        val next=ReaderState(pageCount=3).reduce(ReaderAction.Right)
        assertEquals(1f,next.zoom);assertEquals(0f,next.panY)
    }
    @Test fun `pan uses arrows until back and stays in bounds`() {
        var state = ReaderState(pageCount = 5, zoom = 2f).reduce(ReaderAction.StartPan)
        repeat(100) { state = state.reduce(ReaderAction.Right) }
        assertEquals(0, state.page)
        assertEquals(1f, state.panX)
        assertEquals(ReaderMode.Tools, state.reduce(ReaderAction.Back).mode)
        assertEquals(ReaderMode.Reading, state.reduce(ReaderAction.Back).reduce(ReaderAction.Back).mode)
    }
    @Test fun `normalizes restored progress and empty input`() {
        assertEquals(2, ReaderState.restore(99, 3).page)
        assertEquals(0, ReaderState.restore(-2, 0).page)
    }
}

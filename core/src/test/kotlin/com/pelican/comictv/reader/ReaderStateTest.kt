package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderStateTest {
    @Test fun `page navigation is clamped and supports RTL`() {
        assertEquals(0, ReaderState(pageCount = 3).reduce(ReaderAction.Left).page)
        assertEquals(2, ReaderState(page = 2, pageCount = 3).reduce(ReaderAction.Right).page)
        assertEquals(0, ReaderState(page = 1, pageCount = 3, rtl = true).reduce(ReaderAction.Right).page)
    }
    @Test fun `zoom is clamped and page change resets it`() {
        var state = ReaderState(pageCount = 3)
        repeat(20) { state = state.reduce(ReaderAction.Up) }
        assertEquals(3f, state.zoom)
        assertEquals(1f, state.reduce(ReaderAction.Right).zoom)
        repeat(20) { state = state.reduce(ReaderAction.Down) }
        assertEquals(1f, state.zoom)
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

package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class CenterPressControllerTest {
    @Test fun `short press is strictly below one second`() {
        for (duration in listOf(0L, 999L, 1000L, 1999L)) {
            val press = CenterPressController()
            assertTrue(press.down(10))
            assertEquals(if (duration < 1000) ReaderAction.TogglePan else null, press.up(10 + duration, ReaderMode.Reading))
        }
    }
    @Test fun `hold fires once at two seconds and release never selects a tool`() {
        val press = CenterPressController()
        press.down(10)
        assertNull(press.hold(2009))
        assertEquals(ReaderAction.OpenTools, press.hold(2010))
        assertNull(press.hold(3000))
        assertFalse(press.down(3000))
        assertTrue(press.isPressed)
        assertNull(press.up(3010, ReaderMode.Tools))
        assertFalse(press.isPressed)
    }
    @Test fun `delayed timer still opens tools on long release`() {
        val press = CenterPressController()
        press.down(0)
        assertEquals(ReaderAction.OpenTools, press.up(2000, ReaderMode.Reading))
        assertNull(press.hold(2100))
        val timeline = ReaderState(page = 2, pageCount = 20).reduce(ReaderAction.StartTimeline).reduce(ReaderAction.Down)
        assertEquals(2, timeline.reduce(ReaderAction.OpenTools).page)
        assertEquals(ReaderMode.Tools, timeline.reduce(ReaderAction.OpenTools).mode)
    }
    @Test fun `repeat does not reset duration and cancelled press cannot activate`() {
        val press = CenterPressController()
        press.down(0)
        assertFalse(press.down(900))
        assertNull(press.up(1100, ReaderMode.Reading))
        press.down(2000)
        press.cancel()
        assertNull(press.hold(4000))
        assertNull(press.up(4100, ReaderMode.Reading))
    }
    @Test fun `timeline short select commits while pan toggles without resetting view`() {
        val press = CenterPressController()
        press.down(0)
        assertEquals(ReaderAction.Select, press.up(100, ReaderMode.Timeline))
        val state = ReaderState(zoom = 2f, panX = .5f)
        val pan = state.reduce(ReaderAction.TogglePan)
        assertEquals(state.copy(mode = ReaderMode.Pan), pan)
        assertEquals(state, pan.reduce(ReaderAction.TogglePan))
    }
}

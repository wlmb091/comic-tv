package com.pelican.comictv.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserCursorTest {
    @Test fun arrowsMovePointerAndOnlyScrollAtViewportEdges() {
        assertEquals(CursorPosition(116f, 100f), moveBrowserCursor(100f, 100f, 16f, 0f, 640, 360, 24f))
        assertEquals(CursorPosition(100f, 336f, 0, 16), moveBrowserCursor(100f, 336f, 0f, 16f, 640, 360, 24f))
        assertEquals(CursorPosition(24f, 24f, -16, -16), moveBrowserCursor(24f, 24f, -16f, -16f, 640, 360, 24f))
    }
    @Test fun smallerViewportAndRepeatedMotionCannotEscapeTheVisiblePage() {
        var position = CursorPosition(320f, 180f)
        repeat(100) { position = moveBrowserCursor(position.x, position.y, 30f, 30f, 400, 220, 24f) }
        assertEquals(376f, position.x, .001f); assertEquals(196f, position.y, .001f)
        val tiny = moveBrowserCursor(0f, 0f, -100f, 100f, 20, 20, 24f)
        assertTrue(tiny.x.isFinite() && tiny.y.isFinite())
    }
}

package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class ChapterEndShortcutTest {
    private val last = ReaderState(page = 2, pageCount = 3)
    @Test fun appearsOnlyAtReadyChapterEndAndSupportsLastBookSpread() {
        assertTrue(chapterEndShortcutVisible(last, true, true))
        assertFalse(chapterEndShortcutVisible(last.copy(page = 1), true, true))
        assertTrue(chapterEndShortcutVisible(last.copy(page = 1), true, true, lastVisiblePage = 2))
        assertFalse(chapterEndShortcutVisible(last, false, true))
        assertFalse(chapterEndShortcutVisible(last, true, false))
        assertFalse(chapterEndShortcutVisible(ReaderState(), true, true))
    }
    @Test fun zoomPanAndMenusKeepTheirExistingControls() {
        for (state in listOf(last.copy(zoom = 1.25f), last.copy(mode = ReaderMode.Pan),
            last.copy(mode = ReaderMode.Tools), last.copy(mode = ReaderMode.Timeline))) {
            assertFalse(chapterEndShortcutVisible(state, true, true))
            assertFalse(focusChapterEndShortcut(state, ReaderAction.Right, visible = false))
        }
        assertEquals(1.25f, last.reduce(ReaderAction.Up).zoom)
        assertEquals(.15f, last.copy(mode = ReaderMode.Pan).reduce(ReaderAction.Right).panX)
    }
    @Test fun onlyExtraForwardAtEndRequestsFocusNotActivationForBothDirections() {
        assertTrue(focusChapterEndShortcut(last, ReaderAction.Right, visible = true))
        assertFalse(focusChapterEndShortcut(last, ReaderAction.Left, visible = true))
        assertFalse(focusChapterEndShortcut(last, ReaderAction.Select, visible = true))
        assertFalse(focusChapterEndShortcut(last, ReaderAction.Up, visible = true))
        assertTrue(focusChapterEndShortcut(last.copy(rtl = true), ReaderAction.Left, visible = true))
        assertFalse(focusChapterEndShortcut(last.copy(rtl = true), ReaderAction.Right, visible = true))
    }
}

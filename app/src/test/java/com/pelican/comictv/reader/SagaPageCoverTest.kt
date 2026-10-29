package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SagaPageCoverTest {
    @Test fun onlyDisplayedOriginalImageMayBeChosen() {
        val page = PageImage(File("original-page-1"), 1000, 1500, displayed = true)
        assertTrue(canUseSagaPageCover(page, false, false))
        assertSame(page.file, page.copy().file)
        assertFalse(canUseSagaPageCover(page, true, false))
        assertFalse(canUseSagaPageCover(page, false, true))
        assertFalse(canUseSagaPageCover(page.copy(displayed = false), false, false))
        assertFalse(canUseSagaPageCover(page.copy(text = "EPUB"), false, false))
        assertFalse(canUseSagaPageCover(null, false, false))
    }
}

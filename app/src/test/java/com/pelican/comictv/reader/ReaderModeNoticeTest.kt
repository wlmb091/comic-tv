package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderModeNoticeTest {
    @Test fun onlyExplicitZoomPanTransitionsHaveANotice() {
        assertTrue(readerModeNotice(ReaderMode.Reading, ReaderMode.Pan).orEmpty().contains("Mover página"))
        assertTrue(readerModeNotice(ReaderMode.Pan, ReaderMode.Reading).orEmpty().contains("Zoom"))
        for (from in ReaderMode.entries) for (to in ReaderMode.entries) {
            if ((from == ReaderMode.Reading && to == ReaderMode.Pan) || (from == ReaderMode.Pan && to == ReaderMode.Reading)) continue
            assertNull(readerModeNotice(from, to))
        }
    }
}

package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class CatalogEntryFocusTest {
    @Test fun titlesWinEvenWhileRefreshingAndStartAfterThreeHeaderItems() {
        assertEquals(CatalogEntryFocus.Title, catalogEntryFocus(10, busy = false, loaded = true))
        assertEquals(CatalogEntryFocus.Title, catalogEntryFocus(10, busy = true, loaded = true))
        assertEquals(3, CatalogEntryFocus.Title.firstIndex)
    }
    @Test fun folderCannotConsumeEntryBeforeFirstCatalogCompletes() {
        assertEquals(CatalogEntryFocus.Wait, catalogEntryFocus(0, busy = false, loaded = false))
        assertEquals(CatalogEntryFocus.Wait, catalogEntryFocus(0, busy = true, loaded = true))
        assertEquals(CatalogEntryFocus.Source, catalogEntryFocus(0, busy = false, loaded = true))
        assertEquals(0, CatalogEntryFocus.Source.firstIndex)
    }
}

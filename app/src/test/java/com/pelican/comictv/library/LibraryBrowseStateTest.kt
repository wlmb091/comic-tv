package com.pelican.comictv.library

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

class LibraryBrowseStateTest {
    @Test fun queryAndFilterRestoreAndOnlyMissingCollectionsReset() {
        val saved = SavedStateHandle()
        val state = LibraryBrowseState(saved)
        state.setQuery("  Ação  "); state.setFilter("collection:c1")
        val restored = LibraryBrowseState(saved)
        assertEquals("  Ação  ", restored.query.value)
        assertEquals("collection:c1", restored.filter.value)
        restored.reconcile(setOf("c1")); assertEquals("collection:c1", restored.filter.value)
        restored.reconcile(emptySet()); assertEquals("all", restored.filter.value)
        restored.setFilter("favorites"); restored.reconcile(emptySet())
        assertEquals("favorites", restored.filter.value)
        assertEquals("  Ação  ", restored.query.value)
    }
    @Test fun collectionDraftDoesNotMutateOriginalAndCancellationNeedsNoWrite() {
        val original = setOf("one")
        val selected = toggleCollection(original, "two")
        assertEquals(setOf("one"), original)
        assertEquals(setOf("one", "two"), selected)
        assertEquals(setOf("two"), toggleCollection(selected, "one"))
    }
}

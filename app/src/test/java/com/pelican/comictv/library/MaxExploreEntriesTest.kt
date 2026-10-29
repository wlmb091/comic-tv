package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class MaxExploreEntriesTest {
    private val comics = (0..15).map { ComicEntity("id-$it", "Obra $it", 20) }

    @Test fun collectionNamesArePreservedAndMissingMembershipDoesNotInventAnImage() {
        val entries = maxExploreEntries(comics,
            listOf(CollectionEntity("a", "Marvel", "marvel"), CollectionEntity("b", "Minha coleção", "minha coleção")),
            listOf(CollectionMembership("removed", "a"), CollectionMembership("id-2", "a")))
        assertEquals(listOf("Marvel", "Minha coleção"), entries.map { it.label })
        assertEquals("id-2", entries[0].comicId)
        assertNull(entries[1].comicId)
        assertEquals("b", entries[1].collectionId)
    }

    @Test fun fallbackIsBoundedActualBooksWithoutPublisherOrCharacterInference() {
        val entries = maxExploreEntries(comics, emptyList(), emptyList())
        assertEquals(12, entries.size)
        assertEquals("Obra 0", entries.first().label)
        assertTrue(entries.all { it.collectionId == null && it.comicId != null })
        assertEquals(emptyList<ExploreEntry>(), maxExploreEntries(emptyList(), emptyList(), emptyList()))
    }

    @Test fun maxHomeFooterOmitsHeadingAndCoversButHistoryAndOtherThemesKeepThem() {
        assertEquals(4, libraryGridFooterIndex(false, 3, 24, true, collectionVisible = false))
        assertEquals(2, libraryGridFooterIndex(false, 0, 0, true, collectionVisible = false))
        assertEquals(29, libraryGridFooterIndex(false, 3, 24, true))
        assertEquals(26, libraryGridFooterIndex(true, 3, 24))
    }

    @Test fun optionalExplorerDoesNotChangeRecentRowIndexAndMovesEveryCoverAndFooterOnce() {
        assertEquals(5, libraryGridHeaderItems(false, 3, true))
        assertEquals(2, libraryGridRecentRowIndex(true))
        assertEquals(2, libraryGridRecentRowIndex(false))
        assertEquals(3, libraryGridHeaderItems(false, 0, true))
        assertEquals(29, libraryGridFooterIndex(false, 3, 24, true))
        assertEquals(6, libraryGridFooterIndex(false, 3, 0, true))
        assertEquals(26, libraryGridFooterIndex(true, 3, 24, false))
    }
}

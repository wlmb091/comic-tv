package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class LibraryOrganizationTest {
    private val entries = listOf(LibrarySearchEntry("1", "Órbita — Edição 1", true),
        LibrarySearchEntry("2", "Orbita — Edição 2", false), LibrarySearchEntry("3", "Spawn", true))

    @Test fun searchCombinesAccentsCaseAndCollectionWithoutChangingOrder() {
        assertEquals(listOf("1", "2"), filterLibrary(entries, "  ORBITA  ", "all"))
        assertEquals(listOf("1"), filterLibrary(entries, "órbita", "favorites"))
        assertEquals(listOf("2"), filterLibrary(entries, "orbita", "collection:sci-fi", mapOf("sci-fi" to setOf("2", "3"))))
        assertEquals(emptyList<String>(), filterLibrary(entries, "missing", "all"))
        assertEquals(listOf("1", "2", "3"), filterLibrary(entries, " ", "all"))
    }
    @Test fun collectionNamesHaveStableNormalizedIdentityAndBounds() {
        assertEquals("Ação" to "acao", validatedCollectionName("  Ação  "))
        assertEquals("A".repeat(40), validatedCollectionName("A".repeat(40)).first)
        for (invalid in listOf("", "  ", "Todas", "FAVORÍTOS", "A".repeat(41), "bad\nname")) {
            assertThrows(IllegalArgumentException::class.java) { validatedCollectionName(invalid) }
        }
        assertEquals(normalizeLibraryText("AÇÃO"), normalizeLibraryText("acao"))
    }
}

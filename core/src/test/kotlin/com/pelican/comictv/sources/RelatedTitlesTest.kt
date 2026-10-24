package com.pelican.comictv.sources
import org.junit.Assert.*
import org.junit.Test

class RelatedTitlesTest {
    @Test fun explicitCreditsNormalizeAccentsAndCase() {
        val a = OnlineTitle("a", "A", author = "José Silva; Garth Ennis", artist = "Darick Robertson")
        val b = OnlineTitle("b", "B", author = "jose silva", artist = "Darick Robertson")
        assertEquals(listOf("Escritor: José Silva", "Desenhista: Darick Robertson"), relatedReasons(a, b, true))
    }
    @Test fun descriptionsAndPartialNamesNeverBecomeCredits() {
        val a = OnlineTitle("a", "A", description = "Garth Ennis", author = "Ennis")
        val b = OnlineTitle("b", "B", description = "Garth Ennis", author = "Garth Ennis")
        assertTrue(relatedReasons(a, b, true).isEmpty())
        assertTrue(relatedReasons(a.copy(author = "unknown"), b.copy(author = "unknown"), true).isEmpty())
    }
    @Test fun seriesIdentityIsScopedToSource() {
        val a = OnlineTitle("a", "Series")
        assertEquals(listOf("Mesma série"), relatedReasons(a, a, true))
        assertTrue(relatedReasons(a, a, false).isEmpty())
    }
    @Test fun otherRolesRequireExplicitMatchingValues() {
        val a = OnlineTitle("a", "A", publisher = "Editora X", characters = "Hero; Other", director = "Name")
        assertEquals(3, relatedReasons(a, a.copy(key = "b", characters = "Hero"), true).size)
        assertTrue(relatedReasons(a, OnlineTitle("c", "C"), true).isEmpty())
    }
}

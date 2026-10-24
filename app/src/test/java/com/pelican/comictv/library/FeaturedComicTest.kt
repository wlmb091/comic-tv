package com.pelican.comictv.library
import org.junit.Assert.*
import org.junit.Test

class FeaturedComicTest {
    @Test fun explicitFocusTakesPriorityWithoutChangingHistory() {
        val a = ComicEntity("a", "A", 10, lastRead = 100)
        val b = ComicEntity("b", "B", 10, hiddenFromHistory = true)
        assertEquals(b, featuredComic(listOf(a, b), "b"))
        assertTrue(b.hiddenFromHistory)
    }
    @Test fun fallbackUsesMostRecentVisibleReading() {
        val a = ComicEntity("a", "A", 10, lastRead = 100)
        val b = ComicEntity("b", "B", 10, lastRead = 200, hiddenFromHistory = true)
        val c = ComicEntity("c", "C", 10, lastRead = 150)
        assertEquals(c, featuredComic(listOf(a, b, c), "missing"))
    }
    @Test fun newAndEmptyLibrariesRemainUsable() {
        val a = ComicEntity("a", "A", 10)
        assertEquals(a, featuredComic(listOf(a), null))
        assertNull(featuredComic(emptyList(), null))
    }
}

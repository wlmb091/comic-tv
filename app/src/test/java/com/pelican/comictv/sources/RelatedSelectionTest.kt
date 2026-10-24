package com.pelican.comictv.sources
import org.junit.Assert.*
import org.junit.Test

class RelatedSelectionTest {
    private val source = SourceHandle("pkg", 1, "Source", "pt", "https://example.com")
    private fun entry(key: String, id: String? = null, reasons: List<String> = listOf("Escritor: Name")) =
        RelatedComic(id, source, OnlineTitle(key, key), "a".repeat(64), reasons)
    @Test fun excludesCurrentAndUnverifiedSuggestions() {
        val result = selectRelated("current", listOf(entry("a", "current"), entry("b", reasons = emptyList()), entry("c")))
        assertEquals(listOf("c"), result.map { it.title.key })
    }
    @Test fun deduplicatesSourceIdentityAndPrefersLibraryCopy() {
        val remote = entry("a")
        val local = entry("a", "saved").copy(source = source.copy(name = "Renamed"))
        assertEquals(listOf(local), selectRelated("current", listOf(remote, local)))
        assertEquals(2, selectRelated("current", listOf(remote, remote.copy(source = source.copy(id = 2)))).size)
    }
    @Test fun ranksExplicitSeriesFirstAndBoundsOutput() {
        val pool = (1..30).map { entry(it.toString()) } + entry("series", "saved", listOf("Mesma série"))
        val result = selectRelated("current", pool)
        assertEquals(18, result.size)
        assertEquals("series", result.first().title.key)
    }
}

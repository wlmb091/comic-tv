package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class OnlineEditionInfoTest {
    private fun record(description: String?) = OnlineReadingRecord(
        SourceHandle("pkg", 1, "HQ Now", "pt-BR", "https://example.com"), OnlineTitle("title", "Spawn", description = description),
        OnlineChapter("chapter", "#1 - Questões"), "a".repeat(64), listOf(OnlinePage(0, "", "https://example.com/page.jpg")))
    @Test fun preservesRealEditionMetadataAndTrimsSummary() {
        val info = record(" Resumo da fonte. ").editionInfo()
        assertEquals("HQ Now", info.sourceName)
        assertEquals("Spawn", info.title)
        assertEquals("#1 - Questões", info.chapter)
        assertEquals("Resumo da fonte.", info.description)
    }
    @Test fun missingSummaryStaysMissing() {
        assertNull(record(null).editionInfo().description)
        assertNull(record("  ").editionInfo().description)
    }
    @Test fun pathologicalSummaryIsBounded() {
        assertEquals(12_000, record("x".repeat(50_000)).editionInfo().description!!.length)
    }
}

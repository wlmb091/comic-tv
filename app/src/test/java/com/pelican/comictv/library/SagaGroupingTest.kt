package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class SagaGroupingTest {
    private val first = ComicEntity("first", "Infinity 1", 20, importedAt = 10)
    private val second = ComicEntity("second", "Infinity 2", 25, currentPage = 9, lastRead = 90, importedAt = 20)
    private val meta = mapOf("first" to SagaMetadata("source-series", "Infinity", "https://source/series.jpg", 1f),
        "second" to SagaMetadata("source-series", "Infinity", "https://source/series.jpg", 2f))
    private fun groups(comics: List<ComicEntity>, metadata: Map<String, SagaMetadata> = meta,
        settings: SagaSettings = SagaSettings()) = buildSagaGroups(comics, metadata, settings, emptyMap()) { "cover:$it" }

    @Test fun exactSourceAndTitleKeysSeparateHomonyms() {
        assertNotEquals(sagaIdentity("a", 1, "batman"), sagaIdentity("b", 1, "batman"))
        assertNotEquals(sagaIdentity("a", 1, "batman"), sagaIdentity("a", 2, "batman"))
        assertNotEquals(sagaIdentity("a", 1, "batman"), sagaIdentity("a", 1, "batman-2026"))
        assertNotEquals(sagaIdentity("a:1", 2, "3"), sagaIdentity("a", 1, "2:3"))
    }
    @Test fun readingAnotherChapterCannotChangeGroupCoverOrRepresentative() {
        val group = groups(listOf(second, first)).single()
        assertEquals("first", group.representative.id)
        assertEquals("https://source/series.jpg", group.cover)
        val reread = groups(listOf(first.copy(lastRead = 900), second)).single()
        assertEquals(group.cover, reread.cover)
        assertEquals(group.representative.id, reread.representative.id)
        assertEquals(9, group.editions.last().currentPage)
        assertEquals(90L, second.lastRead)
    }
    @Test fun chosenEditionCoverRemainsStableAcrossProgressAndInputOrder() {
        val settings = SagaSettings(covers = mapOf("source-series" to "first"))
        assertEquals("cover:first", groups(listOf(second, first), settings = settings).single().cover)
        assertEquals("cover:first", groups(listOf(first, second.copy(lastRead = 999)), settings = settings).single().cover)
    }
    @Test fun importsAreNeverGroupedByDisplayName() {
        val comics = listOf(first, second.copy(title = first.title))
        assertEquals(2, groups(comics, emptyMap()).size)
        val settings = SagaSettings(assignments = mapOf("first" to "manual:collection", "second" to "manual:collection"))
        assertEquals(1, groups(comics, emptyMap(), settings).size)
    }
    @Test fun deletedChosenEditionFallsBackToSeriesCoverWithoutMutatingProgress() {
        assertEquals("https://source/series.jpg", groups(listOf(second), settings =
            SagaSettings(covers = mapOf("source-series" to "first"))).single().cover)
        assertEquals(9, second.currentPage)
    }
    @Test fun chapterOrderUsesSourceNumbersRatherThanRecentReading() {
        assertEquals(listOf("first", "second"), groups(listOf(second, first)).single().editions.map { it.id })
    }

    @Test fun savedMetadataCacheReusesUnchangedRecordAndRefreshesChangedFile() = kotlinx.coroutines.runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("saga-metadata-test").toFile()
        try {
            val source = com.pelican.comictv.sources.SourceHandle("test.source", 1, "Test", "pt", "https://example.test")
            val title = com.pelican.comictv.sources.OnlineTitle("series-key", "Series A", "https://example.test/cover.jpg")
            val chapter = com.pelican.comictv.sources.OnlineChapter("chapter-key", "Chapter", 1f)
            val id = com.pelican.comictv.sources.OnlineIdentity.id(source, title, chapter)
            val record = com.pelican.comictv.sources.OnlineReadingRecord(source, title, chapter, "a".repeat(64), emptyList())
            val file = java.io.File(directory, "$id.json")
            file.writeBytes(com.pelican.comictv.sources.OnlineRecordCodec.encode(record))
            val comics = listOf(first.copy(id = id))
            val cache = linkedMapOf<String, SagaMetadataCacheEntry>()
            val initial = readSagaMetadata(directory, comics, cache).getValue(id)
            val unchanged = readSagaMetadata(directory, comics.map { it.copy(lastRead = 999) }, cache).getValue(id)
            assertSame(initial, unchanged)
            val oldModified = file.lastModified()
            file.writeBytes(com.pelican.comictv.sources.OnlineRecordCodec.encode(record.copy(title = title.copy(title = "Series B"))))
            assertTrue(file.setLastModified(oldModified + 5000))
            assertEquals("Series B", readSagaMetadata(directory, comics, cache).getValue(id).title)
            file.delete()
            assertTrue(readSagaMetadata(directory, comics, cache).isEmpty())
            assertTrue(cache.isEmpty())
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }

    @Test fun trueSagaWithoutMainCoverDoesNotUseAnEditionCoverImplicitly() {
        val noCover = meta.mapValues { (_, value) -> value.copy(coverUrl = null) }
        assertNull(groups(listOf(first, second), noCover).single().cover)
        val explicit = SagaSettings(covers = mapOf("source-series" to "second"))
        assertEquals("cover:second", groups(listOf(first, second), noCover, explicit).single().cover)
        assertEquals("cover:first", groups(listOf(first), emptyMap()).single().cover)
    }
}

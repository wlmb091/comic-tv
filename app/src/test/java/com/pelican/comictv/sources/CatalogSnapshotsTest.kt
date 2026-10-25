package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class CatalogSnapshotsTest {
    private val source = SourceHandle("pkg", 1, "Source", "pt", "https://example.com")
    private fun key(latest: Boolean = false, id: Long = 1) = CatalogSearchQuery(source.copy(id = id), "", latest)
    private fun result(key: String = "one") = CatalogResult(listOf(OnlineTitle(key, "HQ")), true)
    @Test fun populatedSameModeReentryStillConsultsFreshnessAndInitializationWaits() {
        val state = SourcesState(activeSource = source, titles = result().titles, catalogLoaded = true, latest = true)
        assertEquals(key(true), catalogQueryOnEntry(state, latest = true, initialLoading = false))
        assertEquals(key(false), catalogQueryOnEntry(state, latest = false, initialLoading = false))
        assertNull(catalogQueryOnEntry(state, latest = true, initialLoading = true))
    }
    @Test fun paginationKeepsMetadataResolvedWhileLoadingAndRejectsAnotherContext() {
        val state = SourcesState(activeSource = source, titles = listOf(OnlineTitle("one", "HQ", "resolved")))
        val response = CatalogResult(listOf(OnlineTitle("one", "HQ"), OnlineTitle("two", "HQ2")), false)
        val merged = appendCatalogPage(state, key(), response)
        assertEquals(listOf("one", "two"), merged.titles.map { it.key })
        assertEquals("resolved", merged.titles.first().coverUrl)
        assertSame(state, appendCatalogPage(state, key(true), response))
        assertSame(state, appendCatalogPage(state, key(id = 2), response))
    }
    @Test fun isolatesSourceModeAndPreservesPaginationUntilExpiry() {
        var now = 0L
        val cache = CatalogSnapshots(clockMillis = { now })
        cache.put(key(), result(), page = 3)
        cache.put(key(true), result("recent"))
        assertEquals(3, cache.get(key())!!.snapshot.page)
        assertNull(cache.get(key(id = 2)))
        now = 45_000
        assertFalse(cache.get(key(true))!!.fresh)
        assertTrue(cache.get(key())!!.fresh)
        now = 180_000
        assertFalse(cache.get(key())!!.fresh)
    }
    @Test fun evictsLeastRecentlyUsedAndRejectsOversizedSnapshots() {
        val cache = CatalogSnapshots(maxEntries = 2, maxTitles = 2, maxBytes = 3000)
        cache.put(key(id = 1), result())
        cache.put(key(id = 2), result())
        cache.get(key(id = 1))
        cache.put(key(id = 3), result())
        assertNull(cache.get(key(id = 2)))
        assertFalse(cache.put(key(id = 1), CatalogResult((1..3).map { OnlineTitle("$it", "HQ") }, false)))
        assertNull(cache.get(key(id = 1)))
        assertFalse(cache.put(key(id = 4), CatalogResult(listOf(OnlineTitle("big", "HQ", description = "a".repeat(5000))), false)))
        assertNull(cache.get(key(id = 4)))
    }
    @Test fun metadataUpdateDoesNotMakeOldCatalogFreshAndClearInvalidatesAll() {
        var now = 0L
        val cache = CatalogSnapshots(clockMillis = { now })
        cache.put(key(), result())
        now = 180_000
        cache.updateTitle(key(), OnlineTitle("one", "HQ", "cover"))
        assertEquals("cover", cache.get(key())!!.snapshot.titles.single().coverUrl)
        assertFalse(cache.get(key())!!.fresh)
        cache.clear()
        assertNull(cache.get(key()))
    }
}

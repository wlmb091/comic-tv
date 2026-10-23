package com.pelican.comictv.sources

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TitlePreviewResolverTest {
    private val source = SourceHandle("extension", 1, "Fonte", "pt", "https://example.com")
    @Test fun fillsMissingCoverAndReusesCachedDetails() = runTest {
        var calls = 0
        val resolver = TitlePreviewResolver { _, title -> calls++; title.copy(coverUrl = "https://example.com/cover.jpg") }
        val title = OnlineTitle("id", "Spawn")
        assertNotNull(resolver.resolve(source, title).coverUrl)
        assertNotNull(resolver.resolve(source, title).coverUrl)
        assertEquals(1, calls)
    }
    @Test fun preservesExistingCoverWithoutRequest() = runTest {
        val resolver = TitlePreviewResolver { _, _ -> error("Should not fetch") }
        val title = OnlineTitle("id", "Spawn", "https://example.com/cover.jpg")
        assertEquals(title, resolver.resolve(source, title))
    }
    @Test fun failuresCanBeRetried() = runTest {
        var calls = 0
        val resolver = TitlePreviewResolver { _, t -> if (++calls == 1) error("offline"); t.copy(coverUrl = "cover") }
        try { resolver.resolve(source, OnlineTitle("id", "HQ")); fail() } catch (_: IllegalStateException) { }
        assertEquals("cover", resolver.resolve(source, OnlineTitle("id", "HQ")).coverUrl)
    }
    @Test fun cacheIsScopedToSource() = runTest {
        var calls = 0
        val resolver = TitlePreviewResolver { _, t -> calls++; t.copy(coverUrl = "cover") }
        resolver.resolve(source, OnlineTitle("id", "HQ"))
        resolver.resolve(source.copy(id = 2), OnlineTitle("id", "HQ"))
        assertEquals(2, calls)
    }
    @Test fun limitsParallelRequestsAndPropagatesCancellation() = runTest {
        var active = 0; var peak = 0
        val resolver = TitlePreviewResolver { _, t ->
            active++; peak = maxOf(peak, active)
            try { delay(100); t.copy(coverUrl = "cover") } finally { active-- }
        }
        val jobs = (1..8).map { launch { resolver.resolve(source, OnlineTitle("$it", "HQ")) } }
        jobs.forEach { it.join() }
        assertEquals(2, peak)
        val cancelled = launch { resolver.resolve(source, OnlineTitle("cancel", "HQ")) }
        yield(); cancelled.cancelAndJoin()
        assertEquals(0, active)
    }
}

package com.pelican.comictv.sources

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TitlePreviewResolverTest {
    @Test fun refreshInvalidatesPositiveCacheAndOlderInFlightCompletion() = runTest {
        var calls = 0
        val released = CompletableDeferred<Unit>()
        val resolver = TitlePreviewResolver { _, title ->
            val call = ++calls
            if (call == 1) released.await()
            title.copy(coverUrl = "cover-$call")
        }
        val pending = async { resolver.resolve(source, OnlineTitle("same", "HQ")) }
        yield()
        resolver.clear()
        released.complete(Unit)
        pending.await()
        assertEquals("cover-2", resolver.resolve(source, OnlineTitle("same", "HQ")).coverUrl)
        resolver.clear()
        assertEquals("cover-3", resolver.resolve(source, OnlineTitle("same", "HQ")).coverUrl)
    }
    private val source = SourceHandle("extension", 1, "Fonte", "pt", "https://example.com")
    @Test fun concurrentSameTitleMakesOneRemoteCall() = runTest {
        var calls = 0
        val resolver = TitlePreviewResolver { _, title -> calls++; delay(100); title.copy(coverUrl = "cover") }
        val results = (1..8).map { async { resolver.resolve(source, OnlineTitle("same", "HQ")) } }.awaitAll()
        assertTrue(results.all { it.coverUrl == "cover" })
        assertEquals(1, calls)
    }
    @Test fun cancelledLeaderDoesNotStrandFollowerOrFutureRetry() = runTest {
        var calls = 0
        val started = CompletableDeferred<Unit>()
        val resolver = TitlePreviewResolver { _, title ->
            if (++calls == 1) { started.complete(Unit); awaitCancellation() }
            title.copy(coverUrl = "cover")
        }
        val first = launch { resolver.resolve(source, OnlineTitle("same", "HQ")) }
        started.await()
        val follower = async { resolver.resolve(source, OnlineTitle("same", "HQ")) }
        first.cancelAndJoin()
        assertEquals("cover", follower.await().coverUrl)
        assertEquals("cover", resolver.resolve(source, OnlineTitle("same", "HQ")).coverUrl)
        assertEquals(2, calls)
    }
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

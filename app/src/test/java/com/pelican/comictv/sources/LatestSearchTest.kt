package com.pelican.comictv.sources

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestSearchTest {
    @Test fun sourceTimeoutReportsFailureAndNextQueryStillPublishes() = runTest {
        val results = mutableListOf<String>(); val failures = mutableListOf<Throwable>()
        val search = LatestSearch<String, String>(this,
            fetch = { query -> sourceRequest(SourceRequest.Catalog, timeoutMillis = 20) {
                if (query == "slow") awaitCancellation() else query
            } }, publish = { _, value -> results += value }, failed = failures::add)
        search.submit("slow", immediate = true); advanceUntilIdle()
        assertEquals(1, failures.size); assertTrue(failures.single() is java.io.IOException)
        search.submit("ready", immediate = true); advanceUntilIdle()
        assertEquals(listOf("ready"), results)
        search.submit("slow", immediate = true); runCurrent()
        search.submit("replacement", immediate = true); advanceUntilIdle()
        assertEquals(1, failures.size)
        assertEquals(listOf("ready", "replacement"), results)
    }
    @Test fun typingOnlyRequestsLastQueryAfterPauseAndClearingAlsoSearches() = runTest {
        val calls = mutableListOf<String>()
        val results = mutableListOf<String>()
        val search = LatestSearch<String, String>(this, fetch = { calls += it; it }, publish = { _, value -> results += value }, failed = { throw it })
        search.submit("s"); advanceTimeBy(200); search.submit("sp"); advanceTimeBy(200); search.submit("spawn")
        advanceTimeBy(399); runCurrent(); assertTrue(calls.isEmpty())
        advanceTimeBy(1); runCurrent(); assertEquals(listOf("spawn"), calls)
        search.submit(""); advanceUntilIdle(); assertEquals(listOf("spawn", ""), results)
    }
    @Test fun staleUncooperativeFetchCannotPublishAndExplicitSearchSkipsDelay() = runTest {
        val results = mutableListOf<String>()
        val search = LatestSearch<String, String>(this, fetch = {
            if (it == "old") withContext(NonCancellable) { delay(1000) }; it
        }, publish = { _, result -> results += result }, failed = { throw it })
        search.submit("old", immediate = true); runCurrent()
        search.submit("new", immediate = true); runCurrent()
        assertEquals(listOf("new"), results)
        advanceUntilIdle(); assertEquals(listOf("new"), results)
    }
    @Test fun cancelStopsPendingSearchAndFailuresCanBeRetried() = runTest {
        var calls = 0; var errors = 0
        val search = LatestSearch<String, String>(this, fetch = { calls++; if (calls == 1) error("offline"); it },
            publish = { _, _ -> }, failed = { errors++ })
        search.submit("cancel"); search.cancel(); advanceUntilIdle(); assertEquals(0, calls)
        search.submit("retry"); advanceUntilIdle(); assertEquals(1, errors)
        search.submit("retry"); advanceUntilIdle(); assertEquals(2, calls)
    }
    @Test fun outgoingScreenCannotCancelIncomingOwnersQuery() = runTest {
        val old = Any(); val incoming = Any(); val results = mutableListOf<String>()
        val search = LatestSearch<String, String>(this, fetch = { it }, publish = { _, value -> results += value }, failed = { throw it })
        search.submit("old", owner = old); search.submit("new", owner = incoming)
        assertFalse(search.cancelFor(old))
        advanceUntilIdle(); assertEquals(listOf("new"), results)
        search.submit("cancel", owner = incoming); assertTrue(search.cancelFor(incoming))
        advanceUntilIdle(); assertEquals(listOf("new"), results)
    }
    @Test fun typingDuringModeSwitchAndClearingUseRequestedRecentMode() = runTest {
        val intent = CatalogSearchIntent()
        val source = SourceHandle("source", 1, "Source", "en", "https://example.com")
        val requests = mutableListOf<CatalogSearchQuery>()
        val search = LatestSearch<CatalogSearchQuery, String>(this,
            fetch = { requests += it; if (it.text.isEmpty() && !it.latest) delay(1000); it.text },
            publish = { _, _ -> }, failed = { throw it })
        search.submit(intent.capture(source, ""), immediate = true); runCurrent()
        intent.latest = true // Recent selected while Popular has not returned yet.
        search.submit(intent.capture(source, "spawn")); advanceUntilIdle()
        search.submit(intent.capture(source, "")); advanceUntilIdle()
        assertEquals(listOf(false, true, true), requests.map { it.latest })
        assertEquals(listOf("", "spawn", ""), requests.map { it.text })
    }
}

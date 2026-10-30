package com.pelican.comictv.search

import com.pelican.comictv.sources.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchCoverLoaderTest {
    private val source = SourceHandle("extension.cover", 1L, "Test", "pt", "https://example.com")
    private val image = File("ready.jpg")
    private val url = "https://example.com/cover.jpg"
    @Test fun ownTimeoutIsTerminalAndDoesNotMasqueradeAsScreenCancellation() = runTest {
        val loader = SearchCoverLoader(resolve = { _, _ -> awaitCancellation() }, fetch = { _, _, _ -> image },
            cached = { null }, timeoutMillis = 30, metadataTimeoutMillis = 20)
        assertEquals(SearchCoverFailure.Timeout, (loader.load(source, OnlineTitle("slow", "HQ")) as SearchCoverResult.Failed).reason)
    }
    @Test fun knownCoverDoesNotWaitForTwoSlowMetadataLookups() = runTest {
        val gate = CompletableDeferred<Unit>()
        val loader = SearchCoverLoader(resolve = { _, _ -> gate.await(); url }, fetch = { _, _, _ -> image }, cached = { null })
        val first = async { loader.load(source, OnlineTitle("one", "One")) }
        val second = async { loader.load(source, OnlineTitle("two", "Two")) }
        runCurrent()
        val ready = async { loader.load(source, OnlineTitle("fast", "Fast", url)) }
        runCurrent()
        assertTrue(ready.isCompleted)
        assertTrue(ready.await() is SearchCoverResult.Ready)
        gate.complete(Unit); first.await(); second.await()
    }
    @Test fun deadlineIncludesAdmissionQueueAndAllWaitersFinish() = runTest {
        val loader = SearchCoverLoader(resolve = { _, _ -> awaitCancellation() }, fetch = { _, _, _ -> image },
            cached = { null }, timeoutMillis = 30, metadataTimeoutMillis = 100)
        val results = (1..5).map { async { loader.load(source, OnlineTitle("$it", "HQ")) } }.awaitAll()
        assertTrue(results.all { it == SearchCoverResult.Failed(SearchCoverFailure.Timeout) })
        assertEquals(30L, testScheduler.currentTime)
    }
    @Test fun resolvedMetadataSurvivesCancelledImageAndIsNotFetchedAgain() = runTest {
        var metadata = 0; var slow = true
        val loader = SearchCoverLoader(resolve = { _, _ -> metadata++; url },
            fetch = { _, _, _ -> if (slow) awaitCancellation() else image }, cached = { null })
        val cancelled = launch { loader.load(source, OnlineTitle("one", "HQ")) }
        runCurrent(); cancelled.cancelAndJoin(); assertTrue(cancelled.isCancelled); slow = false
        assertTrue(loader.load(source, OnlineTitle("one", "HQ")) is SearchCoverResult.Ready)
        assertEquals(1, metadata)
    }
    @Test fun duplicatesShareWorkAndDiskHitSkipsNetwork() = runTest {
        var metadata = 0; var downloads = 0
        val files = mutableMapOf<String, File>()
        val loader = SearchCoverLoader(resolve = { _, _ -> metadata++; delay(10); url },
            fetch = { _, key, _ -> downloads++; delay(10); files[key] = image; image }, cached = { files[it] })
        val title = OnlineTitle("one", "HQ")
        val results = listOf(async { loader.load(source, title) }, async { loader.load(source, title) }).awaitAll()
        assertTrue(results.all { it is SearchCoverResult.Ready }); assertEquals(1, metadata); assertEquals(1, downloads)
        loader.load(source, title); assertEquals(1, downloads)
    }
    @Test fun failedOldAddressRefreshesMetadataOnceAndUsesReplacement() = runTest {
        val used = mutableListOf<String>(); var metadata = 0
        val loader = SearchCoverLoader(resolve = { _, _ -> metadata++; url }, fetch = { _, _, value ->
            used += value; if (value.endsWith("old.jpg")) throw IOException("stale address"); image
        }, cached = { null })
        assertTrue(loader.load(source, OnlineTitle("one", "HQ", "https://example.com/old.jpg")) is SearchCoverResult.Ready)
        assertEquals(listOf("https://example.com/old.jpg", url), used); assertEquals(1, metadata)
    }
    @Test fun unavailableCoversHaveCooldownButExplicitSearchCanRetry() = runTest {
        var calls = 0; var ready = false
        val loader = SearchCoverLoader(resolve = { _, _ -> calls++; if (ready) url else null },
            fetch = { _, _, _ -> image }, cached = { null }, clockMillis = { testScheduler.currentTime })
        val title = OnlineTitle("one", "HQ")
        assertEquals(SearchCoverResult.Failed(SearchCoverFailure.NoImage), loader.load(source, title))
        ready = true
        loader.load(source, title); assertEquals(1, calls)
        assertTrue(loader.load(source, title, attempt = 1) is SearchCoverResult.Ready); assertEquals(2, calls)
    }
    @Test fun openingDetailsSuppliesMissingCoverAndClearsNegativeCacheImmediately() = runTest {
        var calls = 0
        val loader = SearchCoverLoader(resolve = { _, _ -> calls++; null }, fetch = { _, _, _ -> image }, cached = { null })
        val title = OnlineTitle("one", "HQ")
        assertEquals(SearchCoverResult.Failed(SearchCoverFailure.NoImage), loader.load(source, title))
        loader.remember(source, listOf(title.copy(coverUrl = url)))
        assertTrue(loader.load(source, title) is SearchCoverResult.Ready)
        assertEquals(1, calls)
    }
    @Test fun cachedCatalogMetadataIsReusedWithoutPerTitleDetailsRequest() = runTest {
        var calls = 0
        val loader = SearchCoverLoader(resolve = { _, _ -> calls++; null }, fetch = { _, _, _ -> image }, cached = { null })
        loader.remember(source, listOf(OnlineTitle("one", "HQ", url)))
        assertTrue(loader.load(source, OnlineTitle("one", "HQ")) is SearchCoverResult.Ready)
        assertEquals(0, calls)
        loader.clear()
        assertEquals(SearchCoverResult.Failed(SearchCoverFailure.NoImage), loader.load(source, OnlineTitle("one", "HQ")))
    }
}

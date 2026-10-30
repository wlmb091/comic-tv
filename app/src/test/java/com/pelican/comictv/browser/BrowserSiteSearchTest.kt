package com.pelican.comictv.browser

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserSiteSearchTest {
    @Test fun explicitRetryRefreshesFailedQueryButReturningKeepsResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            var fail = true
            val model = BrowserSiteSearchModel { site, query ->
                calls++
                if (fail) throw java.io.IOException("offline")
                listOf(BrowserSiteHit(query, site.home))
            }
            model.search("Batman")
            advanceUntilIdle()
            assertEquals(6, calls)
            assertNotNull(model.results["sq"]?.error)
            model.search("Batman")
            advanceUntilIdle()
            assertEquals(6, calls)
            fail = false
            model.search("Batman", retry = 1)
            advanceUntilIdle()
            assertEquals(12, calls)
            assertEquals("Batman", model.results["sq"]?.hits?.single()?.title)
            model.search("Batman", retry = 1)
            advanceUntilIdle()
            assertEquals(12, calls)
            model.search("")
            advanceUntilIdle()
            assertTrue(model.results.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}

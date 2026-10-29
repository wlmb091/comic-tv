package com.pelican.comictv.search

import com.pelican.comictv.sources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchSessionTest {
    @Test fun returningAndPausingRetainsResultsUntilExplicitNewRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = SourceHandle("extension.test", 1L, "Test", "pt", "https://example.com")
            var calls = 0
            val runner = GlobalSearch({ _, query, _ -> calls++; listOf(OnlineTitle(query, query)) })
            val session = GlobalSearchSession()
            session.search("batman:0", listOf(source), "Batman", false, runner)
            advanceUntilIdle()
            assertEquals(1, calls)
            session.pause()
            session.search("batman:0", listOf(source), "Batman", false, runner)
            advanceUntilIdle()
            assertEquals(1, calls)
            assertEquals("Batman", session.batch.value.hits.single().title.title)
            session.search("batman:1", listOf(source), "Batman", false, runner)
            advanceUntilIdle()
            assertEquals(2, calls)
            session.search("superman:1", listOf(source), "Superman", false, runner)
            advanceUntilIdle()
            assertEquals("Superman", session.batch.value.hits.single().title.title)
            session.clear()
            assertTrue(session.batch.value.hits.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}

package com.pelican.comictv.search

import com.pelican.comictv.sources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchTest {
    private fun source(id: Int) = SourceHandle("extension.$id", id.toLong(), "Source $id", "pt", "https://example.com")

    @Test fun boundedFirstPagesPreserveSourceIdentityAndLimitConcurrency() = runTest {
        var active = 0; var peak = 0
        val calls = mutableListOf<Int>()
        val search = GlobalSearch({ source, query, latest ->
            assertEquals("Batman", query); assertFalse(latest)
            calls += source.id.toInt(); active++; peak = maxOf(peak, active)
            delay(10); active--
            (1..30).map { OnlineTitle("$it", "Title $it") }
        })
        val result = search.search((1..25).map(::source), "Batman", false).last()
        assertEquals(20, calls.size); assertEquals(2, peak)
        assertEquals(240, result.hits.size); assertEquals(240, result.hits.map { it.identity }.distinct().size)
        assertEquals(20, result.finished); assertFalse(result.running); assertTrue(result.limited)
    }

    @Test fun failureAndTotalBudgetKeepCompletedResultsWithoutWaitingForOtherSources() = runTest {
        val result = GlobalSearch({ source, _, _ ->
            when (source.id) {
                1L -> listOf(OnlineTitle("one", "Ready"))
                2L -> error("offline")
                else -> awaitCancellation()
            }
        }, budgetMillis = 100).search((1..4).map(::source), "", true).last()
        assertEquals("Ready", result.hits.single().title.title)
        assertEquals(1, result.errors.size); assertTrue(result.timedOut); assertFalse(result.running)
    }

    @Test fun cancelledUncooperativeSourceCannotPublishLateResults() = runTest {
        val results = mutableListOf<SearchBatch>()
        val search = GlobalSearch({ _, _, _ ->
            withContext(NonCancellable) { delay(100) }
            listOf(OnlineTitle("late", "Old query"))
        })
        val collector = launch { search.search(listOf(source(1)), "old", false).collect { results += it } }
        runCurrent(); collector.cancel(); advanceUntilIdle()
        assertTrue(results.all { it.hits.isEmpty() })
    }

    @Test fun unknownTitlesRemainInAllAndCategoriesRequireExplicitClassification() {
        assertTrue(categoryVisible(SearchCategory.ALL, null))
        assertFalse(categoryVisible(SearchCategory.MANGA, null))
        assertFalse(categoryVisible(SearchCategory.COMICS, SearchCategory.MANGA))
        assertTrue(categoryVisible(SearchCategory.MANGA, SearchCategory.MANGA))
    }

    @Test fun incompatibleExtensionIsAPartialFailureAndOtherSourcesComplete() = runTest {
        val result = GlobalSearch({ source, _, _ ->
            if (source.id == 1L) throw NoClassDefFoundError("missing API")
            listOf(OnlineTitle("ready", "Ready"))
        }).search(listOf(source(1), source(2)), "query", false).last()
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("incompatível"))
        assertEquals("Ready", result.hits.single().title.title)
        assertEquals(2, result.finished)
        assertFalse(result.running)
    }
}

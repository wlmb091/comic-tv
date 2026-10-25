package com.pelican.comictv.sources

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisibleTitlePreviewsTest {
    @Test fun keepsVisibleRequestsAcrossEmissionsAndCancelsOnlyLeavingTitles() = runTest {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val window = VisibleTitlePreviews(this) { title ->
            started += title.key
            try { awaitCancellation() } finally { cancelled += title.key }
        }
        val a = OnlineTitle("a", "A"); val b = OnlineTitle("b", "B")
        window.submit(listOf(a, b)); runCurrent()
        window.submit(listOf(a, b)); runCurrent()
        assertEquals(listOf("a", "b"), started)
        window.submit(listOf(b)); runCurrent()
        assertEquals(listOf("a"), cancelled)
        window.close(); runCurrent()
        assertEquals(setOf("a", "b"), cancelled.toSet())
    }
    @Test fun skipsExistingCoversAndDoesNotHammerFailedTitleUntilItReenters() = runTest {
        var calls = 0
        val window = VisibleTitlePreviews(this) { calls++ }
        val a = OnlineTitle("a", "A")
        window.submit(listOf(a, OnlineTitle("b", "B", "cover"))); runCurrent()
        window.submit(listOf(a)); runCurrent()
        assertEquals(1, calls)
        window.submit(emptyList()); window.submit(listOf(a)); runCurrent()
        assertEquals(2, calls)
        window.close()
    }
}

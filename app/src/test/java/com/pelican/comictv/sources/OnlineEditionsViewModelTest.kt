package com.pelican.comictv.sources

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineEditionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val first = OnlineChapter("1", "Primeira")
    private val second = OnlineChapter("2", "Segunda")
    private val context = OnlineEditions(SourceHandle("example.extension", 1, "Fonte", "pt", "https://example.com"),
        OnlineTitle("title", "Saga"), first, listOf(first, second), "a".repeat(64))
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun failedOpenKeepsListAndCanRetryWithoutDoubleOpening() = runTest {
        var attempts = 0
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, _ ->
            delay(10); if (++attempts == 1) error("Fonte indisponível"); "reader-id"
        })
        advanceUntilIdle()
        var destination: String? = null
        vm.open(second) { destination = it }
        vm.open(second) { fail("Second concurrent open") }
        advanceUntilIdle()
        assertEquals(1, attempts)
        assertNull(destination)
        assertEquals(context, vm.state.value.context)
        assertFalse(vm.state.value.busy)
        vm.open(second) { destination = it }
        advanceUntilIdle()
        assertEquals("reader-id", destination)
    }
    @Test fun browsingAndReversalNeverOpenAChapter() = runTest {
        var calls = 0
        var persisted: Boolean? = null
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, _ -> calls++; "id" },
            readOrder = { false }, writeOrder = { _, reversed -> persisted = reversed })
        advanceUntilIdle()
        vm.toggleOrder()
        advanceUntilIdle()
        assertTrue(vm.state.value.reversed)
        assertEquals(true, persisted)
        assertEquals(0, calls)
    }
    @Test fun cannotOpenChapterMissingFromRefreshedList() = runTest {
        var calls = 0
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, _ -> calls++; "id" })
        advanceUntilIdle()
        vm.open(OnlineChapter("missing", "Ausente")) { fail("Unknown chapter opened") }
        advanceUntilIdle()
        assertEquals(0, calls)
        assertNotNull(vm.state.value.message)
    }
    @Test fun leavingCancelsPendingOpenBeforeItCanNavigate() = runTest {
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, _ -> delay(1000); "late-reader" })
        advanceUntilIdle()
        var navigated = false
        vm.open(second) { navigated = true }
        runCurrent()
        vm.leave()
        advanceUntilIdle()
        assertFalse(navigated)
    }
}

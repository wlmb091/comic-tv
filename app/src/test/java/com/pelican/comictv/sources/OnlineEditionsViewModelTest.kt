package com.pelican.comictv.sources

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineEditionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val first = OnlineChapter("1", "Primeira", 1f)
    private val second = OnlineChapter("2", "Segunda", 2f)
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
        var persisted: ChapterOrder? = null
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, _ -> calls++; "id" },
            readOrder = { ChapterOrder.Ascending }, writeOrder = { _, order -> persisted = order })
        advanceUntilIdle()
        vm.selectOrder(ChapterOrder.Descending)
        advanceUntilIdle()
        assertEquals(ChapterOrder.Descending, vm.state.value.order)
        assertEquals(ChapterOrder.Descending, persisted)
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
    @Test fun nextShortcutOpensNeighborAndBoundaryKeepsList() = runTest {
        var opened: String? = null
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, chapter -> opened = chapter.key; "reader" })
        advanceUntilIdle()
        vm.openNeighbor(-1) { fail("Before first chapter") }
        assertNull(opened)
        assertNotNull(vm.state.value.message)
        vm.openNeighbor(1) { assertEquals("reader", it) }
        advanceUntilIdle()
        assertEquals(second.key, opened)
    }
    @Test fun neighborShortcutHonorsSavedReverseOrder() = runTest {
        var opened: String? = null
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, chapter -> opened = chapter.key; "reader" }, readOrder = { ChapterOrder.Descending })
        advanceUntilIdle()
        vm.openNeighbor(-1) { }
        advanceUntilIdle()
        assertEquals(second.key, opened)
    }
    @Test fun missingCurrentNeverChoosesAnUnrelatedChapter() = runTest {
        val missing = context.copy(current = OnlineChapter("gone", "Removed"))
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { missing }, { _, _ -> fail("Unrelated chapter opened"); "reader" })
        advanceUntilIdle()
        vm.openNeighbor(1) { fail("Unrelated navigation") }
        assertNotNull(vm.state.value.message)
    }
    @Test fun descendingSourceStillOpensChapterTwoFromChapterOneByDefault() = runTest {
        val one = OnlineChapter("one", "Chapter 01", 1f)
        val two = OnlineChapter("two", "Chapter 02", 2f)
        val ten = OnlineChapter("ten", "Chapter 10", 10f)
        val descending = context.copy(current = one, chapters = listOf(ten, two, one))
        var opened: String? = null
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { descending }, { _, chapter -> opened = chapter.key; "reader" })
        advanceUntilIdle()
        vm.openNeighbor(1) { assertEquals("reader", it) }
        advanceUntilIdle()
        assertEquals("two", opened)
        assertNull(vm.state.value.message)
    }
    @Test fun explicitSourceOrderPersistsAndIsUsedByShortcutsAfterReload() = runTest {
        var stored = ChapterOrder.Ascending
        val descending = context.copy(current = second, chapters = listOf(second, first))
        var opened: String? = null
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { descending }, { _, chapter -> opened = chapter.key; "reader" },
            readOrder = { stored }, writeOrder = { _, value -> stored = value })
        advanceUntilIdle(); vm.selectOrder(ChapterOrder.Source); advanceUntilIdle()
        assertEquals(ChapterOrder.Source, stored)
        vm.leave()
        val nextVm = OnlineEditionsViewModel("other-id", SavedStateHandle(), { descending }, { _, chapter -> opened = chapter.key; "reader" }, readOrder = { stored })
        advanceUntilIdle(); nextVm.openNeighbor(1) {}; advanceUntilIdle()
        assertEquals(first.key, opened)
    }
    @Test fun failedOrderWriteKeepsCurrentOrderAndDoesNotOpenAnything() = runTest {
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, _ -> error("No open") },
            writeOrder = { _, _ -> error("Disk full") })
        advanceUntilIdle(); vm.selectOrder(ChapterOrder.Descending); advanceUntilIdle()
        assertEquals(ChapterOrder.Ascending, vm.state.value.order)
        assertNotNull(vm.state.value.message); assertFalse(vm.state.value.busy)
    }
}

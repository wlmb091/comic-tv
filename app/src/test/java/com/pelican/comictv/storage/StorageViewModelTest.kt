package com.pelican.comictv.storage

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class StorageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun cleanupIsSerializedAndShowsPartialFailureWithFreshReport() = runTest(dispatcher) {
        var reads = 0; var clears = 0
        val vm = StorageViewModel(load = { reads++; StorageReport(9,7,if (clears == 0) 8 else 5,3,100) },
            cleanup = { clears++; delay(100); CacheCleanup(3,1) })
        advanceUntilIdle()
        vm.clearCaches(CacheSelection.Both); vm.clearCaches(CacheSelection.Both); vm.refresh()
        assertTrue(vm.state.value.busy)
        advanceUntilIdle()
        assertEquals(1, clears); assertEquals(2, reads)
        assertEquals(CacheCleanup(3,1), vm.state.value.cleanup)
        assertEquals(5L, vm.state.value.report!!.pages)
        assertFalse(vm.state.value.busy)
    }
    @Test fun failureDoesNotPublishSuccessfulCleanupAndCanRetry() = runTest(dispatcher) {
        var failing = true
        val vm = StorageViewModel(load = { StorageReport(0,0,5,0,100) }, cleanup = {
            if (failing) throw java.io.IOException("Falhou")
            CacheCleanup(5,0)
        })
        advanceUntilIdle(); vm.clearCaches(CacheSelection.Pages); advanceUntilIdle()
        assertNotNull(vm.state.value.error); assertNull(vm.state.value.cleanup)
        failing = false; vm.clearCaches(CacheSelection.Pages); advanceUntilIdle()
        assertNull(vm.state.value.error); assertEquals(5L, vm.state.value.cleanup!!.freedBytes)
    }
}

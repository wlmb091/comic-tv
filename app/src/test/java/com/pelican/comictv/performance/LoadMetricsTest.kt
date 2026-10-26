package com.pelican.comictv.performance

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

class LoadMetricsTest {
    @Test fun deferredDisplayRecordsElapsedWithoutStoringImageIdentity() {
        var time = 10_000_000L
        val metrics = LoadMetrics { time }
        val start = metrics.mark()
        time += 35_000_000
        metrics.finish(LoadStage.PageDisplay, start)
        time += 5_000_000
        metrics.finish(LoadStage.PageDisplay, time, failed = true)
        val sample = metrics.snapshot().getValue(LoadStage.PageDisplay)
        assertEquals(2L, sample.count); assertEquals(1L, sample.failed)
        assertEquals(35L, sample.maxMillis)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun cancelledWaitIsCountedOnceWithoutBeingARequestFailure() = runTest {
        var time = 0L
        val metrics = LoadMetrics { time }
        val job = launch { metrics.measure(LoadStage.PageQueue) { time += 15_000_000; awaitCancellation() } }
        runCurrent(); job.cancelAndJoin()
        val sample = metrics.snapshot().getValue(LoadStage.PageQueue)
        assertEquals(1L, sample.count); assertEquals(1L, sample.cancelled)
        assertEquals(0L, sample.failed); assertEquals(15L, sample.averageMillis)
    }
    @Test fun recordsElapsedFailureAndBoundedFixedStagesWithoutRequestData() = runTest {
        var time = 0L
        val metrics = LoadMetrics { time }
        assertEquals("done", metrics.measure(LoadStage.Catalog) { time += 20_000_000; "done" })
        try { metrics.measure(LoadStage.Catalog) { time += 40_000_000; error("private payload") } } catch (_: IllegalStateException) { }
        val sample = metrics.snapshot().getValue(LoadStage.Catalog)
        assertEquals(2L, sample.count)
        assertEquals(1L, sample.failed)
        assertEquals(30L, sample.averageMillis)
        assertEquals(40L, sample.maxMillis)
        repeat(100) { metrics.hit(LoadStage.CatalogMemory) }
        assertEquals(2, metrics.snapshot().size)
        metrics.clear()
        assertTrue(metrics.snapshot().isEmpty())
    }
}

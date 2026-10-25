package com.pelican.comictv.performance

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LoadMetricsTest {
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

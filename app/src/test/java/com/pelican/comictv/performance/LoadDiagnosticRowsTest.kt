package com.pelican.comictv.performance

import org.junit.Assert.*
import org.junit.Test

class LoadDiagnosticRowsTest {
    @Test fun everyStageRemainsVisibleInStableOrderEvenWithoutMeasurements() {
        val rows = loadDiagnosticRows(emptyMap())
        assertEquals(LoadStage.entries.toSet(), rows.map { it.stage }.toSet())
        assertEquals(LoadStage.entries.size, rows.size)
        assertEquals(listOf(LoadStage.PageMaterialize, LoadStage.PageQueue, LoadStage.RemoteImage), rows.take(3).map { it.stage })
        assertTrue(rows.all { it.detail == "Sem medições nesta sessão." })
    }
    @Test fun zeroElapsedMeasuredHitIsDifferentFromAnUnmeasuredStage() {
        val samples = mapOf(LoadStage.CatalogMemory to LoadSample(count = 1),
            LoadStage.RemoteImage to LoadSample(count = 3, cancelled = 1, totalNanos = 4_653_000_000, maxNanos = 3_156_000_000))
        val rows = loadDiagnosticRows(samples)
        assertTrue(rows.first { it.stage == LoadStage.CatalogMemory }.detail.contains("média 0 ms"))
        val remote = rows.first { it.stage == LoadStage.RemoteImage }.detail
        assertTrue(remote.contains("média 1551 ms")); assertTrue(remote.contains("maior 3156 ms"))
        assertTrue(remote.contains("1 cancelada(s)"))
        assertEquals(loadDiagnosticRows(emptyMap()).map { it.stage }, rows.map { it.stage })
    }
}

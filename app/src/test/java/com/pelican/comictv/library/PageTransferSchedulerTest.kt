package com.pelican.comictv.library

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageTransferSchedulerTest {
    @Test fun repeatedFailuresAndRapidCancellationsKeepTransfersBoundedAndUsable() = runTest {
        val scheduler = PageTransferScheduler()
        var active = 0; var maximum = 0
        val jobs = (0 until 200).map { index -> launch {
            try { scheduler.load("page-$index", index % 2 == 0, { null }) {
                active++; maximum = maxOf(maximum, active)
                try { delay(5); if (index % 7 == 0) throw java.io.IOException("offline"); "page" }
                finally { active-- }
            } } catch (_: java.io.IOException) { }
        } }
        runCurrent(); jobs.filterIndexed { index, _ -> index % 3 == 0 }.forEach { it.cancel() }
        withTimeout(5_000) { jobs.joinAll() }
        assertEquals(0, active); assertTrue(maximum <= 2)
        assertEquals("ready", scheduler.load("after-stress", true, { null }) { "ready" })
    }
    @Test fun queueDiagnosticsExcludeCacheHitsAndRecordCancelledPermitWait() = runTest {
        val metrics = com.pelican.comictv.performance.AppTimings
        val stage = com.pelican.comictv.performance.LoadStage.PageQueue
        val scheduler = PageTransferScheduler()
        val background = launch { scheduler.load("background", false, { null }) { awaitCancellation(); "never" } }
        val visible = launch { scheduler.load("visible", true, { null }) { awaitCancellation(); "never" } }
        runCurrent(); metrics.clear()
        try {
            assertEquals("hit", scheduler.load("cached", true, { "hit" }) { error("No fetch") })
            assertTrue(metrics.snapshot().isEmpty())
            val waiting = launch { scheduler.load("waiting", true, { null }) { error("No permit") } }
            runCurrent(); waiting.cancelAndJoin()
            val sample = metrics.snapshot().getValue(stage)
            assertEquals(1L, sample.count); assertEquals(1L, sample.cancelled)
            assertEquals(0L, sample.failed)
        } finally { background.cancelAndJoin(); visible.cancelAndJoin(); metrics.clear() }
    }
    @Test fun oldCancellationCannotUndoNewForegroundDemand() = runTest {
        val demoting = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
        var paused = false
        val scheduler = PageTransferScheduler {
            if (!paused) { paused = true; demoting.complete(Unit); resume.await() }
        }
        val releaseVisible = CompletableDeferred<Unit>()
        val blockerBg = launch { scheduler.load("block-bg", false, { null }) { awaitCancellation(); "never" } }
        val blockerFg = launch { scheduler.load("block-fg", true, { null }) { releaseVisible.await(); "done" } }
        runCurrent()
        var cache: String? = null
        val background = async { scheduler.load("target", false, { cache }) { "ready".also { cache = it } } }
        runCurrent()
        val old = async { scheduler.load("target", true, { cache }) { error("Duplicate transfer") } }
        runCurrent(); old.cancel(); runCurrent(); demoting.await()
        val incoming = async { scheduler.load("target", true, { cache }) { error("Duplicate transfer") } }
        try {
            runCurrent(); resume.complete(Unit); old.join()
            releaseVisible.complete(Unit); runCurrent()
            assertTrue("Current page lost foreground priority", incoming.isCompleted)
            assertEquals("ready", incoming.await())
        } finally {
            resume.complete(Unit)
            listOf(blockerBg, blockerFg, background, old, incoming).forEach { it.cancel() }
            runCurrent()
        }
    }
    @Test fun visiblePageAndCacheHitDoNotWaitForSlowBackgroundTransfer() = runTest {
        val scheduler = PageTransferScheduler()
        val bg = launch { scheduler.load("bg", false, { null }) { awaitCancellation(); "never" } }
        runCurrent()
        var secondStarted = false
        val queued = launch { scheduler.load("bg2", false, { null }) { secondStarted = true; "bg2" } }
        val current = async { scheduler.load("visible", true, { null }) { "visible" } }
        runCurrent()
        assertEquals("visible", current.await())
        assertFalse(secondStarted)
        assertEquals("cached", scheduler.load("cached", true, { "cached" }) { error("No download") })
        queued.cancelAndJoin(); bg.cancelAndJoin()
    }
    @Test fun sameImageSharesCompletedDownloadWithoutConsumingAnotherSlot() = runTest {
        val scheduler = PageTransferScheduler()
        val cache = mutableMapOf<String, String>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        suspend fun image() = scheduler.load("same", true, { cache["same"] }) {
            calls++; release.await(); "ready".also { cache["same"] = it }
        }
        val first = async { image() }; runCurrent()
        val duplicate = async { image() }
        val other = async { scheduler.load("other", true, { null }) { "other" } }
        runCurrent()
        assertTrue(other.isCompleted)
        release.complete(Unit)
        assertEquals(listOf("ready", "ready"), listOf(first.await(), duplicate.await()))
        assertEquals(1, calls)
    }
    @Test fun foregroundPromotesSameImageWaitingInBackgroundQueue() = runTest {
        val scheduler = PageTransferScheduler()
        val blocker = launch { scheduler.load("blocker", false, { null }) { awaitCancellation(); "never" } }
        runCurrent()
        var cached: String? = null
        var calls = 0
        val background = async { scheduler.load("next", false, { cached }) { calls++; "ready".also { cached = it } } }
        runCurrent()
        val foreground = async { scheduler.load("next", true, { cached }) { calls++; "unexpected" } }
        runCurrent()
        assertTrue(foreground.isCompleted)
        assertEquals("ready", foreground.await())
        assertEquals("ready", background.await())
        assertEquals(1, calls)
        blocker.cancelAndJoin()
    }
    @Test fun cancellationAndFailureReleaseQueueAndKeyForRetry() = runTest {
        val scheduler = PageTransferScheduler()
        val active = launch { scheduler.load("a", false, { null }) { awaitCancellation(); "never" } }
        runCurrent()
        val waiting = launch { scheduler.load("b", false, { null }) { error("Cancelled request ran") } }
        runCurrent(); waiting.cancelAndJoin(); active.cancelAndJoin()
        try { scheduler.load("retry", true, { null }) { error("offline") }; fail() } catch (_: IllegalStateException) { }
        assertEquals("ok", scheduler.load("retry", true, { null }) { "ok" })
        assertEquals("b", scheduler.load("b", false, { null }) { "b" })
    }
}

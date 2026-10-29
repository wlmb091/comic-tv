package com.pelican.comictv.sources

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SagaCoverCoordinatorTest {
    @Test fun duplicateWorkIsCoalescedAndDifferentWorksAreSerialized() = runTest {
        val coordinator = SagaCoverCoordinator(now = { testScheduler.currentTime })
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val first = launch { coordinator.ensure("sourceA:title") { calls++; release.await() } }
        yield()
        val duplicate = launch { coordinator.ensure("sourceA:title") { fail("Duplicate transfer") } }
        val second = launch { coordinator.ensure("sourceB:title") { calls++ } }
        yield()
        assertEquals(1, calls)
        release.complete(Unit)
        first.join(); duplicate.join(); second.join()
        assertEquals(2, calls)
    }

    @Test fun failuresCoolDownButCancellationAllowsImmediateReentry() = runTest {
        val coordinator = SagaCoverCoordinator(now = { testScheduler.currentTime }, cooldownMillis = 100)
        var calls = 0
        coordinator.ensure("failed") { calls++; error("Offline") }
        coordinator.ensure("failed") { calls++ }
        assertEquals(1, calls)
        testScheduler.advanceTimeBy(101)
        coordinator.ensure("failed") { calls++ }
        assertEquals(2, calls)
        val pending = launch { coordinator.ensure("cancelled") { awaitCancellation() } }
        yield(); pending.cancelAndJoin()
        coordinator.ensure("cancelled") { calls++ }
        assertEquals(3, calls)
    }

    @Test fun incomingReaderWaitsForCancelledDetailsCleanupAndTakesOver() = runTest {
        val coordinator = SagaCoverCoordinator(now = { testScheduler.currentTime })
        val cleanupEntered = CompletableDeferred<Unit>()
        val cleanupRelease = CompletableDeferred<Unit>()
        var cached = false
        var downloads = 0
        val outgoingDetails = launch {
            coordinator.ensure("same-source:title") {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) {
                    cleanupEntered.complete(Unit)
                    cleanupRelease.await()
                } }
            }
        }
        yield()
        outgoingDetails.cancel()
        cleanupEntered.await()
        val incomingReader = launch {
            coordinator.ensure("same-source:title") {
                if (!cached) { downloads++; cached = true }
            }
        }
        yield()
        assertFalse(incomingReader.isCompleted)
        assertEquals(0, downloads)
        cleanupRelease.complete(Unit)
        outgoingDetails.join(); incomingReader.join()
        assertTrue(cached)
        assertEquals(1, downloads)
    }

    @Test fun cancelledWaiterDoesNotCancelOwnersTransfer() = runTest {
        val coordinator = SagaCoverCoordinator(now = { testScheduler.currentTime })
        val release = CompletableDeferred<Unit>()
        var finished = false
        val owner = launch { coordinator.ensure("same") { release.await(); finished = true } }
        yield()
        val waiter = launch { coordinator.ensure("same") { fail("Duplicate transfer") } }
        yield()
        waiter.cancelAndJoin()
        release.complete(Unit)
        owner.join()
        assertTrue(finished)
    }

    @Test fun cacheAndUserChoiceAreRecheckedAfterWaitingForTransferSlot() = runTest {
        val coordinator = SagaCoverCoordinator(now = { testScheduler.currentTime })
        val release = CompletableDeferred<Unit>()
        var chosenOrCached = false
        var downloads = 0
        val first = launch { coordinator.ensure("one") { release.await() } }
        yield()
        val waiting = launch { coordinator.ensure("two") { if (!chosenOrCached) downloads++ } }
        yield()
        chosenOrCached = true
        release.complete(Unit)
        first.join(); waiting.join()
        assertEquals(0, downloads)
    }
}

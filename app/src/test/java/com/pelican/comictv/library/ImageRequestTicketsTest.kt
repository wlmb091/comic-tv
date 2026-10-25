package com.pelican.comictv.library

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class ImageRequestTicketsTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun cleanupCancelsObsoleteOperationWithoutCancellingItsParent() = runTest {
        val requests = ImageRequestTickets()
        val started = CompletableDeferred<ImageRequestTickets.Ticket>()
        val operation = async {
            runCatching { withContext(Dispatchers.Unconfined) {
                val ticket = requests.begin("one", currentCoroutineContext()[Job])
                started.complete(ticket)
                try { awaitCancellation() } finally { requests.finish(ticket) }
            } }
        }
        started.await()
        requests.invalidateAll()
        runCurrent()
        assertTrue(operation.await().isFailure)
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun deletionInvalidatesOnlyThatEditionAndNewRequestsAreIndependent() {
        val requests = ImageRequestTickets()
        val removed = requests.begin("one"); val other = requests.begin("other")
        requests.invalidate("one")
        try { requests.check(removed); fail() } catch (_: IOException) { }
        requests.check(other)
        val reopened = requests.begin("one")
        requests.check(reopened)
        requests.finish(removed); requests.finish(other); requests.finish(reopened)
    }
    @Test fun cleanupAndFinishingPreventLaterCommitButDoNotPoisonFutureLoads() {
        val requests = ImageRequestTickets()
        val before = requests.begin("one")
        requests.invalidateAll()
        try { requests.check(before); fail() } catch (_: IOException) { }
        val after = requests.begin("one")
        requests.check(after)
        requests.finish(after)
        try { requests.check(after); fail() } catch (_: IOException) { }
        requests.finish(before)
    }
}

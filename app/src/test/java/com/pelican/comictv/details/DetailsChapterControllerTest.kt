package com.pelican.comictv.details

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsChapterControllerTest {
    @Test fun leavingJoinsExtensionCancellationBeforeReaderCanStart() = runTest {
        val state = DetailsChapterController().apply { scope = this@runTest }
        val events = mutableListOf<String>()
        state.run {
            try { events += "loading"; awaitCancellation() }
            finally { withContext(NonCancellable) { delay(30); events += "extension stopped" } }
        }
        runCurrent()
        state.stop()
        events += "reader"
        assertEquals(listOf("loading", "extension stopped", "reader"), events)
        assertFalse(state.busy)
    }
    @Test fun retryJoinsPreviousWorkerAndCanResumeWithNewVisibleScope() = runTest {
        val state = DetailsChapterController().apply { scope = this@runTest }
        val events = mutableListOf<String>()
        state.run { try { awaitCancellation() } finally { events += "cancelled" } }
        runCurrent()
        state.run { events += "retry" }
        advanceUntilIdle()
        assertEquals(listOf("cancelled", "retry"), events)
        state.scope = null
        state.stop()
        state.run { fail("background source query") }
        state.scope = this
        state.run { events += "resumed" }
        advanceUntilIdle()
        assertEquals("resumed", events.last())
    }
}

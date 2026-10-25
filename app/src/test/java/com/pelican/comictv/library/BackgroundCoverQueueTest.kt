package com.pelican.comictv.library

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundCoverQueueTest {
    @Test fun boundsAndDeduplicatesWorkAndRunsOneGenerationAtATime() = runTest {
        val started = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val queue = BackgroundCoverQueue(this) { id, _ -> started += id; release.await() }
        assertTrue(queue.enqueue("one", File("unused")))
        assertFalse(queue.enqueue("one", File("unused")))
        assertTrue(queue.enqueue("two", File("unused")))
        assertFalse(queue.enqueue("three", File("unused")))
        runCurrent(); assertEquals(listOf("one"), started)
        release.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("one", "two"), started)
        assertTrue(queue.enqueue("three", File("unused")))
        advanceUntilIdle(); queue.close()
    }
    @Test fun optionalFailureAndCancellationReleaseCapacity() = runTest {
        var fail = true
        val queue = BackgroundCoverQueue(this) { _, _ -> if (fail) error("optional cover") else awaitCancellation() }
        queue.enqueue("one", File("unused")); advanceUntilIdle()
        fail = false
        assertTrue(queue.enqueue("one", File("unused"))); runCurrent()
        queue.close(); runCurrent()
        assertFalse(queue.enqueue("later", File("unused")))
    }
}

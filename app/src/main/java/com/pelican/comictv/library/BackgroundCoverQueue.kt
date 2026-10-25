package com.pelican.comictv.library

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Optional app-lifetime work: two jobs maximum, one generator, no service or new network work. */
internal class BackgroundCoverQueue(private val scope: CoroutineScope,
    private val render: suspend (String, File) -> Unit) {
    private val jobs = mutableMapOf<String, Job>()
    private val slot = Semaphore(1)
    private var closed = false
    fun enqueue(id: String, source: File): Boolean = synchronized(jobs) {
        if (closed || id in jobs || jobs.size >= 2) return@synchronized false
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { slot.withPermit { render(id, source) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* A missing optional cover must not fail reading. */ }
            finally { synchronized(jobs) { jobs.remove(id) } }
        }
        jobs[id] = job
        job.start()
        true
    }
    fun close() = synchronized(jobs) { closed = true; jobs.values.toList().forEach { it.cancel() } }
}

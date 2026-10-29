package com.pelican.comictv.sources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One optional transfer at a time. Waiters can take over a cancelled screen's unfinished work. */
internal class SagaCoverCoordinator(private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val cooldownMillis: Long = 5 * 60_000L) {
    private val lock = Mutex()
    private val transfer = Mutex()
    // The result is true only if the owner was cancelled and a live waiter should retry.
    private val active = hashMapOf<String, CompletableDeferred<Boolean>>()
    private val failed = linkedMapOf<String, Long>()
    suspend fun ensure(key: String, work: suspend () -> Unit) {
        while (true) {
            val (completion, owner) = lock.withLock {
                active[key]?.let { return@withLock it to false }
                if (failed[key]?.let { now() - it < cooldownMillis } == true) return
                val created = CompletableDeferred<Boolean>()
                active[key] = created
                created to true
            }
            if (!owner) {
                if (completion.await()) continue
                return
            }
            var cancelled = false
            try {
                transfer.withLock { work() }
                lock.withLock { failed.remove(key) }
            } catch (failure: CancellationException) {
                cancelled = true
                throw failure
            } catch (_: Exception) {
                lock.withLock {
                    if (failed.size >= 256) failed.remove(failed.keys.first())
                    failed[key] = now()
                }
            } finally {
                withContext(NonCancellable) {
                    lock.withLock {
                        active.remove(key)
                        completion.complete(cancelled)
                    }
                }
            }
            return
        }
    }
}

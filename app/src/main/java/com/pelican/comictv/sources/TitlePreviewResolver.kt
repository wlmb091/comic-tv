package com.pelican.comictv.sources

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Resolve only composed titles, with a bounded cache and respect for source request limits. */
internal class TitlePreviewResolver(private val fetch: suspend (SourceHandle, OnlineTitle) -> OnlineTitle) {
    private val slots = Semaphore(2)
    private val lock = Mutex()
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val gates = mutableMapOf<Pair<SourceHandle, String>, Gate>()
    private var generation = 0L
    private val cache = object : LinkedHashMap<Pair<SourceHandle, String>, OnlineTitle>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<SourceHandle, String>, OnlineTitle>?) = size > 128
    }
    suspend fun resolve(source: SourceHandle, title: OnlineTitle): OnlineTitle {
        if (!title.coverUrl.isNullOrBlank()) return title
        val key = source to title.key
        lock.withLock { cache[key] }?.let { return it }
        val (gate, expectedGeneration) = lock.withLock { gates.getOrPut(key) { Gate() }.also { it.users++ } to generation }
        try { return gate.mutex.withLock {
            lock.withLock { cache[key] } ?: slots.withPermit { fetch(source, title).let { details ->
                val resolved = details.copy(title = details.title.ifBlank { title.title },
                    coverUrl = details.coverUrl?.takeIf { it.isNotBlank() }, description = details.description ?: title.description)
                if (!resolved.coverUrl.isNullOrBlank()) lock.withLock {
                    if (generation == expectedGeneration) cache[key] = resolved
                }
                resolved
            } }
        } } finally {
            withContext(NonCancellable) { lock.withLock {
                gate.users--
                if (gate.users == 0 && gates[key] === gate) gates.remove(key)
            } }
        }
    }
    suspend fun clear() = lock.withLock { generation++; cache.clear() }
}

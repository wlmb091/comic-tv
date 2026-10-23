package com.pelican.comictv.sources

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Resolve only composed titles, with a bounded cache and respect for source request limits. */
internal class TitlePreviewResolver(private val fetch: suspend (SourceHandle, OnlineTitle) -> OnlineTitle) {
    private val slots = Semaphore(2)
    private val lock = Mutex()
    private val cache = object : LinkedHashMap<Pair<SourceHandle, String>, OnlineTitle>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<SourceHandle, String>, OnlineTitle>?) = size > 128
    }
    suspend fun resolve(source: SourceHandle, title: OnlineTitle): OnlineTitle {
        if (!title.coverUrl.isNullOrBlank()) return title
        val key = source to title.key
        lock.withLock { cache[key] }?.let { return it }
        return slots.withPermit {
            lock.withLock { cache[key] } ?: fetch(source, title).let { details ->
                val resolved = title.copy(title = details.title.ifBlank { title.title },
                    coverUrl = details.coverUrl?.takeIf { it.isNotBlank() }, description = details.description ?: title.description)
                if (!resolved.coverUrl.isNullOrBlank()) lock.withLock { cache[key] = resolved }
                resolved
            }
        }
    }
}

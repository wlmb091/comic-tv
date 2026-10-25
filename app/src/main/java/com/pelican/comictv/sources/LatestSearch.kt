package com.pelican.comictv.sources

import kotlinx.coroutines.*

/** A new edit invalidates the previous result immediately, including non-cooperative fetches. */
internal class LatestSearch<Q, R>(private val scope: CoroutineScope, private val waitMillis: Long = 400,
    private val fetch: suspend (Q) -> R, private val publish: (Q, R) -> Unit,
    private val failed: (Throwable) -> Unit) {
    private var job: Job? = null
    private var generation = 0L
    private var owner: Any? = null
    val isActive get() = job?.isActive == true
    fun submit(query: Q, immediate: Boolean = false, owner: Any? = null) {
        cancel()
        this.owner = owner
        val token = generation
        job = scope.launch {
            try {
                if (!immediate) delay(waitMillis)
                val result = fetch(query)
                ensureActive()
                if (generation == token) publish(query, result)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive && generation == token) failed(e) }
            catch (e: LinkageError) { if (isActive && generation == token) failed(e) }
        }
    }
    fun cancelFor(owner: Any): Boolean {
        if (this.owner !== owner || !isActive) return false
        cancel()
        return true
    }
    fun cancel() { generation++; job?.cancel(); job = null; owner = null }
}

internal data class CatalogSearchQuery(val source: SourceHandle, val text: String, val latest: Boolean)

/** Requested route mode is authoritative while a previous catalog response is still loading. */
internal class CatalogSearchIntent(var latest: Boolean = false) {
    fun capture(source: SourceHandle, text: String) = CatalogSearchQuery(source, text.trim().take(200), latest)
}

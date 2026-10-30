package com.pelican.comictv.search

import com.pelican.comictv.sources.*
import eu.kanade.tachiyomi.network.HttpException
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal enum class SearchCoverFailure(val label: String) {
    Timeout("Fonte lenta · tente Buscar novamente"), NoImage("Fonte sem capa"),
    Limited("Fonte limitando pedidos · tente mais tarde"), Forbidden("Fonte recusou a capa"),
    Missing("Capa não encontrada na fonte"), Network("Falha ao carregar capa · tente novamente"),
    Incompatible("Capa incompatível com esta extensão")
}
internal sealed interface SearchCoverResult {
    data class Ready(val file: File) : SearchCoverResult
    data class Failed(val reason: SearchCoverFailure) : SearchCoverResult
}
internal fun searchCoverKey(source: SourceHandle, titleKey: String): String = MessageDigest.getInstance("SHA-256")
    .digest("${source.packageName}:${source.id}:$titleKey".toByteArray()).let { bytes ->
        val hex = "0123456789abcdef"
        buildString(64) { bytes.forEach { byte -> val value = byte.toInt() and 255; append(hex[value ushr 4]); append(hex[value and 15]) } }
    }

/** Optional visible-thumbnail work. Metadata cannot monopolize the image-transfer slots. */
internal class SearchCoverLoader(
    private val resolve: suspend (SourceHandle, OnlineTitle) -> String?,
    private val fetch: suspend (SourceHandle, String, String) -> File,
    private val cached: suspend (String) -> File?,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val timeoutMillis: Long = 40_000,
    private val metadataTimeoutMillis: Long = 12_000,
    private val imageTimeoutMillis: Long = 12_000,
) {
    private val metadataSlots = Semaphore(2)
    private val imageSlots = Semaphore(3)
    private val lock = Mutex()
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)
    private val gates = mutableMapOf<String, Gate>()
    private data class Failure(val result: SearchCoverResult.Failed, val at: Long, val attempt: Long)
    private val urls = object : LinkedHashMap<String, String>(32, .75f, true) {
        override fun removeEldestEntry(entry: MutableMap.MutableEntry<String, String>?) = size > 512
    }
    private val failures = object : LinkedHashMap<String, Failure>(32, .75f, true) {
        override fun removeEldestEntry(entry: MutableMap.MutableEntry<String, Failure>?) = size > 256
    }
    private var generation = 0L
    @Synchronized fun remember(source: SourceHandle, titles: List<OnlineTitle>) {
        titles.forEach { title -> normalizeTitleCoverUrl(title.coverUrl)?.let { url ->
            val key = searchCoverKey(source, title.key)
            urls[key] = url; failures.remove(key)
        } }
    }
    @Synchronized fun clear() { generation++; urls.clear(); failures.clear() }

    suspend fun load(source: SourceHandle, title: OnlineTitle, attempt: Long = 0): SearchCoverResult {
        val key = searchCoverKey(source, title.key)
        val expectedGeneration = synchronized(this) { generation }
        var gate: Gate? = null
        val result = try {
            // Includes queue admission, not just the request that eventually wins a slot.
            withTimeout(timeoutMillis) {
                cached(key)?.let { return@withTimeout SearchCoverResult.Ready(it) }
                val acquired = lock.withLock { gates.getOrPut(key) { Gate() }.also { it.users++ } }
                gate = acquired
                acquired.mutex.withLock {
                    cached(key)?.let { return@withLock SearchCoverResult.Ready(it) }
                    synchronized(this@SearchCoverLoader) { failures[key] }?.let {
                        if (it.attempt == attempt && clockMillis() - it.at < 30_000) return@withLock it.result
                    }
                    var freshlyResolved = false
                    suspend fun metadata(): String? = withTimeout(metadataTimeoutMillis) {
                        metadataSlots.withPermit { normalizeTitleCoverUrl(resolve(source, title.copy(coverUrl = null))) }
                    }?.also { url -> synchronized(this@SearchCoverLoader) {
                        if (generation == expectedGeneration) urls[key] = url
                    } }
                    val url = synchronized(this@SearchCoverLoader) { urls[key] }
                        ?: normalizeTitleCoverUrl(title.coverUrl)
                        ?: metadata().also { freshlyResolved = true }
                        ?: return@withLock SearchCoverResult.Failed(SearchCoverFailure.NoImage)
                    suspend fun image(address: String) = withTimeout(imageTimeoutMillis) {
                        imageSlots.withPermit { fetch(source, key, address) }
                    }
                    var usedUrl = url
                    val file = try { image(url) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        currentCoroutineContext().ensureActive()
                        if (freshlyResolved || httpCode(failure) == 429) throw failure
                        val replacement = metadata()
                        if (replacement == null || replacement == url) throw failure
                        usedUrl = replacement
                        image(replacement)
                    }
                    synchronized(this@SearchCoverLoader) { if (generation == expectedGeneration) { urls[key] = usedUrl; failures.remove(key) } }
                    SearchCoverResult.Ready(file)
                }
            }
        } catch (failure: CancellationException) {
            // Our deadline is a displayed terminal state. Route/scroll cancellation must propagate.
            currentCoroutineContext().ensureActive()
            if (failure is TimeoutCancellationException) SearchCoverResult.Failed(SearchCoverFailure.Timeout) else throw failure
        } catch (_: LinkageError) {
            currentCoroutineContext().ensureActive(); SearchCoverResult.Failed(SearchCoverFailure.Incompatible)
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            SearchCoverResult.Failed(when (httpCode(failure)) {
                401, 403 -> SearchCoverFailure.Forbidden
                404, 410 -> SearchCoverFailure.Missing
                429 -> SearchCoverFailure.Limited
                else -> SearchCoverFailure.Network
            })
        } finally {
            gate?.let { current -> withContext(NonCancellable) { lock.withLock {
                current.users--; if (current.users == 0 && gates[key] === current) gates.remove(key)
            } } }
        }
        if (result is SearchCoverResult.Failed) synchronized(this) {
            if (generation == expectedGeneration) failures[key] = Failure(result, clockMillis(), attempt)
        }
        return result
    }
    private fun httpCode(error: Throwable): Int? {
        var cause: Throwable? = error
        repeat(8) { if (cause is HttpException) return (cause as HttpException).code; cause = cause?.cause }
        return null
    }
}

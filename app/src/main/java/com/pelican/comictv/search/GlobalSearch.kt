package com.pelican.comictv.search

import com.pelican.comictv.sources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SearchHit(val source: SourceHandle, val title: OnlineTitle) {
    val identity: String get() = "${source.packageName}:${source.id}:${title.key}"
}
data class SearchBatch(val hits: List<SearchHit> = emptyList(), val finished: Int = 0,
    val total: Int = 0, val errors: List<String> = emptyList(), val running: Boolean = false,
    val limited: Boolean = false, val timedOut: Boolean = false)

/** First page only. Child requests belong to the collector and never alter the active source. */
internal class GlobalSearch(private val fetch: suspend (SourceHandle, String, Boolean) -> List<OnlineTitle>,
    private val budgetMillis: Long = 90_000) {
    fun search(sources: List<SourceHandle>, query: String, latest: Boolean): Flow<SearchBatch> = channelFlow {
        val admitted = sources.distinctBy { it.packageName to it.id }.take(20)
        var snapshot = SearchBatch(total = admitted.size, running = admitted.isNotEmpty(), limited = sources.size > 20)
        send(snapshot)
        val permits = Semaphore(2)
        val publication = Mutex()
        val complete = withTimeoutOrNull(budgetMillis) {
            coroutineScope {
                admitted.forEach { source -> launch {
                    permits.withPermit {
                        var hits = emptyList<SearchHit>(); var failure: String? = null
                        try {
                            hits = fetch(source, query, latest).distinctBy { it.key }.take(12).map { SearchHit(source, it) }
                            currentCoroutineContext().ensureActive()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { failure = "${source.name}: ${error.message?.take(160) ?: "indisponível"}" }
                        catch (_: LinkageError) { failure = "${source.name}: extensão incompatível com esta versão." }
                        currentCoroutineContext().ensureActive()
                        publication.withLock {
                            snapshot = snapshot.copy(hits = snapshot.hits + hits, finished = snapshot.finished + 1,
                                errors = snapshot.errors + listOfNotNull(failure))
                            send(snapshot)
                        }
                    }
                } }
            }
            true
        } ?: false
        currentCoroutineContext().ensureActive()
        send(snapshot.copy(running = false, timedOut = !complete))
    }
}

enum class SearchCategory(val label: String) { ALL("Todos"), COMICS("Quadrinhos"), MANGA("Mangás") }

/** Categories are explicit user choices for this session, never guessed from title, cover or RTL. */
internal fun categoryVisible(category: SearchCategory, classification: SearchCategory?): Boolean =
    category == SearchCategory.ALL || classification == category

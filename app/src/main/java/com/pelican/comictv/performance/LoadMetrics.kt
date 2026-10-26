package com.pelican.comictv.performance

import kotlinx.coroutines.CancellationException

internal enum class LoadStage(val label: String) {
    Catalog("Consulta do catálogo"), CatalogMemory("Catálogo reaproveitado"), CoverMetadata("Informações de capa"),
    OpenChapter("Abertura do capítulo"), PageList("Lista de páginas"), PageMaterialize("Página atual"),
    PagePrefetch("Antecipação de página"), Thumbnail("Miniatura de página"), CoverCreation("Geração opcional da capa"),
    PageQueue("Espera por vaga de transferência"), RemoteImage("Imagem na fonte/rede"),
    CacheLookup("Consulta/preparo local do cache"), CacheCommit("Validação/gravação do cache"),
    PageCacheHit("Imagem reaproveitada do cache"), PagePrepare("Preparo da página atual"),
    PageDisplay("Até primeira exibição da página")
}
internal data class LoadSample(val count: Long = 0, val failed: Long = 0, val cancelled: Long = 0,
    val totalNanos: Long = 0, val maxNanos: Long = 0) {
    val averageMillis get() = if (count == 0L) 0L else totalNanos / count / 1_000_000
    val maxMillis get() = maxNanos / 1_000_000
}
/** Fixed enum keys and aggregates only: no request IDs, URLs, text or persisted records. */
internal class LoadMetrics(private val clockNanos: () -> Long = System::nanoTime) {
    private val samples = linkedMapOf<LoadStage, LoadSample>()
    suspend fun <T> measure(stage: LoadStage, block: suspend () -> T): T {
        val start = clockNanos()
        var failed = false; var cancelled = false
        try { return block() }
        catch (e: CancellationException) { cancelled = true; throw e }
        catch (e: Throwable) { failed = true; throw e }
        finally { record(stage, (clockNanos() - start).coerceAtLeast(0), failed, cancelled) }
    }
    @Synchronized private fun record(stage: LoadStage, nanos: Long, failed: Boolean, cancelled: Boolean) {
        val old = samples[stage] ?: LoadSample()
        samples[stage] = LoadSample(old.count + 1, old.failed + if (failed) 1 else 0,
            old.cancelled + if (cancelled) 1 else 0, old.totalNanos + nanos, maxOf(old.maxNanos, nanos))
    }
    fun hit(stage: LoadStage) = record(stage, 0, false, false)
    fun mark(): Long = clockNanos()
    fun finish(stage: LoadStage, startNanos: Long, failed: Boolean = false) =
        record(stage, (clockNanos() - startNanos).coerceAtLeast(0), failed, false)
    @Synchronized fun snapshot(): Map<LoadStage, LoadSample> = samples.toMap()
    @Synchronized fun clear() = samples.clear()
}
internal val AppTimings = LoadMetrics()

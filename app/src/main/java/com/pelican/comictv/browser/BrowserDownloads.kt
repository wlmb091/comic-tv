package com.pelican.comictv.browser

import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class BrowserDownloadStatus(val label: String) {
    Queued("Na fila"), Running("Baixando"), Paused("Pausado"), Complete("Concluído"),
    Failed("Falhou"), Canceled("Cancelado"), Importing("Importando"), Imported("Importado")
}
internal data class BrowserDownloadItem(
    val id: String, val url: String, val filename: String, val userAgent: String,
    val referer: String? = null, val validator: String? = null,
    val bytes: Long = 0, val total: Long = -1, val status: BrowserDownloadStatus = BrowserDownloadStatus.Queued,
    val error: String? = null, val importedId: String? = null, val mime: String? = null, val blobKey: String? = null,
)
internal typealias BrowserTransfer = suspend (BrowserDownloadRequest, File, (Long, Long) -> Unit, (BrowserTransferResult) -> Unit) -> BrowserTransferResult

/** One owned transfer, no service. Completed downloads are independent of the page/view lifecycle. */
internal class BrowserDownloads(private val scope: CoroutineScope, private val directory: File,
    restored: List<BrowserDownloadItem> = emptyList(), private val transfer: BrowserTransfer,
    private val cancelTransfer: () -> Unit = {}, private val persist: (List<BrowserDownloadItem>) -> Unit = {}) {
    private val state = MutableStateFlow(restored.take(100).filter { validId(it.id) }.map { row ->
        val stored = File(directory, "${row.id}.download")
        val bytes = if (stored.isFile) stored.length() else 0L
        when (row.status) {
            BrowserDownloadStatus.Running, BrowserDownloadStatus.Queued, BrowserDownloadStatus.Importing -> row.copy(status = BrowserDownloadStatus.Paused, bytes = bytes)
            BrowserDownloadStatus.Paused, BrowserDownloadStatus.Failed, BrowserDownloadStatus.Canceled -> row.copy(bytes = bytes)
            BrowserDownloadStatus.Complete, BrowserDownloadStatus.Imported -> if (stored.isFile) row.copy(bytes = bytes)
                else row.copy(status = BrowserDownloadStatus.Failed, bytes = 0, error = "O arquivo deste download não está mais disponível. Baixe novamente para recuperar a cópia do navegador.")
        }
    })
    val items = state.asStateFlow()
    @Volatile private var serial = 0L
    private var worker: Job? = null
    private var runningId: String? = null
    private var foreground = true
    init { directory.mkdirs() }
    fun file(id: String): File { require(validId(id)); return File(directory, "$id.download") }
    private fun save() = persist(state.value)
    private fun edit(id: String, change: (BrowserDownloadItem) -> BrowserDownloadItem) = state.update { list -> list.map { if (it.id == id) change(it) else it } }
    fun enqueue(request: BrowserDownloadRequest): String? {
        if (request.url.startsWith("blob:")) require(browserBlobOrigin(request.url) != null) else BrowserPolicy.url(request.url)
        if (state.value.size >= 100 || state.value.count { it.status in setOf(BrowserDownloadStatus.Queued, BrowserDownloadStatus.Running) } >= 12) return null
        val id = UUID.randomUUID().toString()
        state.update { it + BrowserDownloadItem(id, request.url, BrowserDownloadNames.safe(request.suggestedName), request.userAgent.take(512), request.referer?.take(4096), blobKey = request.blobKey) }
        save(); pump(); return id
    }
    private fun pump() {
        if (!foreground || !scope.isActive || worker?.isCompleted == false) return
        val item = state.value.firstOrNull { it.status == BrowserDownloadStatus.Queued } ?: return
        val token = ++serial; runningId = item.id
        edit(item.id) { it.copy(status = BrowserDownloadStatus.Running, error = null) }
        worker = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val target = file(item.id)
                val used = directory.listFiles()?.filter { it.name.endsWith(".download") && it != target }?.sumOf { it.length() } ?: 0L
                val limit = (2L * 1024 * 1024 * 1024 - used).coerceAtMost(BrowserPolicy.MAX_BYTES)
                check(limit > 0) { "Limite de 2 GiB dos downloads atingido. Exclua arquivos concluídos para liberar espaço." }
                var notifiedAt = 0L
                val result = transfer(BrowserDownloadRequest(item.url, item.filename, item.userAgent, item.referer, item.validator, limit, item.blobKey), target,
                    { bytes, total ->
                        val now = System.nanoTime()
                        if (token == serial && (bytes == total || now - notifiedAt > 200_000_000)) {
                            notifiedAt = now; edit(item.id) { if (it.status == BrowserDownloadStatus.Running) it.copy(bytes = bytes, total = total) else it }
                        }
                    }, { info -> if (token == serial) {
                        edit(item.id) { if (it.status == BrowserDownloadStatus.Running) it.copy(filename = info.filename, total = info.totalBytes, validator = info.validator, mime = info.mime) else it }
                        save()
                    } })
                currentCoroutineContext().ensureActive()
                if (token == serial) edit(item.id) { it.copy(status = BrowserDownloadStatus.Complete, filename = result.filename,
                    bytes = target.length(), total = result.totalBytes, validator = result.validator, mime = result.mime, error = null) }
            } catch (cancelled: CancellationException) {
                edit(item.id) { if (it.status == BrowserDownloadStatus.Running) it.copy(status = BrowserDownloadStatus.Paused, bytes = file(it.id).length()) else it }
                throw cancelled
            } catch (error: Exception) {
                if (token == serial) edit(item.id) { it.copy(status = BrowserDownloadStatus.Failed, bytes = file(it.id).length(), error = error.message?.take(400) ?: "Download interrompido.") }
            } finally {
                if (state.value.none { it.id == item.id } || state.value.any { it.id == item.id && it.status == BrowserDownloadStatus.Canceled }) file(item.id).delete()
                runningId = null; worker = null; save(); pump()
            }
        }
        worker?.start()
    }
    fun pause(id: String) {
        edit(id) { if (it.status in setOf(BrowserDownloadStatus.Running, BrowserDownloadStatus.Queued)) it.copy(status = BrowserDownloadStatus.Paused, bytes = file(it.id).length()) else it }
        if (runningId == id) { serial++; cancelTransfer(); worker?.cancel() }
        save()
    }
    fun pauseAll() {
        state.update { list -> list.map { if (it.status in setOf(BrowserDownloadStatus.Running, BrowserDownloadStatus.Queued))
            it.copy(status = BrowserDownloadStatus.Paused, bytes = file(it.id).length()) else it } }
        serial++; cancelTransfer(); worker?.cancel(); save()
    }
    fun setForeground(value: Boolean) { foreground = value; if (!value) pauseAll() }
    fun resume(id: String) {
        edit(id) { if (it.status in setOf(BrowserDownloadStatus.Paused, BrowserDownloadStatus.Failed)) it.copy(status = BrowserDownloadStatus.Queued, error = null) else it }
        save(); pump()
    }
    fun restart(id: String) {
        if (runningId == id || worker?.isCompleted == false && state.value.any { it.id == id && it.status == BrowserDownloadStatus.Running }) return
        edit(id) { if (it.status in setOf(BrowserDownloadStatus.Paused, BrowserDownloadStatus.Failed, BrowserDownloadStatus.Canceled)) {
            file(id).delete(); it.copy(status = BrowserDownloadStatus.Queued, validator = null, bytes = 0, total = -1, error = null)
        } else it }
        save(); pump()
    }
    fun cancel(id: String) {
        edit(id) { if (it.status !in setOf(BrowserDownloadStatus.Complete, BrowserDownloadStatus.Imported)) it.copy(status = BrowserDownloadStatus.Canceled, error = null) else it }
        if (runningId == id) { serial++; cancelTransfer(); worker?.cancel() }
        else if (state.value.any { it.id == id && it.status == BrowserDownloadStatus.Canceled }) file(id).delete()
        save()
    }
    fun remove(id: String) {
        if (!validId(id)) return
        val running = runningId == id
        if (running) { serial++; cancelTransfer(); worker?.cancel() }
        state.update { it.filterNot { row -> row.id == id } }
        if (!running) file(id).delete()
        save()
    }
    fun imported(id: String, importedId: String?, message: String?) { edit(id) { it.copy(status = BrowserDownloadStatus.Imported, importedId = importedId, error = message) }; save() }
    fun importing(id: String) { edit(id) { it.copy(status = BrowserDownloadStatus.Importing, error = null) }; save() }
    fun importFailed(id: String, message: String?) { edit(id) { it.copy(status = BrowserDownloadStatus.Complete, error = message) }; save() }
    companion object { fun validId(id: String) = id.matches(Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")) }
}

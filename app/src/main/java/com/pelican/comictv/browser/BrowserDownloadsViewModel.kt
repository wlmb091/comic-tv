package com.pelican.comictv.browser

import android.content.Context
import android.util.AtomicFile
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pelican.comictv.library.LibraryRepository
import java.io.File
import kotlinx.coroutines.*

internal class BrowserDownloadsViewModel(context: Context, private val library: LibraryRepository) : ViewModel() {
    private val directory = File(context.filesDir, "browser-downloads").apply { mkdirs() }
    private val records = AtomicFile(File(directory, "records.json"))
    private val gson = Gson()
    @Volatile private var downloader: BrowserDownloader? = null
    private var importJob: Job? = null
    private val blobPages = LinkedHashMap<String, java.lang.ref.WeakReference<android.webkit.WebView>>()
    fun registerBlob(offer: BrowserDownloadOffer) {
        if (browserBlobOrigin(offer.request.url) == browserPageOrigin(offer.webView.url)) {
            blobPages[offer.request.url] = java.lang.ref.WeakReference(offer.webView)
            while (blobPages.size > 4) blobPages.remove(blobPages.keys.first())
        }
    }
    private fun restore(): List<BrowserDownloadItem> = runCatching {
        if (records.baseFile.length() > 2L * 1024 * 1024) return@runCatching emptyList()
        records.openRead().bufferedReader().use { gson.fromJson(it, Array<BrowserDownloadItem>::class.java)?.toList().orEmpty() }
            .take(100).filter { BrowserDownloads.validId(it.id) && it.url.length <= 4096 && (runCatching { BrowserPolicy.url(it.url) }.isSuccess || browserBlobOrigin(it.url) != null) }
    }.getOrDefault(emptyList())
    val manager = BrowserDownloads(viewModelScope, directory, restore().map { row ->
        if (row.url.startsWith("blob:") && row.status !in setOf(BrowserDownloadStatus.Complete, BrowserDownloadStatus.Imported, BrowserDownloadStatus.Canceled))
            row.copy(status = BrowserDownloadStatus.Failed, error = "Link temporário encerrado. Reabra o download na página de origem.") else row
    },
        transfer = { request, target, progress, headers -> if (request.url.startsWith("blob:")) {
            val page = blobPages[request.url]?.get() ?: throw java.io.IOException("Reabra o arquivo temporário na página de origem.")
            BrowserBlobTransfer.transfer(page, request, target, progress, headers)
        } else withContext(Dispatchers.IO) {
            val engine = BrowserDownloader(); downloader = engine; val job = currentCoroutineContext()
            try { engine.transfer(request, target, { CookieManager.getInstance().getCookie(it) }, { job.ensureActive() }, progress, headers) }
            finally { if (downloader === engine) downloader = null }
        } },
        cancelTransfer = { downloader?.cancel() },
        persist = { rows -> synchronized(records) {
            var stream: java.io.FileOutputStream? = null
            try { stream = records.startWrite(); stream.write(gson.toJson(rows).toByteArray(Charsets.UTF_8)); records.finishWrite(stream) }
            catch (_: Exception) { stream?.let(records::failWrite) }
        } })
    val items = manager.items
    fun setForeground(value: Boolean) { manager.setForeground(value); if (!value) importJob?.cancel() }
    fun import(id: String) {
        if (importJob?.isActive == true) return
        val row = items.value.firstOrNull { it.id == id && it.status in setOf(BrowserDownloadStatus.Complete, BrowserDownloadStatus.Imported) } ?: return
        if (!BrowserDownloadNames.importable(row.filename)) return
        manager.importing(id)
        importJob = viewModelScope.launch {
            try {
                val result = library.importReceived(manager.file(id), row.filename) { }
                if (result.failures.isEmpty()) manager.imported(id, result.singleId, result.summary())
                else manager.importFailed(id, result.summary())
            } catch (cancelled: CancellationException) { manager.importFailed(id, "Importação pausada. Itens já importados são preservados."); throw cancelled }
            catch (error: Exception) { manager.importFailed(id, error.message?.take(300) ?: "Falha na importação. O download foi mantido.") }
        }
    }
    override fun onCleared() { manager.pauseAll(); downloader?.cancel(); blobPages.clear(); super.onCleared() }
}

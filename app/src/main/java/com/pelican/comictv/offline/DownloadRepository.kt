package com.pelican.comictv.offline

import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.pelican.comictv.sources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest

data class DownloadsState(val queue: List<String> = emptyList(), val saved: List<String> = emptyList(), val complete: Set<String> = emptySet(),
    val active: String? = null, val done: Int = 0, val total: Int = 0, val bytes: Long = 0, val running: Boolean = false, val message: String? = null)

class DownloadRepository(private val queueFile: File, val store: OfflineStore,
    private val plan: suspend (String) -> List<OnlinePage>, private val fetch: suspend (String,Int,File) -> Unit,
    private val dimensions: (File) -> Pair<Int,Int> = { file -> BitmapFactory.Options().apply { inJustDecodeBounds = true; BitmapFactory.decodeFile(file.path,this) }.let { it.outWidth to it.outHeight } },
    private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val gson = Gson(); private val queueLock = Mutex(); private val workerLock = Mutex()
    private val _state = MutableStateFlow(DownloadsState()); val state = _state.asStateFlow()
    suspend fun refresh() = withContext(io) { queueLock.withLock {
        val queue = if (!queueFile.exists()) emptyList() else {
            require(queueFile.length() <= 64000)
            queueFile.reader().use { gson.fromJson(it, Array<String>::class.java) }.toList().also { rows ->
                require(rows.size <= 100 && rows.distinct().size == rows.size && rows.all(OnlineIdentity::isOnline))
            }
        }
        val saved = store.ids(); val complete = saved.filter(store::isComplete).toSet()
        _state.update { it.copy(queue = queue, saved = saved, complete = complete) }
    } }
    private fun saveQueue(queue: List<String>) {
        queueFile.parentFile?.mkdirs(); val temp = File(queueFile.path + ".tmp")
        try {
            temp.outputStream().use { it.write(gson.toJson(queue).toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temp.toPath(),queueFile.toPath(),ATOMIC_MOVE,REPLACE_EXISTING)
            _state.update { it.copy(queue = queue) }
        } finally { temp.delete() }
    }
    suspend fun enqueue(id: String) = withContext(io) { queueLock.withLock {
        require(OnlineIdentity.isOnline(id)); val queue = _state.value.queue
        if (id !in queue && !store.isComplete(id)) { require(queue.size < 100) { "Fila limitada a 100 edições." }; saveQueue(queue + id) }
    } }
    suspend fun remove(id: String) = withContext(io) { workerLock.withLock { queueLock.withLock {
        store.remove(id); saveQueue(_state.value.queue - id)
        _state.update { it.copy(saved = it.saved - id, complete = it.complete - id, active = it.active.takeUnless { active -> active == id }, message = "Download removido. Biblioteca e progresso foram mantidos.") }
    } } }
    suspend fun runQueue() = withContext(io) { workerLock.withLock {
        _state.update { it.copy(running = true,message = null) }
        try {
            while (_state.value.queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val id = _state.value.queue.first()
                if (!store.isComplete(id)) {
                    _state.update { it.copy(active = id,done = 0,total = 0,bytes = 0) }
                    val pages = plan(id)
                    require(pages.isNotEmpty() && pages.size <= 5000 && pages.map { it.index } == pages.indices.toList())
                    val fingerprint = MessageDigest.getInstance("SHA-256").digest(gson.toJson(pages).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                    val before = store.progress(id)
                    var manifest = runInterruptible { store.begin(id,pages.size,fingerprint) }
                    _state.update { it.copy(total = pages.size,done = manifest.pages.size,bytes = manifest.pages.sumOf { page -> page.bytes },
                        message = if (before > 0 && manifest.pages.isEmpty()) "A lista da fonte mudou. Reiniciando o download parcial." else it.message) }
                    for (index in pages.indices) {
                        if (manifest.pages.any { it.index == index }) continue
                        currentCoroutineContext().ensureActive()
                        val target = store.incoming(id,index)
                        try {
                            fetch(id,index,target); currentCoroutineContext().ensureActive()
                            val (width,height) = dimensions(target)
                            manifest = runInterruptible { store.commitPage(id,index,width,height) }
                            _state.update { it.copy(done = manifest.pages.size,bytes = manifest.pages.sumOf { page -> page.bytes }) }
                        } finally { target.delete() }
                    }
                    runInterruptible { store.finish(id) }
                }
                queueLock.withLock { saveQueue(_state.value.queue - id) }
                refresh()
            }
            _state.update { it.copy(message = "Fila concluída. As edições completas podem ser lidas sem internet.") }
        } catch (e: CancellationException) { _state.update { it.copy(message = "Download pausado. Use Retomar para continuar.") }; throw e }
        catch (_: Exception) { _state.update { it.copy(message = "Download interrompido. Confira a fonte, a conexão e o espaço livre; depois retome. Limites: 1 GiB por edição e 96 MiB livres para iniciar uma página.") } }
        finally { _state.update { it.copy(running = false) } }
    } }
}

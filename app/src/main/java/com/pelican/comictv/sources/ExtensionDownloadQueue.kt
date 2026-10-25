package com.pelican.comictv.sources

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ExtensionTransferStatus { Queued, Downloading, Ready, Failed }
data class ExtensionTransfer(val token: Long, val candidate: AvailableExtension, val status: ExtensionTransferStatus,
    val bytes: Long = 0, val total: Long = -1, val file: File? = null, val error: String? = null)

/** Main-thread commands, one writer at a time. IO progress publication uses atomic StateFlow updates. */
class ExtensionDownloadQueue(private val scope: CoroutineScope,
    private val prepare: suspend (AvailableExtension, (Long,Long) -> Unit) -> File) {
    private val mutable = MutableStateFlow<List<ExtensionTransfer>>(emptyList())
    val state = mutable.asStateFlow()
    private var sequence = 0L
    private var visible = false
    private var worker: Job? = null
    private var active: Pair<Long,Job>? = null
    private var installing: Long? = null
    private val visibilityOwners = mutableSetOf<Any>()
    private val defaultOwner = Any()
    private var validation: Job? = null

    fun toggle(candidate: AvailableExtension) {
        val existing = mutable.value.firstOrNull { it.candidate.entry.packageName == candidate.entry.packageName }
        if (existing?.token == installing && installing != null) return
        if (existing != null) {
            mutable.update { it.filterNot { row -> row.token == existing.token } }
            active?.takeIf { it.first == existing.token }?.second?.cancel()
            existing.file?.delete()
            if (existing.status == ExtensionTransferStatus.Queued || existing.status == ExtensionTransferStatus.Downloading) return
        }
        require(mutable.value.size < 12) { "Fila limitada a 12 extensões. Instale ou limpe as baixadas antes de adicionar outras." }
        mutable.update { it + ExtensionTransfer(++sequence,candidate,ExtensionTransferStatus.Queued) }
        kick()
    }
    fun setVisible(value: Boolean) = setVisible(defaultOwner,value)
    fun setVisible(owner: Any, value: Boolean) {
        if (value) visibilityOwners.add(owner) else visibilityOwners.remove(owner)
        applyVisibility(visibilityOwners.isNotEmpty())
    }
    private fun applyVisibility(value: Boolean) {
        visible = value
        if (!value) {
            validation?.cancel()
            mutable.update { it.map { row -> if (row.status == ExtensionTransferStatus.Downloading)
                row.copy(status = ExtensionTransferStatus.Queued,bytes = 0,total = -1) else row } }
            active?.second?.cancel()
        } else kick()
    }
    private fun kick() {
        if (!visible || worker?.isActive == true) return
        worker = scope.launch {
            try {
                while (visible) {
                    val row = mutable.value.firstOrNull { it.status == ExtensionTransferStatus.Queued } ?: break
                    val child = launch {
                        var file: File? = null
                        try {
                            mutable.update { it.map { value -> if (value.token == row.token) value.copy(status = ExtensionTransferStatus.Downloading) else value } }
                            file = prepare(row.candidate) { bytes,total -> mutable.update { it.map { value ->
                                if (value.token == row.token && value.status == ExtensionTransferStatus.Downloading)
                                    value.copy(bytes = bytes.coerceAtLeast(0),total = total) else value
                            } } }
                            ensureActive()
                            if (mutable.value.none { it.token == row.token }) { file.delete(); return@launch }
                            mutable.update { it.map { value -> if (value.token == row.token) value.copy(status = ExtensionTransferStatus.Ready,file = file) else value } }
                        } catch (e: CancellationException) {
                            file?.delete()
                            mutable.update { it.map { value -> if (value.token == row.token && value.status == ExtensionTransferStatus.Downloading)
                                value.copy(status = ExtensionTransferStatus.Queued,bytes = 0,total = -1) else value } }
                            throw e
                        } catch (e: Exception) {
                            file?.delete()
                            mutable.update { it.map { value -> if (value.token == row.token) value.copy(status = ExtensionTransferStatus.Failed,
                                error = e.message?.take(180) ?: "Não foi possível baixar.") else value } }
                        }
                    }
                    active = row.token to child
                    child.join() // Cancellation must finish deleting its partial file before the next writer starts.
                    active = null
                }
            } finally { worker = null }
        }
    }
    fun claimInstall(candidate: AvailableExtension, expectedToken: Long? = null): File? {
        if (installing != null) return null
        val row = mutable.value.firstOrNull { it.candidate == candidate && it.status == ExtensionTransferStatus.Ready } ?: return null
        if (expectedToken != null && row.token != expectedToken) return null
        val file = row.file?.takeIf { it.isFile } ?: return null
        installing = row.token
        return file
    }
    fun requestInstall(candidate: AvailableExtension, validate: suspend (File) -> Unit,
        onReady: (File) -> Unit, onError: (String) -> Unit) {
        if (!visible || installing != null) return
        val row = mutable.value.firstOrNull { it.candidate == candidate && it.status == ExtensionTransferStatus.Ready } ?: return
        val file = row.file ?: return
        val previous = validation
        previous?.cancel()
        validation = scope.launch {
            previous?.join()
            try {
                ensureActive(); if (!visible) return@launch
                validate(file)
                ensureActive(); if (!visible) return@launch
                claimInstall(candidate,row.token)?.let(onReady)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (mutable.value.none {it.token == row.token} || !visible) return@launch
                installationReturned(file,false)
                val message=e.message?.take(180) ?: "Não foi possível verificar o APK. Baixe novamente."
                invalidate(candidate,message); onError(message)
            }
        }
    }
    fun installationReturned(file: File, remove: Boolean) {
        val row = mutable.value.firstOrNull { it.file == file }
        if (row?.token == installing) installing = null
        if (remove && row != null) {
            mutable.update { it.filterNot { value -> value.token == row.token } }
            file.delete()
        }
    }
    fun invalidate(candidate: AvailableExtension, message: String) {
        val row = mutable.value.firstOrNull { it.candidate == candidate && it.status == ExtensionTransferStatus.Ready && it.token != installing } ?: return
        row.file?.delete()
        mutable.update { it.map { value -> if (value.token == row.token) value.copy(status = ExtensionTransferStatus.Failed,file = null,error = message) else value } }
    }
    fun clear() {
        validation?.cancel()
        val removed = mutable.value.filter { it.token != installing }
        mutable.update { it.filter { value -> value.token == installing } }
        active?.takeIf { pair -> removed.any { it.token == pair.first } }?.second?.cancel()
        removed.forEach { it.file?.delete() }
    }
    fun close() { visibilityOwners.clear(); applyVisibility(false); worker?.cancel() }
}

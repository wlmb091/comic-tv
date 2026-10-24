package com.pelican.comictv.backup

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.*
import com.google.zxing.qrcode.QRCodeWriter
import com.pelican.comictv.transfer.LocalAddressResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class BackupUiState(val busy: Boolean = false, val file: File? = null, val preview: BackupDocument? = null,
    val restoreAppearance: Boolean = true, val pending: Int = 0, val url: String? = null, val qr: Bitmap? = null, val message: String? = null)

class BackupViewModel(private val context: Context, private val repository: BackupRepository) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()
    private var server: BackupTransferServer? = null
    private var sessionJob: Job? = null
    private var expiry: Job? = null
    private var generation = 0
    init { viewModelScope.launch { _state.update { it.copy(pending = repository.pendingCount()) } } }
    fun notice(message: String) { _state.update { it.copy(message = message) } }
    private fun run(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice("Não foi possível concluir. Confira o arquivo, o espaço e tente novamente.") }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun prepare() = run {
        val file = repository.prepare(); val old = _state.value.file
        _state.update { it.copy(file = file, message = "Backup preparado. Salve uma cópia fora da TV; quadrinhos e downloads não estão incluídos.") }
        old?.takeIf { it != file }?.delete()
    }
    fun preview(uri: Uri) = run {
        val document = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use(BackupCodec::decode) ?: error("Arquivo indisponível")
        }
        _state.update { it.copy(preview = document) }
    }
    fun save(uri: Uri) = run {
        val source = checkNotNull(_state.value.file)
        withContext(Dispatchers.IO) {
            try { context.contentResolver.openOutputStream(uri, "w")?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("Destino indisponível") }
            catch (e: Exception) { runCatching { context.contentResolver.delete(uri, null, null) }; throw e }
        }
        notice("Backup salvo. Guarde uma cópia fora da TV.")
    }
    fun dismissPreview() { if (!_state.value.busy) _state.update { it.copy(preview = null) } }
    fun toggleAppearance() { _state.update { it.copy(restoreAppearance = !it.restoreAppearance) } }
    fun restore() = run {
        val document = checkNotNull(_state.value.preview)
        val result = repository.restore(document, _state.value.restoreAppearance)
        _state.update { it.copy(preview = null, pending = result.pending,
            message = "Restaurado: ${result.added} adicionados, ${result.updated} mesclados. ${result.pending} locais aguardam reimportação do original.") }
    }
    fun startSession() = run {
        stopSession()
        val host = withContext(Dispatchers.IO) { LocalAddressResolver.currentAddress(context) } ?: error("Conecte à rede local")
        val expected = ++generation
        val job = SupervisorJob(viewModelScope.coroutineContext[Job]); sessionJob = job
        val instance = BackupTransferServer(host, File(context.cacheDir,"backup-transfers"), { _state.value.file }, { file ->
            runBlocking { withContext(job) {
                val preview = repository.preview(file); ensureActive()
                check(generation == expected && !_state.value.busy && _state.value.preview == null) { "TV ocupada" }
                _state.update { it.copy(preview = preview, message = "Backup recebido. Confira a prévia antes de confirmar.") }
            } }
        })
        try {
            withContext(Dispatchers.IO) { instance.open() }
            server = instance
            val bitmap = withContext(Dispatchers.Default) {
                val matrix = QRCodeWriter().encode(instance.url, BarcodeFormat.QR_CODE, 384, 384)
                Bitmap.createBitmap(IntArray(384*384) { if (matrix[it%384,it/384]) -0x1000000 else -1 },384,384,Bitmap.Config.ARGB_8888)
            }
            _state.update { it.copy(url = instance.url, qr = bitmap) }
            expiry = viewModelScope.launch { while (isActive) {
                delay(1000)
                if (instance.expired() || withContext(Dispatchers.IO) { LocalAddressResolver.currentAddress(context) } != host) { stopSession(); notice("Sessão encerrada ou rede alterada."); break }
            } }
        } catch (e: Exception) { instance.close(); job.cancel(); throw e }
    }
    fun stopSession() {
        generation++; expiry?.cancel(); expiry = null
        sessionJob?.cancel(); sessionJob = null; server?.close(); server = null
        _state.update { it.copy(url = null, qr = null) }
    }
    override fun onCleared() { stopSession(); _state.value.file?.delete() }
}

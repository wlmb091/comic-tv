package com.pelican.comictv.transfer

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.pelican.comictv.library.LibraryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class LocalTransferState(
    val starting: Boolean = false, val url: String? = null, val qr: Bitmap? = null,
    val status: TransferStatus = TransferStatus(), val message: String? = null,
)

class LocalTransferViewModel(private val context: Context, private val library: LibraryRepository) : ViewModel() {
    private val _state = MutableStateFlow(LocalTransferState())
    val state = _state.asStateFlow()
    private var server: LocalUploadServer? = null
    private var startJob: Job? = null
    private var expiryJob: Job? = null
    private var sessionJob: Job? = null
    private val generation = AtomicInteger()

    fun start() {
        if (server != null || _state.value.starting) return
        _state.value = LocalTransferState(starting = true)
        val sessionGeneration = generation.incrementAndGet()
        startJob = viewModelScope.launch {
            try {
                val host = withContext(Dispatchers.IO) { LocalAddressResolver.currentAddress(context) }
                    ?: throw java.io.IOException("Conecte a TV ao Wi-Fi ou Ethernet para iniciar a transferência.")
                val job = SupervisorJob(viewModelScope.coroutineContext[Job])
                sessionJob = job
                val directory = File(context.cacheDir, "uploads").apply { mkdirs() }
                val instance = LocalUploadServer(host, directory, clock = SystemClock::elapsedRealtime,
                    onStatus = { status -> _state.update {
                        if (generation.get() == sessionGeneration) it.copy(status = status) else it
                    } },
                    onReceived = { file, name ->
                        try { runBlocking { withContext(job) { library.importComic(file, name) } } }
                        catch (e: Exception) {
                            if (e !is CancellationException) Log.e("ComicTvTransfer", "Falha ao importar quadrinho recebido.", e)
                            throw e
                        }
                    })
                server = instance
                val url = withContext(Dispatchers.IO) { instance.open(); instance.url }
                val qr = withContext(Dispatchers.Default) {
                    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.MARGIN to 4))
                    val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                    Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888)
                }
                _state.update { it.copy(starting = false, url = url, qr = qr) }
                expiryJob = viewModelScope.launch {
                    while (isActive) {
                        delay(1_000)
                        if (instance.expired()) { stop("Sessão expirada. Inicie uma nova para continuar."); break }
                        val current = withContext(Dispatchers.IO) { LocalAddressResolver.currentAddress(context) }
                        if (current != host) { stop("A rede da TV mudou. Inicie uma nova sessão."); break }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (isActive && generation.get() == sessionGeneration) stop(e.message ?: "Não foi possível iniciar o servidor de transferência.")
            }
        }
    }

    fun stop(message: String? = null) {
        generation.incrementAndGet()
        startJob?.cancel(); startJob = null
        expiryJob?.cancel(); expiryJob = null
        sessionJob?.cancel(); sessionJob = null
        val old = server
        server = null
        old?.close()
        _state.update { it.copy(starting = false, url = null, qr = null,
            status = it.status.copy(running = false, clients = 0, activeUploads = 0), message = message) }
    }

    override fun onCleared() { stop() }
}

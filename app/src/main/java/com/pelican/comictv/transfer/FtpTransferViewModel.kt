package com.pelican.comictv.transfer

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
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
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

data class FtpTransferState(
    val starting: Boolean = false, val url: String? = null, val username: String? = null,
    val password: String? = null, val qr: Bitmap? = null,
    val status: TransferStatus = TransferStatus(), val message: String? = null,
)

class FtpTransferViewModel(context: Context, private val library: LibraryRepository) : ViewModel() {
    private val app = context.applicationContext
    private val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _state = MutableStateFlow(FtpTransferState())
    val state = _state.asStateFlow()
    private var server: FtpUploadServer? = null
    private var startJob: Job? = null
    private var expiryJob: Job? = null
    private var importJob: Job? = null
    private val generation = AtomicInteger()

    fun start() {
        if (server != null || _state.value.starting) return
        val current = generation.incrementAndGet()
        _state.value = FtpTransferState(starting = true)
        startJob = viewModelScope.launch {
            try {
                val network = connectivity.activeNetwork
                val host = withContext(Dispatchers.IO) { LocalAddressResolver.currentAddress(app) }
                    ?: throw IOException("Conecte a TV ao Wi-Fi ou Ethernet para iniciar o FTP.")
                val session = SupervisorJob(viewModelScope.coroutineContext[Job])
                importJob = session
                val instance = FtpUploadServer(host, File(app.cacheDir, "ftp-uploads"), clock = SystemClock::elapsedRealtime,
                    onStatus = { status -> _state.update { if (generation.get() == current) it.copy(status = status) else it } },
                    onReceived = { file, name ->
                        try {
                            runBlocking { withContext(session) {
                                ensureActive()
                                if (generation.get() != current) throw CancellationException("Sessão encerrada")
                                library.importComic(file, name)
                            } }
                        } catch (e: Exception) {
                            if (e !is CancellationException) Log.e("ComicTV-FTP", "Falha ao importar arquivo recebido", e)
                            throw e
                        }
                    })
                server = instance
                val url = withContext(Dispatchers.IO) { instance.open(); instance.url }
                val qr = withContext(Dispatchers.Default) { ftpQr(url) }
                _state.update { if (generation.get() == current) it.copy(starting = false, url = url,
                    username = instance.username, password = instance.password, qr = qr) else it }
                expiryJob = viewModelScope.launch {
                    while (isActive) {
                        delay(1000)
                        if (instance.expired()) { stop("Sessão expirada. Inicie outra para continuar."); break }
                        val address = withContext(Dispatchers.IO) { LocalAddressResolver.currentAddress(app) }
                        if (address != host || connectivity.activeNetwork != network) {
                            stop("A rede da TV mudou. Inicie uma nova sessão."); break
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (isActive && generation.get() == current) stop(e.message ?: "Não foi possível iniciar o FTP.")
            }
        }
    }

    fun stop(message: String? = null) {
        generation.incrementAndGet()
        startJob?.cancel(); startJob = null
        expiryJob?.cancel(); expiryJob = null
        importJob?.cancel(); importJob = null
        val old = server; server = null
        old?.close()
        _state.update { it.copy(starting = false, url = null, username = null, password = null, qr = null,
            status = it.status.copy(running = false, clients = 0, activeUploads = 0), message = message) }
    }

    override fun onCleared() { stop() }
}

private fun ftpQr(url: String): Bitmap {
    val size = 512
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 4))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

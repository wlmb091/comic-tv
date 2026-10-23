package com.pelican.comictv.transfer

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

data class TransferStatus(
    val running: Boolean = false, val activeUploads: Int = 0, val receivedBytes: Long = 0,
    val totalBytes: Long = 0, val completed: Int = 0, val clients: Int = 0,
    val message: String = "Servidor parado",
)

class LocalUploadServer(
    private val host: String,
    private val temporaryDirectory: File,
    private val maxBytes: Long = 1024L * 1024 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 15 * 60 * 1000L,
    private val onStatus: (TransferStatus) -> Unit = {},
    private val onReceived: (File, String) -> Unit,
) : Closeable {
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private val root = "/s/$token"
    private val gate = Semaphore(1)
    private val stateLock = Any()
    private val clients = LinkedHashMap<String, Long>()
    private var openedAt = 0L
    private var permanentlyClosed = false
    @Volatile private var accepting = false
    @Volatile var status = TransferStatus(); private set
    val port: Int get() = server.listeningPort
    val url: String get() = "http://$host:$port$root"

    private val server = object : NanoHTTPD(host, 0) {
        override fun serve(session: IHTTPSession): Response = handle(session)
    }.apply {
        // Bound simultaneous HTTP handlers, including clients that never finish sending headers.
        val delegate = NanoHTTPD.DefaultAsyncRunner()
        val count = AtomicInteger()
        setAsyncRunner(object : NanoHTTPD.AsyncRunner {
            override fun exec(handler: NanoHTTPD.ClientHandler) {
                if (count.incrementAndGet() > 4) { count.decrementAndGet(); handler.close() }
                else delegate.exec(handler)
            }
            override fun closed(handler: NanoHTTPD.ClientHandler) { count.decrementAndGet(); delegate.closed(handler) }
            override fun closeAll() { delegate.closeAll() }
        })
    }

    @Synchronized fun open() {
        check(!permanentlyClosed) { "Esta sessão já foi encerrada." }
        check(!accepting) { "A sessão já está aberta." }
        temporaryDirectory.mkdirs()
        temporaryDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(".upload") }?.forEach { it.delete() }
        server.start(15_000, true)
        openedAt = clock()
        accepting = true
        update { it.copy(running = true, message = "Aguardando envio de quadrinhos") }
    }

    fun expired(): Boolean = clock() - openedAt >= lifetimeMillis

    @Synchronized override fun close() {
        permanentlyClosed = true
        accepting = false
        server.stop()
        update { it.copy(running = false, clients = 0, message = "Servidor parado") }
    }

    private fun update(change: (TransferStatus) -> TransferStatus) = synchronized(stateLock) {
        status = change(status)
        onStatus(status)
    }

    private enum class UploadStatus(private val number: Int, private val phrase: String) : Response.IStatus {
        Invalid(422, "Unprocessable Entity"), NoSpace(507, "Insufficient Storage");
        override fun getRequestStatus() = number
        override fun getDescription() = "$number $phrase"
    }

    private fun response(code: Response.IStatus, text: String, html: Boolean = false): Response =
        NanoHTTPD.newFixedLengthResponse(code, if (html) "text/html; charset=utf-8" else "text/plain; charset=utf-8", text).apply {
            addHeader("Connection", "close")
            addHeader("Cache-Control", "no-store")
            addHeader("Referrer-Policy", "no-referrer")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("X-Frame-Options", "DENY")
            addHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
        }

    private fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri
        if (uri != root && uri != "$root/upload" && uri != "$root/status") return response(NanoHTTPD.Response.Status.NOT_FOUND, "Sessão não encontrada.")
        if (!accepting || expired()) return response(NanoHTTPD.Response.Status.GONE, "Sessão encerrada ou expirada. Inicie outra na TV.")
        val expectedHost = "$host:$port"
        if (session.headers["host"] != expectedHost) return response(NanoHTTPD.Response.Status.FORBIDDEN, "Endereço de acesso inválido.")
        val origin = session.headers["origin"]
        if (origin != null && origin != "http://$expectedHost") return response(NanoHTTPD.Response.Status.FORBIDDEN, "Origem não autorizada.")
        synchronized(stateLock) {
            val now = clock()
            clients.entries.removeAll { now - it.value > 15_000 }
            if (clients.size < 8 || session.remoteIpAddress in clients) clients[session.remoteIpAddress] = now
            update { it.copy(clients = clients.size) }
        }
        if (session.method == NanoHTTPD.Method.GET && uri == root) return response(NanoHTTPD.Response.Status.OK, UploadPage.html, true)
        if (session.method == NanoHTTPD.Method.GET && uri == "$root/status") return response(NanoHTTPD.Response.Status.OK, "Sessão ativa")
        if (session.method != NanoHTTPD.Method.POST || uri != "$root/upload") return response(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "Método não permitido.")
        if (session.headers.containsKey("transfer-encoding")) return response(NanoHTTPD.Response.Status.BAD_REQUEST, "Envie um arquivo com tamanho conhecido.")
        val length = session.headers["content-length"]?.toLongOrNull()
            ?: return response(NanoHTTPD.Response.Status.LENGTH_REQUIRED, "O tamanho do arquivo é obrigatório.")
        if (length <= 0 || length > maxBytes) return response(NanoHTTPD.Response.Status.PAYLOAD_TOO_LARGE, "Limite de 1 GiB por arquivo.")
        val name = session.parameters["name"]?.singleOrNull()?.takeIf {
            it.length in 1..200 && !it.contains('/') && !it.contains('\\') && it.none { c -> c.code < 32 } &&
                it.substringAfterLast('.', "").lowercase() in setOf("cbz", "zip")
        } ?: return response(NanoHTTPD.Response.Status.BAD_REQUEST, "Selecione um arquivo CBZ ou ZIP com nome válido.")
        if (!gate.tryAcquire()) return response(NanoHTTPD.Response.Status.CONFLICT, "Outro envio está em andamento. Tente novamente em instantes.")
        val target = File(temporaryDirectory, "${UUID.randomUUID()}.upload")
        try {
            if (temporaryDirectory.usableSpace < 2 * length + 64L * 1024 * 1024) return response(UploadStatus.NoSpace, "Espaço insuficiente na TV.")
            update { it.copy(activeUploads = 1, receivedBytes = 0, totalBytes = length, message = "Recebendo $name") }
            var received = 0L
            var notified = 0L
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (received < length) {
                    if (!accepting || expired() || Thread.currentThread().isInterrupted) throw IOException("Sessão encerrada")
                    val read = session.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), length - received).toInt())
                    if (read < 0) throw IOException("Envio incompleto")
                    output.write(buffer, 0, read)
                    received += read
                    if (received - notified >= 256 * 1024 || received == length) {
                        notified = received
                        update { it.copy(receivedBytes = received) }
                    }
                }
            }
            if (!accepting || expired()) throw IOException("Sessão encerrada")
            update { it.copy(message = "Validando $name") }
            try { onReceived(target, name) }
            catch (_: Exception) {
                update { it.copy(message = "Arquivo recusado: verifique se é um CBZ/ZIP válido") }
                return response(UploadStatus.Invalid, "Arquivo recusado. Verifique se é um CBZ/ZIP válido e se há espaço disponível.")
            }
            update { it.copy(completed = it.completed + 1, message = "Importação concluída: $name") }
            return response(NanoHTTPD.Response.Status.OK, "Quadrinho adicionado à biblioteca.")
        } catch (_: IOException) {
            update { it.copy(message = "Envio incompleto ou interrompido. Tente novamente.") }
            return response(NanoHTTPD.Response.Status.BAD_REQUEST, "Envio incompleto ou interrompido.")
        } finally {
            target.delete()
            update { it.copy(activeUploads = 0) }
            gate.release()
        }
    }
}

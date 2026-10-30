package com.pelican.comictv.transfer

import com.pelican.comictv.archive.ComicImportException
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Semaphore

/** A temporary, passive-only FTP inbox. Its virtual filesystem never exposes local files. */
class FtpUploadServer(
    private val host: String,
    private val temporaryDirectory: File,
    private val maxBytes: Long = 1024L * 1024 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMillis: Long = 15 * 60 * 1000L,
    private val onStatus: (TransferStatus) -> Unit = {},
    private val onReceived: (File, String) -> Unit,
) : Closeable {
    val username = "comictv"
    val password = ByteArray(18).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val lock = Any()
    private val uploadGate = Semaphore(1)
    private val sessions = mutableSetOf<Session>()
    private var listener: ServerSocket? = null
    private var openedAt = 0L
    private var closed = false
    @Volatile private var running = false
    @Volatile var status = TransferStatus(); private set
    val port: Int get() = listener?.localPort ?: -1
    val url: String get() = "ftp://$username:$password@$host:$port/"

    fun open(): Unit = synchronized(lock) {
        check(!closed && !running) { "Esta sessão já foi encerrada ou está aberta." }
        require(maxBytes > 0 && lifetimeMillis > 0)
        val address = InetAddress.getByName(host)
        require(address is Inet4Address) { "Use um endereço IPv4 local." }
        check(temporaryDirectory.isDirectory || temporaryDirectory.mkdirs())
        temporaryDirectory.listFiles()?.filter { it.isFile && it.name.matches(Regex("[0-9a-f-]{36}\\.ftp-upload")) }?.forEach { it.delete() }
        listener = ServerSocket(0, 4, address).apply { soTimeout = 500 }
        openedAt = clock()
        running = true
        update { it.copy(running = true, message = "Aguardando envio de quadrinhos") }
        daemon("ftp-listener") {
            while (running) {
                if (expired()) { close(); break }
                try {
                    val socket = listener!!.accept()
                    synchronized(lock) {
                        if (!running || sessions.size >= 4) {
                            runCatching { socket.getOutputStream().write("421 Sessão indisponível.\r\n".toByteArray()) }
                            socket.close()
                        } else {
                            val session = Session(socket)
                            sessions.add(session)
                            update { it.copy(clients = sessions.size) }
                            session.worker = daemon("ftp-client") { session.run() }
                        }
                    }
                } catch (_: SocketTimeoutException) { /* Check expiry every half second. */ }
                catch (_: IOException) { if (running) close() }
            }
        }
    }

    fun expired(): Boolean = clock() - openedAt >= lifetimeMillis

    override fun close() {
        val active: List<Session>
        synchronized(lock) {
            closed = true
            running = false
            runCatching { listener?.close() }
            active = sessions.toList()
            active.forEach { it.stop() }
        }
        update { it.copy(running = false, clients = 0, message = "Servidor parado") }
    }

    private fun daemon(name: String, action: () -> Unit): Thread = Thread(action, name).apply {
        isDaemon = true
        start()
    }

    private fun update(change: (TransferStatus) -> TransferStatus) = synchronized(lock) {
        status = change(status)
        runCatching { onStatus(status) }
        Unit
    }

    private inner class Session(private val control: Socket) {
        var worker: Thread? = null
        @Volatile private var passive: ServerSocket? = null
        @Volatile private var data: Socket? = null
        private var userAccepted = false
        private var authenticated = false
        private var attempts = 0
        private var commands = 0

        fun stop() {
            runCatching { control.close() }
            closeData()
            worker?.interrupt()
        }

        private fun closeData() {
            runCatching { passive?.close() }; passive = null
            runCatching { data?.close() }; data = null
        }

        private fun reply(code: Int, message: String) {
            control.getOutputStream().write("$code ${message.replace('\r', ' ').replace('\n', ' ')}\r\n".toByteArray(Charsets.UTF_8))
        }

        private fun command(): String? {
            val bytes = java.io.ByteArrayOutputStream()
            val input = control.getInputStream()
            while (true) {
                val byte = input.read()
                if (byte < 0) return null
                if (byte == 10) return bytes.toString("UTF-8").removeSuffix("\r")
                if (bytes.size() >= 4096) throw IOException("Command limit")
                bytes.write(byte)
            }
        }

        fun run() {
            try {
                control.soTimeout = 30_000
                reply(220, "Comic TV - recebimento temporário")
                while (running && ++commands <= 4096) {
                    val line = command() ?: break
                    if (!running || expired()) { reply(421, "Sessão encerrada ou expirada."); break }
                    val verb = line.substringBefore(' ').uppercase(java.util.Locale.ROOT)
                    val argument = line.substringAfter(' ', "")
                    when {
                        verb == "QUIT" -> { reply(221, "Até logo."); break }
                        verb == "USER" -> { authenticated = false; userAccepted = argument == username; reply(331, "Informe a senha.") }
                        verb == "PASS" -> {
                            authenticated = userAccepted && java.security.MessageDigest.isEqual(argument.toByteArray(), password.toByteArray())
                            reply(if (authenticated) 230 else 530, if (authenticated) "Acesso autorizado." else "Credenciais inválidas.")
                            if (!authenticated && ++attempts >= 3) break
                        }
                        !authenticated -> reply(530, "Autenticação necessária.")
                        verb == "SYST" -> reply(215, "UNIX Type: L8")
                        verb == "FEAT" -> control.getOutputStream().write("211-Recursos\r\n UTF8\r\n EPSV\r\n PASV\r\n MLST type*;size*;\r\n211 Fim\r\n".toByteArray())
                        verb == "OPTS" && argument.equals("UTF8 ON", true) -> reply(200, "UTF8 ativo.")
                        verb == "PWD" || verb == "XPWD" -> reply(257, "\"/\" é a raiz.")
                        verb == "CWD" && argument in listOf("/", ".") -> reply(250, "Raiz selecionada.")
                        verb == "TYPE" && argument.uppercase() in listOf("I", "A", "A N") -> reply(200, "Tipo aceito.")
                        verb == "NOOP" -> reply(200, "OK")
                        verb == "PASV" || verb == "EPSV" -> passive(verb)
                        verb == "LIST" || verb == "NLST" || verb == "MLSD" -> listing(argument)
                        verb == "STOR" -> upload(argument)
                        else -> reply(502, "Comando não permitido nesta área de recebimento.")
                    }
                }
            } catch (_: IOException) { /* Socket errors and timeouts terminate only this client. */ }
            finally {
                stop()
                synchronized(lock) { sessions.remove(this); update { it.copy(clients = if (running) sessions.size else 0) } }
            }
        }

        private fun passive(verb: String) {
            closeData()
            val endpoint = ServerSocket(0, 1, control.localAddress).apply { soTimeout = 15_000 }
            synchronized(lock) {
                if (!running || expired()) { endpoint.close(); throw IOException("Stopped") }
                passive = endpoint
            }
            if (verb == "EPSV") reply(229, "Entering Extended Passive Mode (|||${endpoint.localPort}|)")
            else reply(227, "Entering Passive Mode (${control.localAddress.hostAddress.replace('.', ',')},${endpoint.localPort / 256},${endpoint.localPort % 256})")
        }

        private fun acceptData(): Socket {
            val endpoint = passive ?: throw IOException("Passive mode required")
            val accepted = endpoint.accept()
            synchronized(lock) {
                if (!running || expired()) { accepted.close(); throw IOException("Stopped") }
                data = accepted
            }
            endpoint.close(); passive = null
            if (accepted.inetAddress != control.inetAddress || !running || expired()) {
                accepted.close()
                throw IOException("Invalid data connection")
            }
            accepted.soTimeout = 15_000
            return accepted
        }

        private fun listing(argument: String) {
            if (argument !in listOf("", "/", ".", "-a", "-l", "-la")) { closeData(); reply(550, "Somente a raiz está disponível."); return }
            if (passive == null) { reply(425, "Use PASV ou EPSV."); return }
            reply(150, "Abrindo lista vazia.")
            try { acceptData().close(); reply(226, "Lista concluída.") }
            catch (_: IOException) { reply(425, "Conexão de dados indisponível.") }
            finally { closeData() }
        }

        private fun upload(name: String) {
            if (name.length !in 1..200 || name.any { it.code < 32 || it.code == 127 || it == '/' || it == '\\' } ||
                name.substringAfterLast('.', "").lowercase() !in com.pelican.comictv.archive.ComicContainers.extensions) {
                closeData(); reply(550, "Nome ou formato inválido."); return
            }
            if (passive == null) { reply(425, "Use PASV ou EPSV."); return }
            if (!uploadGate.tryAcquire()) { closeData(); reply(450, "Outro envio está em andamento."); return }
            val target = File(temporaryDirectory, "${UUID.randomUUID()}.ftp-upload")
            var pendingReply: Pair<Int, String>? = null
            fun result(code: Int, message: String) { pendingReply = code to message }
            try {
                if (temporaryDirectory.usableSpace < 64L * 1024 * 1024) { result(452, "Espaço insuficiente na TV."); return }
                update { it.copy(activeUploads = 1, receivedBytes = 0, totalBytes = 0, message = "Recebendo $name") }
                reply(150, "Pronto para receber.")
                var received = 0L
                var notified = 0L
                acceptData().getInputStream().use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (!running || expired() || Thread.currentThread().isInterrupted) throw IOException("Stopped")
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > maxBytes - received) { result(552, "Limite de tamanho excedido."); return }
                        if (temporaryDirectory.usableSpace < received + count + 64L * 1024 * 1024) { result(452, "Espaço insuficiente na TV."); return }
                        output.write(buffer, 0, count)
                        received += count
                        if (received - notified >= 256 * 1024) {
                            notified = received
                            update { it.copy(receivedBytes = received) }
                        }
                    }
                } }
                update { it.copy(receivedBytes = received) }
                if (received == 0L) { result(550, "Arquivo vazio."); return }
                synchronized(lock) {
                    if (!running || expired() || Thread.currentThread().isInterrupted) throw IOException("Stopped")
                    // Register the callback's beginning under the same lock as close().
                    update { it.copy(message = "Validando $name") }
                }
                try { onReceived(target, name) }
                catch (error: Exception) {
                    val reason = generateSequence<Throwable>(error) { it.cause }.take(8)
                        .filterIsInstance<ComicImportException>().firstOrNull()?.message
                        ?: "Não foi possível importar este arquivo. Verifique o formato e o espaço disponível na TV."
                    if (running) update { it.copy(message = "Arquivo recusado: $reason") }
                    result(550, reason)
                    return
                }
                if (!running || expired()) throw IOException("Stopped")
                update { it.copy(completed = it.completed + 1, message = "Importação concluída: $name") }
                result(226, "Quadrinho adicionado à biblioteca.")
            } catch (_: IOException) {
                if (running) { update { it.copy(message = "Envio incompleto ou interrompido. Tente novamente.") }; runCatching { result(426, "Envio incompleto ou interrompido.") } }
            } finally {
                closeData()
                target.delete()
                update { it.copy(activeUploads = 0) }
                uploadGate.release()
                pendingReply?.let { (code, message) -> runCatching { reply(code, message) } }
            }
        }
    }
}

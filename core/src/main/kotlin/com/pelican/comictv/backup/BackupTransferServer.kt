package com.pelican.comictv.backup

import fi.iki.elonen.NanoHTTPD
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import fi.iki.elonen.NanoHTTPD.Response.Status.*

class BackupTransferServer(private val host: String, private val directory: File,
    private val export: () -> File?, private val received: (File) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis) : Closeable {
    private val root = "/b/" + ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val gate = Semaphore(1)
    @Volatile private var active = false
    private var started = 0L
    private val server = object : NanoHTTPD(host, 0) {
        override fun serve(session: IHTTPSession): Response = try { handle(session) }
            catch (_: Exception) { reply(Response.Status.BAD_REQUEST, "Transferência interrompida ou inválida.") }
    }.apply {
        val delegate = NanoHTTPD.DefaultAsyncRunner(); val count = AtomicInteger()
        setAsyncRunner(object : NanoHTTPD.AsyncRunner {
            override fun exec(handler: NanoHTTPD.ClientHandler) { if (count.incrementAndGet() > 4) { count.decrementAndGet(); handler.close() } else delegate.exec(handler) }
            override fun closed(handler: NanoHTTPD.ClientHandler) { count.decrementAndGet(); delegate.closed(handler) }
            override fun closeAll() { delegate.closeAll() }
        })
    }
    val port get() = server.listeningPort
    val url get() = "http://$host:$port$root"
    fun expired() = clock() - started >= 15 * 60 * 1000L
    @Synchronized fun open() { check(!active); directory.mkdirs(); server.start(15000, true); started = clock(); active = true }
    @Synchronized override fun close() { active = false; server.stop() }
    private fun secure(response: NanoHTTPD.Response): NanoHTTPD.Response = response.apply {
        addHeader("Connection", "close"); addHeader("Cache-Control", "no-store"); addHeader("Referrer-Policy", "no-referrer")
        addHeader("X-Content-Type-Options", "nosniff"); addHeader("X-Frame-Options", "DENY")
        addHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
    }
    private fun reply(status: NanoHTTPD.Response.IStatus, text: String, html: Boolean = false) = secure(
        NanoHTTPD.newFixedLengthResponse(status, if (html) "text/html; charset=utf-8" else "text/plain; charset=utf-8", text))
    private fun handle(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.uri !in setOf(root, "$root/export", "$root/receive")) return reply(NOT_FOUND, "Sessão não encontrada.")
        if (!active || expired()) return reply(GONE, "Sessão encerrada.")
        val authority = "$host:$port"
        if (session.headers["host"] != authority || session.headers["origin"]?.let { it != "http://$authority" } == true)
            return reply(FORBIDDEN, "Origem inválida.")
        if (session.method == NanoHTTPD.Method.GET && session.uri == root) return reply(OK, PAGE, true)
        if (session.method == NanoHTTPD.Method.GET && session.uri == "$root/export") {
            val file = export()?.takeIf { it.isFile && it.length() in 1..BackupCodec.MAX_BYTES.toLong() }
                ?: return reply(NOT_FOUND, "Primeiro prepare o backup na TV.")
            return secure(NanoHTTPD.newFixedLengthResponse(OK, "application/octet-stream", file.inputStream(), file.length())).apply {
                addHeader("Content-Disposition", "attachment; filename=Comic-TV.ctvbackup")
            }
        }
        if (session.method != NanoHTTPD.Method.POST || session.uri != "$root/receive") return reply(METHOD_NOT_ALLOWED, "Método inválido.")
        val length = session.headers["content-length"]?.toLongOrNull()
        if (session.headers.containsKey("transfer-encoding") || length == null || length !in 1..BackupCodec.MAX_BYTES.toLong()) return reply(PAYLOAD_TOO_LARGE, "Envie backup de até 32 MiB com tamanho conhecido.")
        if (!gate.tryAcquire()) return reply(CONFLICT, "Outro envio está em andamento.")
        val target = File.createTempFile("backup-", ".upload", directory)
        try {
            if (directory.usableSpace < length + 64L * 1024 * 1024) return reply(INTERNAL_ERROR, "Espaço insuficiente.")
            target.outputStream().use { output ->
                var left = length; val buffer = ByteArray(16384)
                while (left > 0) {
                    if (!active || expired() || Thread.currentThread().isInterrupted) throw IOException("Sessão encerrada")
                    val count = session.inputStream.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                    if (count < 0) throw IOException("Envio incompleto"); output.write(buffer, 0, count); left -= count
                }
            }
            try {
                target.inputStream().use(BackupCodec::decode)
                if (!active || expired()) throw IOException("Sessão encerrada")
                received(target)
            } catch (_: Exception) {
                val invalid = object : NanoHTTPD.Response.IStatus { override fun getRequestStatus() = 422; override fun getDescription() = "422 Invalid Backup" }
                return reply(invalid, "Backup inválido ou sessão interrompida. Nada foi restaurado.")
            }
            return reply(OK, "Backup recebido. Confira e confirme a restauração na TV.")
        } finally { target.delete(); gate.release() }
    }
    companion object {
        private val PAGE = """<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Comic TV · Backup</title><style>body{background:#090a0c;color:white;font:18px system-ui;max-width:680px;margin:40px auto;padding:24px}button,a{display:inline-block;padding:16px;background:#ffbd59;color:black;border:0;border-radius:8px;margin:12px 0}input{display:block;margin:20px 0}</style><h1>COMIC TV · Backup</h1><p>Prepare o backup na TV e guarde uma cópia fora do aparelho.</p><a id="download">Baixar backup preparado</a><h2>Enviar backup para restauração</h2><p>Enviar apenas abre uma prévia na TV. A restauração exige confirmação no controle.</p><input id="file" type="file"><button id="send">Enviar backup · até 32 MiB</button><p id="status"></p><script>document.getElementById('download').href=location.pathname+'/export';document.getElementById('send').onclick=async()=>{const f=document.getElementById('file').files[0];const s=document.getElementById('status');if(!f||f.size>33554432){s.textContent='Selecione um backup de até 32 MiB.';return;}const b=document.getElementById('send');b.disabled=true;try{const r=await fetch(location.pathname+'/receive',{method:'POST',body:f});s.textContent=await r.text();}catch(e){s.textContent='Falha na transferência. Tente novamente.';}finally{b.disabled=false;}};</script></html>"""
    }
}

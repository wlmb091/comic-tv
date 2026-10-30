package com.pelican.comictv.browser

import android.net.Uri
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebView
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** A one-file, user-approved stream. No JavascriptInterface is exposed to web pages. */
internal object BrowserBlobTransfer {
    suspend fun transfer(web: WebView, request: BrowserDownloadRequest, target: File,
        progress: (Long, Long) -> Unit, headers: (BrowserTransferResult) -> Unit): BrowserTransferResult {
        try { return withTimeout(120_000) {
            val origin = withContext(Dispatchers.Main.immediate) { browserPageOrigin(web.url) }
                ?: throw IOException("Reabra a página que gerou o arquivo temporário.")
            check(browserBlobOrigin(request.url) == origin) { "O arquivo temporário pertence a outra página. Abra o link na aba de origem." }
            val token = UUID.randomUUID().toString(); val messages = Channel<String>(1)
            val ports = withContext(Dispatchers.Main.immediate) {
                web.createWebMessageChannel().also { pair -> pair[0].setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
                    override fun onMessage(port: WebMessagePort, message: WebMessage) {
                        val data = message.data
                        if (browserPageOrigin(web.url) != origin || data == null || data.length > 270_000 || !data.startsWith("$token|"))
                            messages.close(IOException("Resposta inválida da página de download."))
                        else if (messages.trySend(data).isFailure) messages.close(IOException("A página enviou dados fora de ordem."))
                    }
                }) }
            }
            val sink = BrowserBlobSink(target, request.maximumBytes)
            try {
                val quote = Gson()
                val script = """(function(){
                    const token=TOKEN, url=URL;
                    const receive=async function(event){
                        if(event.data!==token||!event.ports||event.ports.length!==1)return;
                        window.removeEventListener('message',receive);
                        const port=event.ports[0]; let ack=null;
                        port.onmessage=function(e){if(e.data===token+'|A'&&ack){const r=ack;ack=null;r();}};
                        const send=(value)=>new Promise(resolve=>{ack=resolve;port.postMessage(token+'|'+value);});
                        try{
                            const response=await fetch(url); const blob=await response.blob();
                            await send('M|'+blob.size+'|'+btoa(blob.type||'application/octet-stream'));
                            let seq=0;
                            for(let offset=0;offset<blob.size;offset+=196608){
                                const bytes=new Uint8Array(await blob.slice(offset,offset+196608).arrayBuffer());
                                let value='';for(let i=0;i<bytes.length;i+=8192)value+=String.fromCharCode.apply(null,bytes.subarray(i,i+8192));
                                await send('C|'+seq+'|'+btoa(value));seq++;
                            }
                            port.postMessage(token+'|E|'+seq);
                        }catch(error){port.postMessage(token+'|X|failed');}
                    };
                    window.addEventListener('message',receive);return 'ready';
                })();""".replace("TOKEN", quote.toJson(token)).replace("URL", quote.toJson(request.url))
                withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        web.evaluateJavascript(script) { value ->
                            if (continuation.isActive) {
                                if (value.contains("ready")) continuation.resume(Unit)
                                else continuation.resumeWithException(IOException("O site não permitiu preparar este download."))
                            }
                        }
                    }
                    web.postWebMessage(WebMessage(token, arrayOf(ports[1])), Uri.parse(origin))
                }
                var filename = BrowserDownloadNames.safe(request.suggestedName); var mime: String? = null
                while (true) {
                    val packet = messages.receive().split('|', limit = 4)
                    when (packet.getOrNull(1)) {
                        "M" -> {
                            val size = packet.getOrNull(2)?.toLongOrNull() ?: throw IOException("Tamanho inválido.")
                            mime = runCatching { String(Base64.getDecoder().decode(packet.getOrNull(3).orEmpty()), Charsets.UTF_8).take(120) }.getOrNull()
                            filename = BrowserDownloadNames.resolve(request.url, null, mime, request.suggestedName)
                            BrowserDownloadNames.validate(filename, mime)
                            withContext(Dispatchers.IO) { sink.start(size) }
                            headers(BrowserTransferResult(filename, size, null, mime)); progress(0, size)
                        }
                        "C" -> {
                            val seq = packet.getOrNull(2)?.toIntOrNull() ?: throw IOException("Sequência inválida.")
                            withContext(Dispatchers.IO) { sink.chunk(seq, packet.getOrNull(3).orEmpty()) }
                            progress(sink.bytes, sink.total)
                        }
                        "E" -> {
                            val result = withContext(Dispatchers.IO) { sink.finish(packet.getOrNull(2)?.toIntOrNull() ?: -1) }
                            return@withTimeout BrowserTransferResult(filename, result, null, mime)
                        }
                        else -> throw IOException("A página não conseguiu fornecer o arquivo. Reabra o link de download.")
                    }
                    withContext(Dispatchers.Main.immediate) { ports[0].postMessage(WebMessage("$token|A")) }
                }
                @Suppress("UNREACHABLE_CODE") error("Stream ended")
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { sink.close() }
                withContext(NonCancellable + Dispatchers.Main.immediate) { ports.forEach { runCatching { it.close() } }; messages.close() }
            }
        } } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive(); throw IOException("O site demorou para fornecer o arquivo temporário. Abra o download novamente.", timeout)
        }
    }
}

/** Flat, bounded protocol; a site cannot choose paths, native calls or arbitrary-sized messages. */
internal class BrowserBlobSink(private val target: File, private val maximum: Long) {
    var bytes = 0L; private set
    var total = -1L; private set
    private var next = 0; private var stream: FileOutputStream? = null
    fun start(size: Long) {
        if (stream != null || total >= 0 || size !in 0..maximum || size > BrowserPolicy.MAX_BYTES) throw IOException("Arquivo temporário excede o limite.")
        BrowserPolicy.checkSize(size, target.parentFile!!.usableSpace)
        if (target.parentFile!!.usableSpace - size < BrowserPolicy.RESERVE) throw IOException("Espaço insuficiente para o download.")
        total = size; stream = FileOutputStream(target)
    }
    fun chunk(sequence: Int, encoded: String) {
        if (stream == null || sequence != next || encoded.length > 262144) throw IOException("Parte de arquivo inválida.")
        val data = try { Base64.getDecoder().decode(encoded) } catch (_: Exception) { throw IOException("Dados de download inválidos.") }
        if (data.size > 196608 || bytes + data.size > total) throw IOException("Tamanho de download inconsistente.")
        BrowserPolicy.checkSize(bytes + data.size, target.parentFile!!.usableSpace - data.size)
        stream!!.write(data); bytes += data.size; next++
    }
    fun finish(sequence: Int): Long {
        if (stream == null || sequence != next || bytes != total) throw IOException("Download temporário incompleto.")
        stream!!.fd.sync(); close(); return bytes
    }
    fun close() { stream?.close(); stream = null }
}

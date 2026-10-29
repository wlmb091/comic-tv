package com.pelican.comictv.browser

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object BrowserPolicy {
    const val MAX_BYTES = 1024L * 1024 * 1024
    const val RESERVE = 64L * 1024 * 1024
    fun url(value: String): HttpUrl = value.trim().toHttpUrlOrNull()?.takeIf {
        it.username.isEmpty() && it.password.isEmpty()
    } ?: throw IOException("Use um endereço HTTP ou HTTPS sem credenciais.")
    fun filename(name: String): String {
        val clean = name.substringAfterLast('/').substringAfterLast('\\').filter { it.code >= 32 && it != ':' }.take(180)
        if (clean.substringAfterLast('.', "").lowercase() !in setOf("cbz", "zip", "cbr", "rar", "pdf", "epub"))
            throw IOException("Formato não aceito. Use CBZ, ZIP, CBR, RAR, PDF ou EPUB; APK não é permitido.")
        return clean
    }
    fun redirect(from: HttpUrl, location: String, count: Int): HttpUrl {
        if (count >= 5) throw IOException("Limite de redirecionamentos atingido.")
        val next = from.resolve(location)?.let { url(it.toString()) } ?: throw IOException("Redirecionamento inválido.")
        if (from.isHttps && !next.isHttps) throw IOException("Redirecionamento HTTPS para HTTP bloqueado.")
        return next
    }
    fun checkSize(bytes: Long, available: Long) {
        if (bytes > MAX_BYTES) throw IOException("O arquivo excede o limite de 1 GiB.")
        if (available < RESERVE) throw IOException("Espaço insuficiente: reserva mínima de 64 MiB.")
    }
}

/** Dedicated client: redirects never carry a previous destination's cookies or authorization. */
internal class BrowserDownloader {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    @Volatile private var call: Call? = null
    fun cancel() { call?.cancel() }
    fun download(url: String, target: File, cookie: (String) -> String?, userAgent: String,
                 active: () -> Unit, progress: (Long, Long) -> Unit) {
        try {
            var current = BrowserPolicy.url(url)
            var redirects = 0
            while (true) {
                active()
                val request = Request.Builder().url(current).header("User-Agent", userAgent.filter { it.code in 32..126 }.take(512))
                cookie(current.toString())?.takeIf { it.none { c -> c == '\r' || c == '\n' } }?.let { request.header("Cookie", it) }
                val nextCall = client.newCall(request.build()); call = nextCall
                active()
                nextCall.execute().use { response ->
                    if (response.code in setOf(301,302,303,307,308)) {
                        current = BrowserPolicy.redirect(current, response.header("Location") ?: throw IOException("Redirecionamento sem destino."), redirects++)
                    } else {
                        if (!response.isSuccessful) throw IOException("O servidor recusou o download (HTTP ${response.code}).")
                        val body = response.body
                        val length = body.contentLength()
                        BrowserPolicy.checkSize(length, target.parentFile!!.usableSpace)
                        if (length >= 0 && target.parentFile!!.usableSpace - length < BrowserPolicy.RESERVE) throw IOException("Espaço insuficiente para o arquivo.")
                        body.byteStream().use { input -> target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024); var total = 0L
                            while (true) {
                                active(); val read = input.read(buffer); if (read < 0) break
                                total += read; BrowserPolicy.checkSize(total, target.parentFile!!.usableSpace)
                                output.write(buffer, 0, read); progress(total, length)
                            }
                            if (length >= 0 && total != length) throw IOException("Download incompleto.")
                        } }
                        active(); return
                    }
                }
            }
        } catch (error: Exception) { target.delete(); throw error }
        finally { call = null }
    }
}

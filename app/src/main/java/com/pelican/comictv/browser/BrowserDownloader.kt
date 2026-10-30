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
    /** Address bar input is not necessarily a URL. Never send a single word to DNS. */
    fun destination(input: String): HttpUrl {
        val text = input.trim().take(4096)
        if (text.isBlank()) throw IOException("Digite um endereço ou uma pesquisa.")
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(text)) return url(text)
        if (text.none { it.isWhitespace() } && (text.substringBefore('/').contains('.') ||
                text.startsWith("localhost") || text.startsWith("["))) return url("https://$text")
        return url("https://www.google.com/search").newBuilder().addQueryParameter("q", text).build()
    }
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

internal data class BrowserDownloadRequest(val url: String, val suggestedName: String, val userAgent: String,
    val referer: String? = null, val validator: String? = null, val maximumBytes: Long = BrowserPolicy.MAX_BYTES)
internal data class BrowserTransferResult(val filename: String, val totalBytes: Long, val validator: String?, val mime: String?)

internal object BrowserDownloadNames {
    fun safe(value: String): String = value.substringAfterLast('/').substringAfterLast('\\')
        .filter { it.code >= 32 && it !in ":\"<>|?*" }.trim().trim('.').take(160).ifBlank { "arquivo.bin" }
    fun validate(name: String, mime: String?) {
        if (name.substringAfterLast('.', "").lowercase() in setOf("apk", "apkm", "apks", "xapk") ||
            mime?.substringBefore(';')?.trim()?.lowercase() == "application/vnd.android.package-archive")
            throw IOException("Downloads de aplicativos Android não são permitidos neste navegador.")
    }
    fun importable(value: String) = value.substringAfterLast('.', "").lowercase() in setOf("cbz", "zip", "cbr", "rar", "pdf", "epub")
    fun resolve(url: String, disposition: String?, mime: String?, suggested: String): String {
        val encoded = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition.orEmpty())?.groupValues?.get(1)
        val plain = Regex("filename\\s*=\\s*(?:\"([^\"]+)\"|([^;]+))", RegexOption.IGNORE_CASE).find(disposition.orEmpty())
            ?.let { it.groupValues[1].ifBlank { it.groupValues[2] }.trim() }
        val decoded = encoded?.let { runCatching { java.net.URLDecoder.decode(it.trim().trim('\"'), "UTF-8") }.getOrNull() }
        var name = safe(decoded ?: plain ?: suggested.ifBlank { url.substringBefore('?').substringAfterLast('/') })
        val extension = when (mime?.substringBefore(';')?.lowercase()) {
            "application/pdf" -> "pdf"; "application/epub+zip" -> "epub"
            "application/zip", "application/x-zip-compressed" -> "zip"
            "application/vnd.rar", "application/x-rar-compressed" -> "rar"
            else -> null
        }
        if (extension != null && ('.' !in name || name.endsWith(".bin", true))) name = name.substringBeforeLast('.', name) + ".$extension"
        return name
    }
}
internal fun browserDownloadReferer(page: String?, destination: String): String? {
    val from = page?.toHttpUrlOrNull() ?: return null
    val to = destination.toHttpUrlOrNull() ?: return null
    if (from.username.isNotEmpty() || from.password.isNotEmpty() || from.isHttps && !to.isHttps) return null
    return if (from.scheme == to.scheme && from.host == to.host && from.port == to.port)
        from.newBuilder().fragment(null).build().toString()
    else from.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
}

/** Dedicated client; destination cookies are re-evaluated after each redirect. */
internal class BrowserDownloader {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    @Volatile private var call: Call? = null
    fun cancel() { call?.cancel() }
    fun download(url: String, target: File, cookie: (String) -> String?, userAgent: String,
                 active: () -> Unit, progress: (Long, Long) -> Unit) {
        try { transfer(BrowserDownloadRequest(url, "arquivo.bin", userAgent), target, cookie, active, progress, {}) }
        catch (error: Exception) { target.delete(); throw error }
    }
    fun transfer(input: BrowserDownloadRequest, target: File, cookie: (String) -> String?, active: () -> Unit,
        progress: (Long, Long) -> Unit, headers: (BrowserTransferResult) -> Unit): BrowserTransferResult {
        var current = BrowserPolicy.url(input.url); var redirects = 0
        val offset = if (input.validator.isNullOrBlank()) 0L else target.takeIf { it.isFile }?.length() ?: 0L
        require(input.maximumBytes in 1..BrowserPolicy.MAX_BYTES)
        try {
            while (true) {
                active()
                val request = Request.Builder().url(current).header("Accept-Encoding", "identity")
                    .header("User-Agent", input.userAgent.filter { it.code in 32..126 }.take(512))
                cookie(current.toString())?.takeIf { it.none { c -> c == '\r' || c == '\n' } }?.let { request.header("Cookie", it) }
                browserDownloadReferer(input.referer, current.toString())?.let { request.header("Referer", it) }
                if (offset > 0 && !input.validator.isNullOrBlank() && input.validator.none { it == '\r' || it == '\n' }) {
                    request.header("Range", "bytes=$offset-"); request.header("If-Range", input.validator)
                }
                val nextCall = client.newCall(request.build()); call = nextCall; active()
                nextCall.execute().use { response ->
                    if (response.code in setOf(301,302,303,307,308)) {
                        current = BrowserPolicy.redirect(current, response.header("Location") ?: throw IOException("Redirecionamento sem destino."), redirects++)
                    } else {
                        if (!response.isSuccessful) throw IOException("O servidor recusou o download (HTTP ${response.code}). Reabra a página e tente novamente.")
                        val type = response.header("Content-Type")?.substringBefore(';')?.lowercase()
                        val disposition = response.header("Content-Disposition")
                        if (type == "text/html" && !disposition.orEmpty().contains("attachment", true))
                            throw IOException("O site retornou uma página, não o arquivo. Confira login/permissão e clique no link de download na página.")
                        val body = response.body; val length = body.contentLength()
                        var start = 0L; var expectedTotal = length
                        if (response.code == 206) {
                            val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                                ?: throw IOException("O servidor não confirmou a parte do arquivo.")
                            start = range.groupValues[1].toLongOrNull() ?: throw IOException("Intervalo inválido.")
                            val end = range.groupValues[2].toLongOrNull() ?: throw IOException("Intervalo inválido.")
                            expectedTotal = range.groupValues[3].toLongOrNull() ?: throw IOException("Tamanho inválido.")
                            if (start != offset || end < start || end >= expectedTotal || length >= 0 && end - start + 1 != length)
                                throw IOException("O servidor retornou uma parte diferente da solicitada.")
                            val etag = response.header("ETag")
                            if (input.validator?.startsWith("\"") == true && etag != null && input.validator != etag)
                                throw IOException("O arquivo mudou. Reinicie o download para não misturar versões.")
                        }
                        if (expectedTotal > input.maximumBytes) throw IOException("O arquivo excede o limite disponível para downloads.")
                        BrowserPolicy.checkSize(expectedTotal, target.parentFile!!.usableSpace)
                        if (expectedTotal >= 0 && target.parentFile!!.usableSpace - (expectedTotal - start) < BrowserPolicy.RESERVE)
                            throw IOException("Espaço insuficiente para o arquivo.")
                        val validator = response.header("ETag")?.takeUnless { it.startsWith("W/") } ?: response.header("Last-Modified")
                        val name = BrowserDownloadNames.resolve(current.toString(), disposition, type, input.suggestedName)
                        BrowserDownloadNames.validate(name, type)
                        java.io.FileOutputStream(target, start > 0).use { output ->
                        // Publish the new validator only after a full-response restart truncates old bytes.
                        headers(BrowserTransferResult(name, expectedTotal, validator, type))
                        body.byteStream().use { stream ->
                            val buffer = ByteArray(64 * 1024); var total = start
                            progress(total, expectedTotal)
                            while (true) {
                                active(); val read = stream.read(buffer); if (read < 0) break
                                total += read
                                if (total > input.maximumBytes) throw IOException("Limite de tamanho dos downloads atingido.")
                                BrowserPolicy.checkSize(total, target.parentFile!!.usableSpace - read)
                                output.write(buffer, 0, read); progress(total, expectedTotal)
                            }
                            if (expectedTotal >= 0 && total != expectedTotal) throw IOException("Download incompleto. Você pode tentar retomar.")
                            output.fd.sync()
                        } }
                        active(); return BrowserTransferResult(name, target.length(), validator, type)
                    }
                }
            }
        } finally { call = null }
    }
}

internal fun browserPageOrigin(url: String?): String? = url?.toHttpUrlOrNull()?.let {
    if (it.username.isNotEmpty() || it.password.isNotEmpty()) null else it.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
}
internal fun browserBlobOrigin(url: String): String? = if (url.startsWith("blob:")) browserPageOrigin(url.removePrefix("blob:")) else null

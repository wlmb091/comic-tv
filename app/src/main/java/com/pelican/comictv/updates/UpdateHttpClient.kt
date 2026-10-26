package com.pelican.comictv.updates

import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

internal class UpdateHttpClient(client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private fun allowed(url: String): URI {
        val value = URI(url)
        require(value.scheme == "https" && value.rawUserInfo == null && value.fragment == null && value.port in setOf(-1, 443) &&
            value.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "raw.githubusercontent.com")) {
            "Endereço de atualização não autorizado."
        }
        return value
    }
    private suspend fun response(url: String): Response {
        var target = allowed(url)
        repeat(6) {
            currentCoroutineContext().ensureActive()
            val response = http.newCall(Request.Builder().url(target.toString()).header("User-Agent", "ComicTV-Updater/1").build()).await()
            if (response.code in setOf(301, 302, 303, 307, 308)) {
                val location = response.header("Location")
                response.close()
                target = allowed(target.resolve(location ?: throw IOException("Redirecionamento de atualização inválido.")).toString())
            } else {
                if (!response.isSuccessful) { val code = response.code; response.close(); throw IOException("Canal de atualização indisponível (HTTP $code). Tente novamente mais tarde.") }
                return response
            }
        }
        throw IOException("Redirecionamentos demais no canal de atualização.")
    }
    suspend fun text(url: String, limit: Int = UpdateManifestCodec.MAX_BYTES): ByteArray = withContext(Dispatchers.IO) {
        ByteArrayOutputStream().use { output -> transfer(url, limit.toLong(), output) { }; output.toByteArray() }
    }
    suspend fun download(url: String, target: File, limit: Long, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        target.outputStream().use { output -> transfer(url, limit, output, progress); output.fd.sync() }
    }
    private suspend fun transfer(url: String, limit: Long, output: OutputStream, progress: (Long) -> Unit) = coroutineScope {
        require(limit in 1..UpdateManifest.MAX_APK_BYTES)
        response(url).use { response ->
            val closeOnCancel = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { response.close() } }
            try {
                val body = requireNotNull(response.body)
                require(body.contentLength() <= limit) { "Arquivo de atualização excede o limite." }
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024); var total = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        ensureActive()
                        if (count < 0) break
                        require(count.toLong() <= limit - total) { "Arquivo de atualização excede o limite." }
                        output.write(buffer, 0, count); total += count; progress(total)
                    }
                    require(total > 0) { "O canal retornou um arquivo vazio." }
                }
            } finally { closeOnCancel.cancel() }
        }
    }
}

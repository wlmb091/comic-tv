package com.pelican.comictv.reader.narration

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface PageTranslation {
    suspend fun translate(text: String): String
    fun close()
}
data class TranslationLanguage(val code: String, val label: String)

/** LibreTranslate-compatible LAN client. No cloud hostnames, redirects or automatic language-model downloads. */
class LocalTranslationClient(url: String, private val language: String, private val apiKey: String = "") : PageTranslation {
    private val base = localTranslationUrl(url)
    private val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()
    init { require(language.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z]{2,8})?")) && apiKey.length <= 300) }
    override suspend fun translate(text: String): String {
        require(text.length <= 1800)
        val payload = JsonObject().apply {
            addProperty("q", text); addProperty("source", language); addProperty("target", "pt"); addProperty("format", "text")
            if (apiKey.isNotBlank()) addProperty("api_key", apiKey)
        }
        val response = request(Request.Builder().url(base.newBuilder().addPathSegment("translate").build())
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())
        return try {
            val value = JsonParser.parseString(response).asJsonObject.get("translatedText")
            if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString || value.asString.isBlank()) throw IOException()
            value.asString
        } catch (_: Exception) { throw IOException("O tradutor local não retornou um texto válido. Confira os idiomas instalados no computador.") }
    }
    suspend fun languages(): List<TranslationLanguage> {
        val response = request(Request.Builder().url(base.newBuilder().addPathSegment("languages").build()).build())
        return try {
            val entries = JsonParser.parseString(response).asJsonArray
            require(entries.size() <= 100)
            val codes = entries.map { it.asJsonObject.get("code").asString }.filter { it.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z]{2,8})?")) }.toSet()
            require("pt" in codes)
            codes.filter { it != "pt" }.map { code ->
                val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.forLanguageTag("pt-BR"))
                TranslationLanguage(code, name.replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-BR")) })
            }.sortedBy { it.label }.also { require(it.isNotEmpty()) }
        } catch (_: Exception) { throw IOException("O servidor precisa disponibilizar português e pelo menos um idioma de origem. Prepare os modelos no computador.") }
    }
    private suspend fun request(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Não foi possível conectar ao tradutor local. Confira computador, endereço e rede."))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw IOException("O tradutor local recusou a solicitação (HTTP ${it.code}). Confira idioma e chave de acesso.")
                        val output = ByteArrayOutputStream()
                        it.body.byteStream().use { input ->
                            val buffer = ByteArray(4096)
                            while (true) {
                                val size = input.read(buffer); if (size < 0) break
                                if (output.size() + size > 65536) throw IOException("A resposta do tradutor local excedeu o limite.")
                                output.write(buffer, 0, size)
                            }
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (e: Exception) {
                    response.close()
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
    override fun close() { client.dispatcher.cancelAll(); client.connectionPool.evictAll() }
}

internal fun localTranslationUrl(value: String): HttpUrl {
    require(value.length <= 512) { "Endereço muito longo." }
    val url = value.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Informe http://IP-DO-COMPUTADOR:5000.")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Use apenas o endereço local, sem senha ou parâmetros na URL." }
    val labels = url.host.split('.')
    val parts = labels.mapNotNull { it.toIntOrNull()?.takeIf { number -> number in 0..255 } }
    val local = url.host == "localhost" || (labels.size == 4 && parts.size == 4 && (parts[0] == 10 || parts[0] == 127 ||
        parts[0] == 192 && parts[1] == 168 || parts[0] == 172 && parts[1] in 16..31 || parts[0] == 169 && parts[1] == 254))
    require(local) { "Use o IPv4 do computador na rede local. Serviços públicos de tradução não são usados por esta função." }
    return url
}

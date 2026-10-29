package com.pelican.comictv.metadata

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class MetadataProvider(val label: String) { OPEN_LIBRARY("Open Library"), ANILIST("AniList") }
data class WorkRating(val value: Double, val scale: Int, val count: Int?)
data class MetadataWork(val title: String, val authors: String, val year: String, val format: String,
    val identifiers: String, val synopsis: String, val url: String, val provider: MetadataProvider, val rating: WorkRating?)
data class MetadataBatch(val works: List<MetadataWork>, val cached: Boolean = false, val message: String? = null)

internal object MetadataParser {
    fun plain(value: String): String = value.take(12000)
        .replace(Regex("<(script|style)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace(Regex("\\s+"), " ").trim().take(4000)
    private fun JsonObject.s(key: String) = get(key)?.takeUnless { it.isJsonNull }?.runCatching { asString }?.getOrNull().orEmpty()
    private fun JsonObject.n(key: String) = s(key).toDoubleOrNull()
    fun openLibrary(body: String): List<MetadataWork> = JsonParser.parseString(body).asJsonObject.getAsJsonArray("docs")?.mapNotNull { raw ->
        val o = raw.asJsonObject
        val key = o.s("key").removePrefix("/works/")
        if (!key.matches(Regex("OL[0-9]+W")) || o.s("title").isBlank()) return@mapNotNull null
        val count = o.n("ratings_count")?.toInt()?.takeIf { it > 0 }
        MetadataWork(plain(o.s("title")), o.getAsJsonArray("author_name")?.take(8)?.joinToString { plain(it.asString) }.orEmpty(),
            o.s("first_publish_year"), "Obra · várias edições", o.getAsJsonArray("isbn")?.take(3)?.joinToString { it.asString }.orEmpty(), "",
            "https://openlibrary.org/works/$key", MetadataProvider.OPEN_LIBRARY,
            o.n("ratings_average")?.takeIf { it.isFinite() && it in 0.0..5.0 && count != null }?.let { WorkRating(it, 5, count) })
    }?.take(6).orEmpty()
    fun aniList(body: String): List<MetadataWork> {
        val root = JsonParser.parseString(body).asJsonObject
        if (root.has("errors")) throw IOException("AniList retornou erro")
        return root.getAsJsonObject("data")?.getAsJsonObject("Page")?.getAsJsonArray("media")?.mapNotNull { raw ->
            val o = raw.asJsonObject
            val id = o.n("id")?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
            if (o.s("type") != "MANGA") return@mapNotNull null
            val title = o.getAsJsonObject("title") ?: return@mapNotNull null
            val name = title.s("english").ifBlank { title.s("romaji") }.ifBlank { title.s("native") }
            if (name.isBlank()) return@mapNotNull null
            val staff = o.getAsJsonObject("staff")?.getAsJsonArray("edges")?.take(8)?.mapNotNull { edge ->
                edge.asJsonObject.getAsJsonObject("node")?.getAsJsonObject("name")?.s("full")?.takeIf { it.isNotBlank() }
            }?.distinct()?.joinToString().orEmpty()
            MetadataWork(plain(name), plain(staff), o.getAsJsonObject("startDate")?.s("year").orEmpty(),
                "Mangá / obra · ${o.s("format")}", "AniList $id", plain(o.s("description")), "https://anilist.co/manga/$id", MetadataProvider.ANILIST,
                o.n("averageScore")?.takeIf { it.isFinite() && it > 0 && it <= 100 }?.let { WorkRating(it, 100, null) })
        }?.take(6).orEmpty()
    }
}

/** Human-triggered read-only lookup. Never imports or associates a result with an edition. */
class MetadataRepository(private val cacheDir: File) {
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private companion object {
        val gate = Mutex()
        var lastRequest = 0L
    }
    suspend fun search(query: String, provider: MetadataProvider): MetadataBatch = withContext(Dispatchers.IO) {
        val text = query.trim().take(200)
        require(text.length >= 2) { "Digite ao menos 2 caracteres." }
        gate.withLock {
            cacheDir.mkdirs()
            val hash = MessageDigest.getInstance("SHA-256").digest((provider.name + text).toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(cacheDir, "$hash.json")
            fun parse(body: String) = if (provider == MetadataProvider.OPEN_LIBRARY) MetadataParser.openLibrary(body) else MetadataParser.aniList(body)
            fun cached(): List<MetadataWork>? = runCatching { if (file.isFile && file.length() <= 262144) parse(file.readText()) else null }.getOrNull()
            val old = cached()
            if (old != null && System.currentTimeMillis() - file.lastModified() in 0 until 86400000L) return@withLock MetadataBatch(old, true)
            try {
                delay((1100 - (System.currentTimeMillis() - lastRequest)).coerceAtLeast(0))
                currentCoroutineContext().ensureActive()
                lastRequest = System.currentTimeMillis()
                val request = if (provider == MetadataProvider.OPEN_LIBRARY) {
                    Request.Builder().url("https://openlibrary.org/search.json".toHttpUrl().newBuilder()
                        .addQueryParameter("q", text).addQueryParameter("limit", "6")
                        .addQueryParameter("fields", "key,title,author_name,first_publish_year,isbn,ratings_average,ratings_count").build()).build()
                } else {
                    val payload = JsonObject().apply {
                        addProperty("query", "query(\u0024search:String!){Page(page:1,perPage:6){media(search:\u0024search,type:MANGA){id type format title{romaji english native} startDate{year} description(asHtml:false) averageScore staff(perPage:8){edges{node{name{full}}}}}}}")
                        add("variables", JsonObject().apply { addProperty("search", text) })
                    }
                    Request.Builder().url("https://graphql.anilist.co").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                }.newBuilder().header("User-Agent", "ComicTV/1.3 (personal human lookup)").header("Accept", "application/json").build()
                val body = execute(request)
                val works = parse(body)
                currentCoroutineContext().ensureActive()
                // Disposable cache, at most16 small responses. No provider/user state is backed up.
                runCatching {
                    file.writeText(body)
                    cacheDir.listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }?.drop(16)?.forEach { it.delete() }
                }
                MetadataBatch(works)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { MetadataBatch(old.orEmpty(), old != null, if (old != null) "Sem conexão: cópia anterior; pode estar desatualizada." else "Não foi possível consultar ${provider.label}. Tente novamente mais tarde.") }
        }
    }
    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        val body = it.body ?: throw IOException("Resposta vazia")
                        if (body.contentLength() > 262144) throw IOException("Resposta muito grande")
                        val source = body.source()
                        val buffer = okio.Buffer()
                        while (true) {
                            val n = source.read(buffer, minOf(8192L, 262145 - buffer.size))
                            if (n == -1L) break
                            if (buffer.size > 262144) throw IOException("Resposta muito grande")
                        }
                        buffer.readUtf8()
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
}

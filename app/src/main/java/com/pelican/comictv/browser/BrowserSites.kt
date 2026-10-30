package com.pelican.comictv.browser

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.jsoup.Jsoup

internal data class BrowserSite(val id: String, val name: String, val home: String, val kind: String) {
    val icon get() = "$home/favicon.ico"
    fun searchUrl(query: String): String {
        val q = URLEncoder.encode(query.trim().take(300), "UTF-8")
        return when (kind) {
            "wordpress" -> "$home/?s=$q"
            "archive" -> "$home/search?query=$q"
            "blogger" -> "$home/search?q=$q"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode("site:${URI(home).host} ${query.trim().take(300)}", "UTF-8")
        }
    }
}
internal data class BrowserSiteHit(val title: String, val url: String, val cover: String? = null)

/** Only sites explicitly selected by the user. These links do not install extensions. */
internal object BrowserSites {
    val all = listOf(
        BrowserSite("sq", "Só Quadrinhos", "https://site.soquadrinhos.com", "wordpress"),
        BrowserSite("ds", "DS Club", "https://site.ds-club.net", "manual"),
        BrowserSite("dc", "DC Infinita", "https://dcinfinita.blogspot.com", "blogger"),
        BrowserSite("apresenta", "SQ Apresenta", "https://sq-apresenta.blogspot.com", "blogger"),
        BrowserSite("marvel", "Poder Marvel", "https://podermarvel.blogspot.com", "blogger"),
        BrowserSite("renegados", "Renegados Comics", "https://renegadoscomics.blogspot.com", "blogger"),
        BrowserSite("archive", "Internet Archive", "https://archive.org", "archive"),
    )
    private fun ownUrl(site: BrowserSite, url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host.equals(URI(site.home).host, true) && uri.userInfo == null
    }.getOrDefault(false)
    private fun imageUrl(url: String): String? = url.takeIf { it.length <= 4096 && runCatching {
        val uri = URI(it); uri.scheme == "https" && uri.host != null && uri.userInfo == null
    }.getOrDefault(false) }

    fun parseHtml(site: BrowserSite, html: String): List<BrowserSiteHit> {
        val doc = Jsoup.parse(html, site.home)
        // Blogger templates use post-title/entry-title. Never follow download links here.
        return doc.select("h3.post-title a[href], h3.entry-title a[href], h2.post-title a[href], h2.entry-title a[href]")
            .mapNotNull { title ->
                val url = title.absUrl("href")
                if (!ownUrl(site, url) || title.text().isBlank()) null else {
                    val post = title.parents().firstOrNull { it.hasClass("post") || it.hasClass("hentry") }
                    val image = post?.selectFirst(".post-body img, .entry-content img")
                    BrowserSiteHit(title.text().take(200), url,
                        image?.let { imageUrl(it.absUrl("data-src").ifBlank { it.absUrl("src") }) })
                }
            }.distinctBy { it.url }.take(6)
    }
    fun parseJson(site: BrowserSite, text: String): List<BrowserSiteHit> {
        val root = JsonParser.parseString(text)
        return if (site.kind == "archive") root.asJsonObject.getAsJsonObject("response")?.getAsJsonArray("docs")
            ?.mapNotNull { value ->
                val row = value.asJsonObject
                val id = row.get("identifier")?.asString.orEmpty()
                if (!id.matches(Regex("[A-Za-z0-9._-]{1,200}"))) null else {
                    val title = row.get("title")?.let { if (it.isJsonArray) it.asJsonArray.firstOrNull()?.asString else it.asString }.orEmpty()
                    BrowserSiteHit(title.ifBlank { id }.take(200), "https://archive.org/details/$id", "https://archive.org/services/img/$id")
                }
            }?.take(6).orEmpty()
        else root.asJsonArray.mapNotNull { value ->
            val row = value.asJsonObject; val url = row.get("url")?.asString.orEmpty()
            if (!ownUrl(site, url)) null else BrowserSiteHit(Jsoup.parse(row.get("title")?.asString.orEmpty()).text().take(200), url)
        }.distinctBy { it.url }.take(6)
    }
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS).followRedirects(false).build()

    suspend fun search(site: BrowserSite, query: String): List<BrowserSiteHit> {
        require(site in all && query.isNotBlank())
        val q = URLEncoder.encode(query.trim().take(300), "UTF-8")
        val endpoint = when (site.kind) {
            "wordpress" -> "${site.home}/wp-json/wp/v2/search?search=$q&per_page=6&type=post"
            "archive" -> "${site.home}/advancedsearch.php?q=" + URLEncoder.encode("(${query.trim().take(300)}) AND mediatype:texts", "UTF-8") + "&output=json&rows=6&fl%5B%5D=identifier&fl%5B%5D=title"
            "blogger" -> site.searchUrl(query)
            else -> error("Abra a pesquisa no navegador.")
        }
        val text = suspendCancellableCoroutine<String> { continuation ->
            val call = client.newCall(Request.Builder().url(endpoint).header("User-Agent", "ComicTV/1.4 (site search)").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            check(it.isSuccessful) { "Site respondeu HTTP ${it.code}." }
                            val source = it.body.source()
                            check(!source.request(1_048_577)) { "Página excede o limite da busca." }
                            source.readUtf8()
                        }
                        if (continuation.isActive) continuation.resume(body)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
        return if (site.kind == "blogger") parseHtml(site, text) else parseJson(site, text)
    }
}

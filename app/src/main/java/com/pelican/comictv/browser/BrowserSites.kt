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
        BrowserSite("site-multiversohq-com", "Multiverso HQ", "https://multiversohq.com", "manual"),
        BrowserSite("site-galaxiadosquadrinhos-com-br", "Galáxia dos Quadrinhos", "https://www.galaxiadosquadrinhos.com.br", "manual"),
        BrowserSite("site-scandehqs-blogspot-com", "Scan de HQs", "https://scandehqs.blogspot.com", "manual"),
        BrowserSite("site-hq-now-com", "HQ Now!", "https://www.hq-now.com", "manual"),
        BrowserSite("site-artehqs-com-br", "Arte HQs", "http://www.artehqs.com.br", "manual"),
        BrowserSite("site-tudohqemanga-com-br", "Tudo HQ e Mangá", "https://tudohqemanga.com.br", "manual"),
        BrowserSite("site-aquilesgrego-blogspot-com", "Aquiles Grego", "https://aquilesgrego.blogspot.com", "manual"),
        BrowserSite("site-aibq-com", "Aibq", "https://aibq.com", "manual"),
        BrowserSite("site-comicsenespanhol-blogspot-com", "Comics en Espanhol", "https://comicsenespanhol.blogspot.com", "manual"),
        BrowserSite("site-comics-all-com", "ComicsAll", "https://comics-all.com", "manual"),
        BrowserSite("site-comicbookplus-com", "Comic Plus", "https://comicbookplus.com", "manual"),
        BrowserSite("site-comics-na-web-blogspot-com", "Comics na Web", "https://comics-na-web.blogspot.com", "manual"),
        BrowserSite("site-digitalcomicmuseum-com", "Digital Comic Museum", "https://digitalcomicmuseum.com", "manual"),
        BrowserSite("site-guiaebal-com", "Ebal", "https://guiaebal.com", "manual"),
        BrowserSite("site-europeanclassiccomic-blogspot-com", "EuropeanClassicComic", "https://europeanclassiccomic.blogspot.com", "manual"),
        BrowserSite("site-emporiobonelli-blogspot-com", "Emporio Bonelli", "https://emporiobonelli.blogspot.com", "manual"),
        BrowserSite("site-epistarsehqs-blogspot-com", "EpistarseHqs", "https://epistarsehqs.blogspot.com", "manual"),
        BrowserSite("site-fantasmabrasil-blogspot-com", "Fantasma Brasil", "https://fantasmabrasil.blogspot.com", "manual"),
        BrowserSite("site-fummetidipoaco-blogspot-com", "Fumetti di Paco", "https://fummetidipoaco.blogspot.com", "manual"),
        BrowserSite("site-guardioesdoglobo-sq-blogspot-com", "Guardiões do Globo", "https://guardioesdoglobo-sq.blogspot.com", "manual"),
        BrowserSite("site-gibiscuits-blogspot-com", "Gibiscuits", "https://gibiscuits.blogspot.com", "manual"),
        BrowserSite("site-gibisclassicos-blogspot-com", "Gibis Classicos", "https://gibisclassicos.blogspot.com", "manual"),
        BrowserSite("site-getcomics-info", "GetComics", "https://getcomics.info", "manual"),
        BrowserSite("site-hqpoint-blogspot-com", "HQ Point", "https://hqpoint.blogspot.com", "manual"),
        BrowserSite("site-hqvintage-blogspot-com", "HQ Vintage", "https://hqvintage.blogspot.com", "manual"),
        BrowserSite("site-osinvisiveishq-blogspot-com", "Invisiveis", "https://osinvisiveishq.blogspot.com", "manual"),
        BrowserSite("site-kenparkerbr-blogspot-com", "Ken Parker", "https://kenparkerbr.blogspot.com", "manual"),
        BrowserSite("site-komikerbr-blogspot-com", "Komikerbr", "https://komikerbr.blogspot.com", "manual"),
        BrowserSite("site-kuroscan-blogspot-com", "Kuro Scan", "https://kuroscan.blogspot.com", "manual"),
        BrowserSite("site-lamansion-crg-net", "La Mansion CRG", "https://lamansion-crg.net/forum", "manual"),
        BrowserSite("site-mdhq-blogspot-com", "Muchileiros dos Quadrinhos", "https://mdhq.blogspot.com", "manual"),
        BrowserSite("site-ndrangheta-br-blogspot-com", "Ndrangheta & Deck'Art", "https://ndrangheta-br.blogspot.com", "manual"),
        BrowserSite("site-newspapercomicstripsblog-wordpress-com", "News Paper Comics Strips", "https://newspapercomicstripsblog.wordpress.com", "manual"),
        BrowserSite("site-oldcomicsworld-blogspot-com", "OldComics", "https://oldcomicsworld.blogspot.com", "manual"),
        BrowserSite("site-quadrinhosinglorios-blogspot-com", "Quadrinhos Inglorios", "https://quadrinhosinglorios.blogspot.com", "manual"),
        BrowserSite("site-revolucaodosquadrinhos-blogspot-com", "Revolução dos Quadrinhos", "https://revolucaodosquadrinhos.blogspot.com", "manual"),
        BrowserSite("site-rockquadrinhosscans-blogspot-com", "Rock e Quadrinhos", "https://rockquadrinhosscans.blogspot.com", "manual"),
        BrowserSite("site-0scanbau-blogspot-com", "Scan Bau", "https://0scanbau.blogspot.com", "manual"),
        BrowserSite("site-soufadotex-blogspot-com", "Sou Fa do Tex", "https://soufadotex.blogspot.com", "manual"),
        BrowserSite("site-trad-mania-blogspot-com", "Trad Mania", "https://trad-mania.blogspot.com", "manual"),
        BrowserSite("site-tralhasvarias-blogspot-com", "Tralhas Varias", "https://tralhasvarias.blogspot.com", "manual"),
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

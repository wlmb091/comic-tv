package com.pelican.comictv.browser

import android.webkit.WebView
import com.google.gson.Gson
import java.io.IOException
import java.util.UUID

internal object BrowserBlobPage {
    private val gson = Gson()
    private var install: String? = null
    private data class Prepared(val ready: Boolean = false, val name: String? = null)
    fun validKey(key: String?) = key != null && BrowserDownloads.validId(key)
    fun prepare(web: WebView, request: BrowserDownloadRequest, complete: (BrowserDownloadRequest?) -> Unit) {
        val origin = browserPageOrigin(web.url)
        if (origin == null || browserBlobOrigin(request.url) != origin) { complete(null); return }
        val key = UUID.randomUUID().toString()
        try {
            val script = install ?: web.context.assets.open("browser-blob.js").bufferedReader().use { it.readText() }.also { install = it }
            web.evaluateJavascript(script + "\nwindow.__comicTvBlobV1.prepare(${gson.toJson(request.url)},${gson.toJson(key)},${request.maximumBytes});") { value ->
                val result = runCatching { gson.fromJson(gson.fromJson(value, String::class.java), Prepared::class.java) }.getOrNull()
                if (result?.ready != true || browserPageOrigin(web.url) != origin) { release(web, key); complete(null) }
                else try {
                    val name = BrowserBlobFile.name(result.name, request.suggestedName, null)
                    complete(request.copy(suggestedName = name, blobKey = key))
                } catch (_: IOException) { release(web, key); complete(null) }
            }
        } catch (_: Exception) { release(web, key); complete(null) }
    }
    fun release(web: WebView, key: String?) {
        if (!validKey(key)) return
        runCatching { web.evaluateJavascript("window.__comicTvBlobV1 && window.__comicTvBlobV1.release(${gson.toJson(key)});", null) }
    }
    fun park(web: WebView, key: String?) {
        if (validKey(key)) runCatching { web.evaluateJavascript("window.__comicTvBlobV1 && window.__comicTvBlobV1.park(${gson.toJson(key)});", null) }
    }
    fun release(offer: BrowserDownloadOffer?) { if (offer != null) release(offer.webView, offer.request.blobKey) }
}

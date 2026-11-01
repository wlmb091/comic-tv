package com.pelican.comictv.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout

internal data class BrowserDownloadOffer(val request: BrowserDownloadRequest, val mime: String?, val webView: WebView)

/** Activity-owned native pages. Only two live renderers; suspended tabs keep bounded navigation state. */
@Suppress("DEPRECATION", "SetJavaScriptEnabled")
internal class BrowserWebViews(private val context: Context, private val session: BrowserSession,
    private val changed: () -> Unit, private val toolbar: () -> Unit,
    private val download: (BrowserDownloadOffer) -> Unit, private val contextLink: (String?) -> Unit,
    private val fullscreen: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    private val fileChooser: (ValueCallback<Array<android.net.Uri>>, Array<String>) -> Unit,
    private val message: (String) -> Unit, private val saved: MutableMap<String, Bundle> = mutableMapOf()) {
    private val views = LinkedHashMap<String, RemoteBrowserWebView>(4, .75f, true)
    private val agents = mutableMapOf<String, String>()
    val progress = mutableMapOf<String, Int>()
    private var foreground = true
    private var inputEnabled = false
    private var fullCallback: WebChromeClient.CustomViewCallback? = null
    fun current(): RemoteBrowserWebView? = views[session.state.value.selectedId]
    fun obtain(tab: BrowserTab, load: Boolean = true): RemoteBrowserWebView {
        views[tab.id]?.let { applyOptions(tab.id, it); return it }
        val web = RemoteBrowserWebView(context)
        views[tab.id] = web; agents[tab.id] = web.settings.userAgentString
        web.setBackgroundColor(android.graphics.Color.rgb(15, 15, 15))
        web.onToolbar = toolbar; web.onPageInteraction = {}; web.onContextLink = contextLink
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; useWideViewPort = true; loadWithOverviewMode = true
            allowFileAccess = false; allowContentAccess = false; allowFileAccessFromFileURLs = false; allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW; setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = true
            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        applyInput(web)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url == "about:blank") return false
                if (url.startsWith("blob:") && browserBlobOrigin(url) != null) return !foreground
                return try {
                    BrowserPolicy.url(url)
                    if (request.isForMainFrame && session.state.value.adblock && BrowserAdblock.blocks(request.url.host)) return true
                    if (request.isForMainFrame) mixedContent(view, url)
                    !foreground
                }
                catch (_: Exception) { message("Este link precisa de outro aplicativo ou não é uma página HTTP/HTTPS."); true }
            }
            override fun onPageStarted(view: WebView, url: String, icon: Bitmap?) {
                progress[tab.id] = 0; mixedContent(view, url)
                if (url == "about:blank") session.home(tab.id) else session.page(tab.id, url, view.title.orEmpty())
                changed()
            }
            /** Runs off the main thread, so it reads only the session state and the request itself. */
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame || !session.state.value.adblock || !BrowserAdblock.blocks(request.url.host)) return null
                return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
            }
            override fun onPageCommitVisible(view: WebView, url: String) { darkMode(view, url) }
            override fun onPageFinished(view: WebView, url: String) {
                progress[tab.id] = 100; darkMode(view, url)
                if (url != "about:blank") session.page(tab.id, url, view.title.orEmpty(), true)
                changed()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) message("Falha ao abrir a página (${error.errorCode}): ${error.description}")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) message("O site respondeu HTTP ${response.statusCode}.")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); message("O certificado deste site é inválido. Confira o endereço, a data e a hora da TV.")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) { progress[tab.id] = value; changed() }
            override fun onReceivedTitle(view: WebView, title: String?) { view.url?.let { session.page(tab.id, it, title.orEmpty()) }; changed() }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!foreground || !isUserGesture && session.state.value.blockPopups) return false
                val id = session.newTab() ?: run { message("Limite de oito abas. Feche uma aba para abrir outra."); return false }
                val target = session.state.value.tabs.first { it.id == id }
                (resultMsg.obj as? WebView.WebViewTransport)?.webView = obtain(target, false)
                resultMsg.sendToTarget(); changed(); return true
            }
            override fun onCloseWindow(window: WebView) { views.entries.firstOrNull { it.value === window }?.key?.let { session.close(it) }; changed() }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullCallback != null) { callback.onCustomViewHidden(); return }
                fullCallback = callback; fullscreen(view, callback)
            }
            override fun onHideCustomView() { hideFullscreen() }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<android.net.Uri>>, params: FileChooserParams): Boolean {
                fileChooser(callback, params.acceptTypes.filter { it.isNotBlank() }.toTypedArray().ifEmpty { arrayOf("*/*") }); return true
            }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        web.setDownloadListener { url, agent, disposition, mime, _ ->
            if (foreground) try {
                if (url.startsWith("blob:")) require(browserBlobOrigin(url) == browserPageOrigin(web.url)) else BrowserPolicy.url(url)
                val name = BrowserDownloadNames.safe(URLUtil.guessFileName(url, disposition, mime))
                val request = BrowserDownloadRequest(url, name, agent ?: web.settings.userAgentString, web.url)
                if (url.startsWith("blob:")) BrowserBlobPage.prepare(web, request) { prepared ->
                    if (prepared == null) message("Não foi possível preparar o arquivo temporário. Reabra o download no site.")
                    else if (!foreground || web !in views.values) BrowserBlobPage.release(web, prepared.blobKey)
                    else download(BrowserDownloadOffer(prepared, mime, web))
                } else download(BrowserDownloadOffer(request, mime, web))
            } catch (_: Exception) { message("Não foi possível iniciar este download. Abra o link direto do arquivo na página.") }
        }
        applyOptions(tab.id, web)
        val restored = saved.remove(tab.id)?.let { runCatching { web.restoreState(it) != null }.getOrDefault(false) } ?: false
        if (!restored && load && tab.url.isNotBlank()) web.loadUrl(tab.url)
        trim(tab.id); return web
    }
    /** Strict everywhere; the compatibility mode lets a chosen site's HTTP images and media through while scripts and requests stay blocked. */
    private fun mixedContent(view: WebView, url: String) {
        val mode = if (BrowserPolicy.mixedImagesAllowed(url, session.state.value.httpImageSites)) WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE else WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (view.settings.mixedContentMode != mode) view.settings.mixedContentMode = mode
    }
    private fun darkMode(view: WebView, url: String) {
        if (url != "about:blank") view.evaluateJavascript(BrowserDarkMode.script(BrowserPolicy.siteMatches(url, session.state.value.darkSites)), null)
    }
    /** Applies a just-changed per-site choice to the open page. */
    fun refreshDarkMode() { current()?.let { view -> view.url?.let { darkMode(view, it) } } }
    fun reloadWithSiteOptions() { current()?.let { view -> view.url?.let { mixedContent(view, it) }; view.reload() } }
    private fun applyOptions(id: String, web: RemoteBrowserWebView) {
        val options = session.state.value
        web.cursorEnabled = options.cursor; web.cursorSpeed = options.cursorSpeed
        web.cursorScale = options.cursorSize / 100f; web.cursorStyle = options.cursorStyle
        web.settings.textZoom = options.textZoom
        web.settings.javaScriptCanOpenWindowsAutomatically = !options.blockPopups
        val original = agents[id] ?: web.settings.userAgentString
        val desired = if (options.desktop && original.contains(')')) original.substringBefore('(') + "(X11; Linux x86_64)" + original.substringAfter(')').replace(" Mobile", "") else original
        if (web.settings.userAgentString != desired) web.settings.userAgentString = desired
    }
    fun attach(frame: FrameLayout, tab: BrowserTab) {
        val view = obtain(tab)
        val ids = session.state.value.tabs.map { it.id }.toSet()
        views.keys.filter { it !in ids }.toList().forEach(::destroyTab)
        saved.keys.retainAll(ids)
        if (frame.childCount != 1 || frame.getChildAt(0) !== view) {
            frame.removeAllViews(); (view.parent as? ViewGroup)?.removeView(view)
            frame.addView(view, FrameLayout.LayoutParams(-1, -1))
            views.values.filter { it !== view }.forEach { it.releasePointer(); it.onPause() }
            if (foreground) view.onResume()
        }
    }
    private fun trim(active: String) {
        while (views.size > 2) {
            val id = views.keys.firstOrNull { it != active && it != session.state.value.selectedId } ?: break
            val view = views.getValue(id); val bundle = Bundle()
            runCatching {
                view.saveState(bundle)
                val parcel = Parcel.obtain()
                try { parcel.writeBundle(bundle); if (parcel.dataSize() <= 512 * 1024) saved[id] = bundle } finally { parcel.recycle() }
            }
            destroyTab(id)
        }
    }
    private fun destroyTab(id: String) { views.remove(id)?.apply { releasePointer(); stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() }; agents.remove(id); progress.remove(id) }
    private fun applyInput(web: RemoteBrowserWebView) {
        if (!inputEnabled) { web.releasePointer(); if (web.hasFocus()) web.clearFocus() }
        web.descendantFocusability = if (inputEnabled) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        web.isFocusableInTouchMode = inputEnabled
        web.isFocusable = inputEnabled
    }
    /** Overlays keep the site alive for transfers, but native WebView must not take their D-pad focus. */
    fun inputEnabled(value: Boolean) {
        if (inputEnabled == value) return
        inputEnabled = value
        views.values.forEach(::applyInput)
    }
    fun hideFullscreen() { val callback = fullCallback; fullCallback = null; fullscreen(null, null); callback?.onCustomViewHidden() }
    fun foreground(value: Boolean) {
        foreground = value
        views.forEach { (id, view) -> view.settings.blockNetworkLoads = !value; if (value && id == session.state.value.selectedId) view.onResume() else { view.releasePointer(); view.onPause() } }
    }
    fun close() {
        hideFullscreen()
        views.forEach { (id, view) ->
            runCatching {
                val bundle = Bundle(); view.saveState(bundle); val parcel = Parcel.obtain()
                try { parcel.writeBundle(bundle); if (parcel.dataSize() <= 512 * 1024) saved[id] = bundle } finally { parcel.recycle() }
            }
        }
        views.keys.toList().forEach(::destroyTab)
    }
    fun updateOptions() { views.forEach { (id, view) -> applyOptions(id, view) } }
}

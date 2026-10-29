package com.pelican.comictv.browser

import android.os.Build
import android.view.KeyEvent
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryRepository
import com.pelican.comictv.ui.*
import kotlinx.coroutines.*
import java.io.File

private data class OfferedDownload(val url: String, val name: String, val agent: String)

@Suppress("DEPRECATION", "SetJavaScriptEnabled")
@Composable fun BrowserScreen(library: LibraryRepository, onBack: () -> Unit, onRead: (String) -> Unit, onDetails: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val toolbar = remember { FocusRequester() }
    val downloader = remember { BrowserDownloader() }
    var view by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf("") }
    var pageMode by remember { mutableStateOf(false) }
    var cursorEnabled by remember { mutableStateOf(true) }
    fun enterPage() { pageMode = true }
    fun showToolbar() { pageMode = false }
    LaunchedEffect(pageMode) {
        withFrameNanos { }
        if (pageMode) view?.requestFocus() else if (view != null) toolbar.requestFocus()
    }
    var ready by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Abra uma página, escolha um arquivo e confirme o download. Setas movem o cursor; OK clica; MENU retorna à barra; Voltar percorre o histórico.") }
    var offered by remember { mutableStateOf<OfferedDownload?>(null) }
    var complete by remember { mutableStateOf<Pair<File, String>?>(null) }
    var importedId by remember { mutableStateOf<String?>(null) }
    var task by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(true) }
    fun cancel() { downloader.cancel(); task?.cancel() }
    fun navigate() {
        try {
            val url = BrowserPolicy.destination(address)
            if (foreground && view != null) { message = "Carregando…"; view?.loadUrl(url.toString()); enterPage() }
            else message = "Aguarde a inicialização do navegador."
        }
        catch (e: Exception) { message = e.message ?: "Endereço inválido." }
    }
    fun back() { if (view?.canGoBack() == true) view?.goBack() else onBack() }
    BackHandler { back() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false; cancel(); offered = null
                view?.apply { stopLoading(); settings.blockNetworkLoads = true; onPause() }
            } else if (event == Lifecycle.Event.ON_START) {
                foreground = true; view?.apply { settings.blockNetworkLoads = false; onResume() }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer); cancel()
            view?.apply { stopLoading(); settings.blockNetworkLoads = true; destroy() }
            val cleanup = complete?.first
            val running = task
            if (running != null && !running.isCompleted) running.invokeOnCompletion { cleanup?.delete() } else cleanup?.delete()
        }
    }
    // Create the native surface after the navigation transition, not in its fading layer.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withFrameNanos { }; ready = true
            if (pageMode) view?.requestFocus() else toolbar.requestFocus()
            awaitCancellation()
        }
    }
    BrowserViewport(controlsVisible = !pageMode, controls = {
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Navegador · downloads para sua biblioteca")
        Text("Endereço ou pesquisa no Google · OK abre o teclado", color = TvColors.Muted)
        TvTextInput(address, { address = it }, Modifier.fillMaxWidth().focusRequester(toolbar).background(TvColors.Panel).padding(10.dp), onSubmit = { navigate() }, maxLength = 4096)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).onPreviewKeyEvent { event ->
            if (offered == null && complete == null && importedId == null &&
                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                enterPage(); true
            } else false
        }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Buscar / acessar", { navigate() })
            TvButton("Voltar", { back() })
            TvButton("Recarregar", { if (foreground) view?.reload() })
            TvButton("Entrar na página", { enterPage() })
            TvButton(if (cursorEnabled) "Cursor: ligado" else "Cursor: desligado", { cursorEnabled = !cursorEnabled })
            TvButton("Sair do navegador", onBack)
            if (busy) TvButton("Cancelar", { cancel(); message = "Operação cancelada. Importações já concluídas são preservadas." })
        }
        Text(message, color = TvColors.Muted)
        offered?.let { offer ->
            Text("Baixar ${offer.name}? Até 1 GiB; somente arquivos que você pode obter legalmente.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Confirmar download", {
                    if (!busy && foreground) {
                        offered = null; importedId = null; complete?.first?.delete(); complete = null; busy = true
                        task = scope.launch {
                            var temporary: File? = null
                            try {
                                val file = withContext(Dispatchers.IO) { File.createTempFile("browser-", ".download", File(context.cacheDir, "browser-downloads").apply { mkdirs() }).also { temporary = it } }
                                withContext(Dispatchers.IO) {
                                    val job = currentCoroutineContext()
                                    var lastProgress = 0L
                                    downloader.download(offer.url, file, { CookieManager.getInstance().getCookie(it) }, offer.agent,
                                        active = { job.ensureActive(); check(foreground) },
                                        progress = { bytes, total -> if (bytes - lastProgress >= 262144 || bytes == total) { lastProgress = bytes; scope.launch { message = "${bytes / 1048576} MiB" + if (total > 0) " / ${total / 1048576} MiB · ${bytes * 100 / total}%" else " · tamanho desconhecido" } } })
                                }
                                complete = file to offer.name; message = "Download concluído. Escolha Importar para validar e adicionar à biblioteca."
                            } catch (e: CancellationException) { temporary?.delete(); throw e }
                            catch (_: Exception) { temporary?.delete(); message = "Não foi possível baixar: conexão, espaço ou limite inválido. Tente novamente." }
                            finally { busy = false }
                        }
                    }
                })
                TvButton("Não baixar", { offered = null })
            }
        }
        complete?.let { (file, name) ->
            if (importedId == null) TvButton(if (busy) "Importando…" else "Importar $name", {
                if (!busy) { busy = true; task = scope.launch {
                    try {
                        val result = library.importReceived(file, name) { text -> scope.launch { message = text } }
                        message = result.summary(); importedId = result.singleId
                        if (result.failures.isEmpty()) { file.delete(); complete = null }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = "Falha na importação. O download foi mantido para tentar novamente." }
                    finally { busy = false }
                } }
            })
        }
        importedId?.let { id -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Ler agora", { onRead(id) }); TvButton("Ver detalhes", { onDetails(id) })
        } }
      }
    }, page = {
        Box(Modifier.fillMaxSize()) {
            var unavailable by remember { mutableStateOf(false) }
            if (unavailable) Text("Esta TV não tem um provedor Android System WebView disponível. Use a importação local ou pela rede.")
            else if (!ready) Text("Iniciando navegador…")
            else AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                // Provider creation can fail on TV firmware; return a harmless native view.
                try {
                    RemoteBrowserWebView(ctx).apply {
                        onToolbar = { showToolbar() }
                        onPageInteraction = { enterPage() }
                        view = this
                        setBackgroundColor(android.graphics.Color.rgb(15, 15, 15))
                        settings.apply {
                            javaScriptEnabled = true; domStorageEnabled = true
                            allowFileAccess = false; allowContentAccess = false
                            allowFileAccessFromFileURLs = false; allowUniversalAccessFromFileURLs = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(false)
                            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean =
                                try { BrowserPolicy.url(request.url.toString()); !foreground }
                                catch (_: Exception) { message = "Somente páginas HTTP/HTTPS são permitidas. Downloads blob/data/file/intent não são suportados."; true }
                            override fun onPageStarted(v: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                if (url.startsWith("http")) message = "Carregando…"
                            }
                            override fun onPageFinished(v: WebView, url: String) {
                                if (url.startsWith("http")) { address = url; if (message == "Carregando…") message = "Página carregada · MENU volta à barra." }
                            }
                            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) message = "Não foi possível abrir a página (${error.errorCode}): ${error.description}. Confira o endereço ou tente uma pesquisa."
                            }
                            override fun onReceivedHttpError(v: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) message = "O site respondeu HTTP ${response.statusCode}."
                            }
                            override fun onReceivedSslError(v: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                                handler.cancel(); message = "A página tem um certificado TLS inválido; conexão bloqueada."
                            }
                        }
                        setDownloadListener { url, agent, disposition, mime, _ ->
                            if (!busy && foreground) try {
                                BrowserPolicy.url(url)
                                val name = BrowserPolicy.filename(URLUtil.guessFileName(url, disposition, mime))
                                offered = OfferedDownload(url, name, agent ?: settings.userAgentString)
                                showToolbar()
                            } catch (e: Exception) { message = e.message ?: "Download incompatível." }
                        }
                        isFocusable = true; isFocusableInTouchMode = true
                        // Render a local welcome document immediately; no network required to open the UI.
                        loadData("<html><body style='background:#0f0f0f;color:#eeeeee;font-family:sans-serif;padding:24px'><h2>Navegador pronto</h2><p>Digite uma pesquisa ou o endereço de um site na barra acima.</p></body></html>", "text/html", "UTF-8")
                    }
                } catch (_: Exception) { unavailable = true; android.view.View(ctx) }
            }, update = { native ->
                (native as? RemoteBrowserWebView)?.cursorEnabled = cursorEnabled
            })
        }
    })
}

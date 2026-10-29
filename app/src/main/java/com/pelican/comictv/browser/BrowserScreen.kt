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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    var address by remember { mutableStateOf("https://") }
    var message by remember { mutableStateOf("Abra uma página, escolha um arquivo e confirme o download. MENU retorna à barra; Voltar percorre o histórico.") }
    var offered by remember { mutableStateOf<OfferedDownload?>(null) }
    var complete by remember { mutableStateOf<Pair<File, String>?>(null) }
    var importedId by remember { mutableStateOf<String?>(null) }
    var task by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(true) }
    fun cancel() { downloader.cancel(); task?.cancel() }
    fun navigate() {
        try { val url = BrowserPolicy.url(address); view?.loadUrl(url.toString()) }
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
    LaunchedEffect(Unit) { toolbar.requestFocus() }
    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Navegador · downloads para sua biblioteca")
        TvTextInput(address, { address = it }, Modifier.fillMaxWidth().focusRequester(toolbar).background(TvColors.Panel).padding(10.dp), onSubmit = { navigate() }, maxLength = 4096)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Abrir endereço", { navigate() })
            TvButton("Voltar", { back() })
            TvButton("Recarregar", { if (foreground) view?.reload() })
            TvButton("Entrar na página", { view?.requestFocus() })
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            var unavailable by remember { mutableStateOf(false) }
            if (unavailable) Text("Esta TV não tem um provedor Android System WebView disponível. Use a importação local ou pela rede.")
            else AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                // Provider creation can fail on TV firmware; return a harmless native view.
                try {
                    WebView(ctx).apply {
                        view = this
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
                            override fun onPageFinished(v: WebView, url: String) { if (url.startsWith("http")) address = url }
                            override fun onReceivedSslError(v: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                                handler.cancel(); message = "A página tem um certificado TLS inválido; conexão bloqueada."
                            }
                        }
                        setDownloadListener { url, agent, disposition, mime, _ ->
                            if (!busy && foreground) try {
                                BrowserPolicy.url(url)
                                val name = BrowserPolicy.filename(URLUtil.guessFileName(url, disposition, mime))
                                offered = OfferedDownload(url, name, agent ?: settings.userAgentString)
                                toolbar.requestFocus()
                            } catch (e: Exception) { message = e.message ?: "Download incompatível." }
                        }
                        setOnKeyListener { _, code, event ->
                            if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_ESCAPE) {
                                if (event.action == KeyEvent.ACTION_UP) toolbar.requestFocus()
                                true
                            } else false
                        }
                        isFocusable = true; isFocusableInTouchMode = true
                    }
                } catch (_: Exception) { unavailable = true; android.view.View(ctx) }
            })
        }
    }
}

package com.pelican.comictv.browser

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.library.LibraryRepository
import com.pelican.comictv.ui.*
import kotlinx.coroutines.*

@Composable fun BrowserScreen(library: LibraryRepository, onBack: () -> Unit, onRead: (String) -> Unit, onDetails: (String) -> Unit, initialUrl: String = "") {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val model: BrowserSessionViewModel = viewModel(key = "tv-browser-session", factory = viewModelFactory { initializer { BrowserSessionViewModel(context.applicationContext) } })
    val downloads: BrowserDownloadsViewModel = viewModel(key = "tv-browser-downloads", factory = viewModelFactory { initializer { BrowserDownloadsViewModel(context.applicationContext, library) } })
    val session = model.session
    val state by session.state.collectAsStateWithLifecycle()
    val rows by downloads.items.collectAsStateWithLifecycle()
    val tab = state.tabs.first { it.id == state.selectedId }
    val addressFocus = remember { FocusRequester() }
    var chrome by rememberSaveable { mutableStateOf(tab.url.isBlank()) }
    var address by rememberSaveable { mutableStateOf(tab.url) }
    var dirtyAddress by remember { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var offer by remember { mutableStateOf<BrowserDownloadOffer?>(null) }
    var link by remember { mutableStateOf<String?>(null) }
    var removeId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var nativeError by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var pageFocus by remember { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf<View?>(null) }
    var find by rememberSaveable { mutableStateOf("") }
    var exporting by remember { mutableStateOf<String?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    var pendingFiles by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        pendingFiles?.onReceiveValue(selected.toTypedArray().takeIf { it.isNotEmpty() }); pendingFiles = null
    }
    val host = remember(context, session) {
        BrowserWebViews(context, session, changed = { revision++ }, toolbar = { chrome = true },
            download = { offer = it }, contextLink = { link = it; sheet = if (it == null) "more" else "link" },
            fullscreen = { view, _ -> fullscreen = view },
            fileChooser = { callback, types ->
                pendingFiles?.onReceiveValue(null); pendingFiles = callback
                try { chooser.launch(types) } catch (_: Exception) { pendingFiles?.onReceiveValue(null); pendingFiles = null; status = "Não há seletor de arquivos disponível nesta TV." }
            }, message = { status = it.take(400) }, saved = model.webStates)
    }
    fun navigate(text: String = address) {
        try {
            val url = BrowserPolicy.destination(text).toString()
            val current = session.state.value.tabs.first { it.id == session.state.value.selectedId }
            session.page(current.id, url, "Carregando…")
            host.obtain(current.copy(url = url), false).loadUrl(url)
            address = url; dirtyAddress = false; status = ""; chrome = false; sheet = null; pageFocus++
        } catch (error: Exception) { status = error.message?.take(300) ?: "Não foi possível abrir a página."; chrome = true }
    }
    var initialOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialUrl, ready) {
        if (ready && !initialOpened && initialUrl.isNotBlank()) { initialOpened = true; navigate(initialUrl) }
    }
    fun newTab(url: String = "") {
        val id = session.newTab(url)
        if (id == null) { status = "Limite de oito abas. Feche uma aba para abrir outra."; chrome = true; return }
        dirtyAddress = false; address = url; sheet = null
        if (url.isBlank()) chrome = true
        else { host.obtain(session.state.value.tabs.first { it.id == id }, false).loadUrl(url); chrome = false; pageFocus++ }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { address = it; navigate(it) }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = exporting; exporting = null
        if (uri != null && id != null) scope.launch {
            exportBusy = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output -> downloads.manager.file(id).inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                    } } ?: error("Não foi possível abrir o destino.")
                }
                status = "Cópia salva. O download original continua disponível."
            } catch (cancelled: CancellationException) { status = "Cópia interrompida. O download original foi mantido."; throw cancelled }
            catch (_: Exception) { status = "Não foi possível salvar a cópia. O download original foi mantido." }
            finally { exportBusy = false }
        }
    }
    LaunchedEffect(tab.id) { address = tab.url; dirtyAddress = false; status = ""; if (tab.url.isBlank()) chrome = true }
    LaunchedEffect(tab.url) { if (!dirtyAddress) address = tab.url }
    DisposableEffect(lifecycle, host, downloads) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { host.foreground(true); downloads.setForeground(true) }
            if (event == Lifecycle.Event.ON_STOP) { host.foreground(false); downloads.setForeground(false) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); pendingFiles?.onReceiveValue(null); downloads.setForeground(false); host.close() }
    }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        withFrameNanos { }; ready = true; withFrameNanos { }
        if (sheet == null && offer == null) { if (chrome) addressFocus.requestFocus() else host.current()?.requestFocus() }
        awaitCancellation()
    } }
    LaunchedEffect(chrome, tab.id, sheet, offer, ready, pageFocus, fullscreen) {
        if (ready && sheet == null && offer == null && fullscreen == null) {
            withFrameNanos { }
            if (chrome) addressFocus.requestFocus() else host.current()?.requestFocus()
        }
    }
    fun back() {
        val view = host.current()
        val ime = view?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true
        when {
            ime -> (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view?.windowToken, 0)
            fullscreen != null -> host.hideFullscreen()
            offer != null -> offer = null
            removeId != null -> removeId = null
            sheet != null -> sheet = null
            chrome -> if (tab.url.isBlank()) onBack() else { chrome = false; pageFocus++ }
            !state.cursor -> { session.options(cursor = true); host.updateOptions() }
            else -> chrome = true
        }
    }
    BackHandler { back() }
    fun action(key: String) {
        val view = host.current()
        when {
            key.startsWith("tab:") -> { session.select(key.removePrefix("tab:")); chrome = false; pageFocus++ }
            key == "go" -> navigate()
            key == "voice" -> try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")) }
                catch (_: Exception) { status = "Pesquisa por voz indisponível. Use OK no endereço para digitar." }
            key == "back" -> { if (view?.canGoBack() == true) view.goBack(); chrome = false; pageFocus++ }
            key == "forward" -> { if (view?.canGoForward() == true) view.goForward(); chrome = false; pageFocus++ }
            key == "reload" -> { if ((host.progress[tab.id] ?: 100) in 1..99) view?.stopLoading() else view?.reload(); chrome = false; pageFocus++ }
            key == "new" -> newTab()
            key == "home" -> { session.home(tab.id); view?.loadUrl("about:blank"); chrome = true; address = ""; dirtyAddress = false }
            key == "close-tab" -> session.close(tab.id)
            key == "bookmark" -> { if (tab.url.isNotBlank()) session.bookmark(tab.url, tab.title) }
            key == "downloads" -> sheet = "downloads"
            key == "more" -> sheet = "more"
            key == "page" -> { chrome = tab.url.isBlank(); pageFocus++ }
            key == "exit" -> onBack()
        }
    }
    revision // Native progress/navigation flags are published without changing page identity or focus.
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
            host.current()?.releasePointer()
            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) { if (fullscreen != null) host.hideFullscreen() else if (sheet != null) sheet = null else chrome = !chrome }
            true
        } else false
    }) {
        BrowserViewport(controlsVisible = chrome && sheet == null && offer == null && fullscreen == null,
            controls = { BrowserChrome(state, address, { address = it; dirtyAddress = true }, addressFocus,
                host.current()?.canGoBack() == true, host.current()?.canGoForward() == true,
                rows.count { it.status in setOf(BrowserDownloadStatus.Running, BrowserDownloadStatus.Queued) },
                host.progress[tab.id] ?: 100, status, ::action) },
            page = {
                if (nativeError) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Android System WebView indisponível. Atualize o componente de navegação da TV.") }
                else if (ready) AndroidView(modifier = Modifier.fillMaxSize(), factory = { FrameLayout(it) }, update = { frame ->
                    try { host.attach(frame, tab) } catch (_: Exception) { nativeError = true }
                })
                if (tab.url.isBlank()) BrowserHome(::navigate) { sheet = it }

            })
        if (sheet == "downloads") BrowserDownloadsPanel(rows, exportBusy, { sheet = null }, message = status, action = { command, id ->
            when (command) {
                "pause" -> downloads.manager.pause(id)
                "resume" -> downloads.manager.resume(id)
                "restart" -> downloads.manager.restart(id)
                "cancel" -> downloads.manager.cancel(id)
                "remove" -> removeId = id
                "import" -> downloads.import(id)
                "read" -> rows.firstOrNull { it.id == id }?.importedId?.let(onRead)
                "details" -> rows.firstOrNull { it.id == id }?.importedId?.let(onDetails)
                "save" -> rows.firstOrNull { it.id == id }?.let { row -> exporting = id; saveFile.launch(row.filename) }
            }
        })
        fullscreen?.let { custom -> AndroidView(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
            factory = { FrameLayout(it) }, update = { frame ->
                if (custom.parent !== frame) { (custom.parent as? android.view.ViewGroup)?.removeView(custom); frame.removeAllViews(); frame.addView(custom, FrameLayout.LayoutParams(-1, -1)); custom.requestFocus() }
            }) }
    }
    offer?.let { selected -> LibraryDialog("Baixar arquivo", { offer = null }) {
        Text(selected.request.suggestedName, fontSize = 20.sp)
        Text("O nome final será confirmado pela resposta do site. Acompanhe a transferência em Downloads.", fontSize = 16.sp)
        TvButton("Baixar", {
            try {
                if (selected.request.url.startsWith("blob:")) downloads.registerBlob(selected)
                val id = downloads.manager.enqueue(selected.request)
                if (id == null) status = "Fila cheia. Conclua ou remova downloads antes de adicionar outro." else sheet = "downloads"
            } catch (error: Exception) { status = error.message ?: "Não foi possível iniciar o download."; chrome = true }
            offer = null
        })
        TvButton("Cancelar", { offer = null })
    } }
    removeId?.let { id -> LibraryDialog("Excluir download?", { removeId = null }) {
        Text("Remove somente este arquivo do navegador. Itens já importados na biblioteca são preservados.")
        TvButton("Cancelar", { removeId = null }); TvButton("Excluir download", { downloads.manager.remove(id); removeId = null })
    } }
    if (sheet == "more") LibraryDialog("Opções do navegador", { sheet = null }) {
        TvButton("Favoritos", { sheet = "bookmarks" }); TvButton("Histórico", { sheet = "history" }); TvButton("Downloads", { sheet = "downloads" })
        TvButton("Localizar nesta página", { sheet = "find" })
        TvButton(if (state.desktop) "Versão para computador: ligada" else "Versão para computador: desligada", {
            session.options(desktop = !state.desktop); host.updateOptions(); host.current()?.reload()
        })
        TvButton(if (state.cursor) "Cursor: ligado" else "Navegação direta: ligada", { session.options(cursor = !state.cursor); host.updateOptions() })
        TvButton("Velocidade do cursor: ${listOf("Lenta", "Normal", "Rápida")[state.cursorSpeed]}", { session.options(speed = (state.cursorSpeed + 1) % 3); host.updateOptions() })
        TvButton("Texto da página: ${state.textZoom}%", { session.options(textZoom = if (state.textZoom >= 150) 75 else state.textZoom + 25); host.updateOptions() })
        TvButton(if (host.current()?.dragMode == true) "Arrastar: ligado" else "Arrastar: desligado", { host.current()?.let { it.dragMode = !it.dragMode }; revision++ })
        TvButton(if (state.blockPopups) "Janelas automáticas: bloqueadas" else "Janelas automáticas: permitidas", { session.options(blockPopups = !state.blockPopups); host.updateOptions() })
        TvButton("Voltar à página", { sheet = null; chrome = false; pageFocus++ }); TvButton("Sair do navegador", onBack)
    }
    if (sheet == "bookmarks" || sheet == "history") LibraryDialog(if (sheet == "history") "Histórico" else "Favoritos", { sheet = null }) {
        val pages = if (sheet == "history") state.history else state.bookmarks
        if (pages.isEmpty()) Text("Nenhuma página salva ainda.")
        pages.forEach { page -> TvButton(page.title, { navigate(page.url) }, Modifier.fillMaxWidth()) }
        if (sheet == "history" && pages.isNotEmpty()) TvButton("Limpar histórico", session::historyClear)
        TvButton("Voltar", { sheet = null })
    }
    if (sheet == "find") LibraryDialog("Localizar na página", { sheet = null }) {
        TvTextInput(find, { find = it; host.current()?.findAllAsync(it) }, Modifier.fillMaxWidth(), maxLength = 200, onSubmit = { host.current()?.findAllAsync(find) })
        TvButton("Anterior", { host.current()?.findNext(false) }); TvButton("Próximo", { host.current()?.findNext(true) })
        TvButton("Concluir", { sheet = null; chrome = false; pageFocus++ })
    }
    if (sheet == "link") LibraryDialog("Opções do link", { sheet = null }) {
        Text(link.orEmpty(), maxLines = 3, fontSize = 15.sp)
        TvButton("Abrir", { link?.let { navigate(it) } })
        TvButton("Abrir em nova aba", { link?.let { newTab(it) } })
        TvButton("Baixar link", {
            link?.let { url -> host.current()?.let { page ->
                offer = BrowserDownloadOffer(BrowserDownloadRequest(url, android.webkit.URLUtil.guessFileName(url, null, null), page.settings.userAgentString, page.url), null, page)
                sheet = null
            } }
        })
        TvButton("Copiar endereço", { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Endereço", link.orEmpty())); sheet = null })
        TvButton("Cancelar", { sheet = null })
    }
}

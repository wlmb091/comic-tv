package com.pelican.comictv.explorer

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.archive.ExplorerFileKind
import com.pelican.comictv.archive.ExplorerFiles
import com.pelican.comictv.ui.*
import java.io.File
import kotlinx.coroutines.*

@Composable internal fun ExplorerScreen(model: ExplorerViewModel, busy: Boolean, importMessage: String?,
    titles: Map<String, String>, onImport: (Uri) -> Unit, onRead: (String) -> Unit,
    onCancelImport: () -> Unit, onBack: () -> Unit, onTools: (Uri,String)->Unit = {_,_->},
    onImportProtected: (List<Uri>,CharArray)->Unit = {_,secret->secret.fill('\u0000')}) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationKey = state.folder?.key ?: "explorer-roots"
    val previousPosition = remember(locationKey) { model.locations.restore(locationKey) }
    var selected by remember { mutableStateOf<ExplorerEntry?>(null) }
    var filter by remember(locationKey) { mutableStateOf(previousPosition.filter) }
    var query by remember(locationKey) { mutableStateOf(previousPosition.query) }
    var editingQuery by remember(locationKey) { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var preparation by remember { mutableStateOf<Job?>(null) }
    var pendingApk by remember { mutableStateOf<File?>(null) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingApk = null
        model.message(result.data?.getStringExtra("message") ?: "Instalador encerrado. O Android informa o resultado da instalação.")
        model.restoreFocus()
    }
    fun launchInstaller(file: File) {
        try { installer.launch(ExplorerApk.install(context, file)) }
        catch (_: ActivityNotFoundException) { model.message("Esta TV não oferece um instalador de APKs.") }
        catch (error: Exception) { model.message(error.message ?: "Não foi possível abrir o instalador.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pendingApk
        if (file != null && context.packageManager.canRequestPackageInstalls()) launchInstaller(file)
        else { pendingApk = null; model.message("Instalação não autorizada. Seus arquivos foram mantidos."); model.restoreFocus() }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(model::authorize)
        model.restoreFocus()
    }
    val folder = state.folder
    val rows = if (folder == null) state.roots.map { ExplorerEntry(it.key, it.label, true) } else state.rows
    val visible = rows.filter { entry ->
        val name = titles[entry.comicId] ?: entry.name
        (entry.directory || when (filter) {
            "Quadrinhos" -> entry.name.substringAfterLast('.').lowercase() in setOf("cbz", "zip", "cbr", "rar", "7z", "cb7")
            "Livros" -> entry.name.substringAfterLast('.').lowercase() in setOf("pdf", "epub", "docx", "mobi", "azw")
            "APK" -> ExplorerFiles.kind(entry.name) == ExplorerFileKind.Install
            else -> true
        }) && name.contains(query, ignoreCase = true)
    }
    val list = remember(locationKey) { LazyListState(previousPosition.index, previousPosition.offset) }
    var restoredPosition by remember(locationKey) { mutableStateOf(false) }
    fun savePosition() = model.locations.save(locationKey, ExplorerPosition(query, filter, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset))
    val entryFocus = remember { FocusRequester() }
    val upFocus = remember { FocusRequester() }
    val focusKey = state.focus?.takeIf { key -> visible.any { it.key == key } } ?: visible.firstOrNull()?.key
    LaunchedEffect(state.focusSerial, folder?.key, state.loading) {
        if (!explorerShouldRestoreFocus(editingQuery, state.loading) || selected != null || preparing) return@LaunchedEffect
        if (!restoredPosition) {
            // Loading temporarily leaves only the header; restore after actual rows arrive.
            if (previousPosition.index > 0) list.scrollToItem(previousPosition.index.coerceAtMost(visible.size), previousPosition.offset)
            restoredPosition = true
        }
        val index = visible.indexOfFirst { it.key == focusKey }
        if (index >= 0) {
            val lazyIndex = index + 1
            if (list.layoutInfo.visibleItemsInfo.none { it.index == lazyIndex }) list.scrollToItem(lazyIndex)
            withFrameNanos { }; withFrameNanos { }
            runCatching { entryFocus.requestFocus() }
        } else { withFrameNanos { }; runCatching { upFocus.requestFocus() } }
    }
    BackHandler {
        if (selected != null) { selected = null; model.restoreFocus() }
        else if (preparing) preparation?.cancel()
        else if (busy) onCancelImport()
        else { savePosition(); if (!model.back()) onBack() }
    }
    LazyColumn(Modifier.fillMaxSize().background(TvColors.Background), state = list,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "commands") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenTitle("Explorar arquivos", folder?.label ?: "Escolha uma origem ou autorize uma pasta da TV / USB.")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton(if (folder == null) "Voltar" else "Pasta acima", { savePosition(); if (!model.back()) onBack() }, Modifier.focusRequester(upFocus))
                    TvButton("Adicionar pasta · TV / USB", {
                        try { folderPicker.launch(null) }
                        catch (_: ActivityNotFoundException) { model.message("Esta TV não tem seletor de pastas. Use Escolher arquivo ou o envio pela rede em Importar.") }
                    })
                    ProtectedArchiveAction(busy || preparing, model::message, onImportProtected)
                    if (busy) TvButton("Interromper importação", onCancelImport)
                    if (preparing) TvButton("Cancelar preparação do APK", { preparation?.cancel() })
                }
                if (folder != null) {
                    Text("Filtrar nesta pasta", color = TvColors.Muted, fontSize = 16.sp)
                    BasicTextField(query, { query = it.take(120) }, Modifier.fillMaxWidth().onFocusChanged { editingQuery = it.isFocused }.background(TvColors.Panel).padding(16.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp), singleLine = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf("Todos", "Quadrinhos", "Livros", "APK").forEach { value ->
                            TvButton(value, { filter = value }, primary = filter == value)
                        }
                    }
                }
                if (folder == null) Text("Pastas privadas de outros aplicativos não são acessíveis. O Android define quais pastas da TV e do USB podem ser autorizadas.", fontSize = 16.sp, color = TvColors.Muted)
                if (preparing) Text("Preparando APK…", fontSize = 18.sp)
                (state.message ?: importMessage)?.let { Text(it, fontSize = 17.sp, color = TvColors.Amber) }
                if (state.loading) Text("Carregando pasta…", fontSize = 18.sp)
                else if (visible.isEmpty()) Text("Nenhum arquivo neste filtro. Você pode adicionar outra pasta acima.", fontSize = 18.sp)
            }
        }
        itemsIndexed(visible, key = { _, row -> row.key }) { _, row ->
            val modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) model.focus(row.key) }
                .then(if (row.key == focusKey) Modifier.focusRequester(entryFocus) else Modifier)
            val name = titles[row.comicId] ?: row.name
            val detail = if (row.directory) "Pasta" else when (ExplorerFiles.kind(row.name)) {
                ExplorerFileKind.Install -> "APK / XAPK / APKM · instalar com confirmação do Android"
                ExplorerFileKind.Import -> "Leitura · importar ou abrir"
                ExplorerFileKind.Other -> "Arquivo"
            }
            TvButton("${if (row.directory) "▸ " else ""}$name\n$detail${if (row.size >= 0 && !row.directory) " · ${row.size / 1024} KiB" else ""}", {
                if (!busy && !preparing) {
                    savePosition()
                    if (folder == null) model.open(state.roots.first { it.key == row.key })
                    else if (row.directory) model.open(row)
                    else selected = row
                }
            }, modifier)
        }
    }
    selected?.let { entry ->
        com.pelican.comictv.ui.ComicDialog(onDismissRequest = { selected = null; model.restoreFocus() }) {
            com.pelican.comictv.companion.CompanionDialogWindow { selected = null; model.restoreFocus() }
            val actionFocus = remember { FocusRequester() }
            LaunchedEffect(entry.key) { withFrameNanos { }; actionFocus.requestFocus() }
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).background(TvColors.Panel).verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(titles[entry.comicId] ?: entry.name, fontSize = 23.sp)
                val existing = entry.comicId?.takeIf { it in titles }
                if (existing != null) TvButton("Ler agora", { savePosition(); selected = null; onRead(existing) }, Modifier.focusRequester(actionFocus))
                else if (ExplorerFiles.kind(entry.name) == ExplorerFileKind.Install) {
                    Text("O Android pedirá sua confirmação. XAPK/APKM exigem base e splits com mesma assinatura, pacote e versão; somente ABI compatível. Pacotes com OBB não são suportados. A instalação não torna o aplicativo uma fonte confiável do Comic TV.", fontSize = 17.sp)
                    TvButton("Instalar pacote Android", {
                        selected = null
                        preparation = scope.launch {
                            preparing = true
                            try {
                                val file = ExplorerApk.prepare(context, model.storage.uri(entry), entry.name)
                                pendingApk = file
                                if (context.packageManager.canRequestPackageInstalls()) launchInstaller(file)
                                else permission.launch(ExplorerApk.permission(context))
                            } catch (cancelled: CancellationException) { model.message("Preparação cancelada."); throw cancelled }
                            catch (error: Exception) { model.message(error.message ?: "Não foi possível preparar o APK.") }
                            finally { preparing = false; model.restoreFocus() }
                        }
                    }, Modifier.focusRequester(actionFocus))
                } else if (ExplorerFiles.kind(entry.name) == ExplorerFileKind.Import) TvButton("Importar para biblioteca", {
                    savePosition()
                    selected = null
                    runCatching { onImport(model.storage.uri(entry)) }.onFailure { model.message(it.message) }
                    model.restoreFocus()
                }, Modifier.focusRequester(actionFocus))
                else Text("Este tipo de arquivo não é compatível com a leitura ou instalação.", fontSize = 18.sp)
                if(entry.name.substringAfterLast('.').lowercase() in setOf("zip","cbz","rar","cbr","7z","cb7"))
                    TvButton("Conteúdo · Extrair · Converter CBZ",{savePosition();selected=null;onTools(model.storage.uri(entry),titles[entry.comicId]?:entry.name)})
                TvButton("Voltar", { selected = null; model.restoreFocus() }, if (existing == null && ExplorerFiles.kind(entry.name) == ExplorerFileKind.Other) Modifier.focusRequester(actionFocus) else Modifier)
            }
        }
    }
}

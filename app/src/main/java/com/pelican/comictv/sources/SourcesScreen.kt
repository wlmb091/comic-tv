package com.pelican.comictv.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.ScreenTitle
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvColors
import com.pelican.comictv.ui.textPanelScroll
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.coroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pelican.comictv.ui.LocalContentNavigation

@Composable
fun SourcesScreen(viewModel: SourcesViewModel, onRead: (String) -> Unit, onInstall: (File) -> Unit, onDetailsBack: (() -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val visibilityOwner = remember {Any()}
    val downloadsVisible = state.details == null && (state.managerPage == SourceManagerPage.EXTENSIONS || state.selectedRepository != null)
    DisposableEffect(lifecycleOwner,viewModel,downloadsVisible) {
        fun update() { viewModel.extensionDownloadsVisible(visibilityOwner,downloadsVisible && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
        val observer=androidx.lifecycle.LifecycleEventObserver {_,_->update()}
        lifecycleOwner.lifecycle.addObserver(observer);update()
        onDispose {lifecycleOwner.lifecycle.removeObserver(observer);viewModel.extensionDownloadsVisible(visibilityOwner,false)}
    }
    val pages = rememberSaveableStateHolder()
    val nested = state.details != null || state.selectedRepository != null || (state.managerPage != null && state.activeSource != null)
    BackHandler(enabled = nested) { if (state.details != null && onDetailsBack != null) onDetailsBack() else viewModel.back() }
    if (state.activeSource != null && state.details == null && state.managerPage == null) {
        val source = state.activeSource!!
        pages.SaveableStateProvider("catalog:${source.packageName}:${source.id}") { SourceCatalog(state, viewModel) }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceSectionHeading(when {
                state.details != null -> "Detalhes"
                state.managerPage == SourceManagerPage.EXTENSIONS -> "Extensões"
                state.managerPage == SourceManagerPage.REPOSITORIES -> "Repositórios"
                else -> "Escolher fonte"
            }, Modifier.weight(1f))
            if (state.managerPage == SourceManagerPage.EXTENSIONS) {
                SourceButton("Atualizar", !state.busy) { viewModel.refreshInstalled(); viewModel.refreshExtensionCatalogs() }
                TvButton("Repositórios", { viewModel.openSourceManager(SourceManagerPage.REPOSITORIES) })
            }
            if (state.managerPage == SourceManagerPage.REPOSITORIES) TvButton("Extensões", { viewModel.openExtensions() })
            if (state.selectedRepository != null) TvButton("Atualizar repositório", { viewModel.refresh() })
        }
        if (state.busy) Text("Carregando…", color = TvColors.Amber, fontSize = 18.sp)
        state.message?.takeUnless { it == "${state.sources.size} fontes disponíveis." && state.loadFailures.isEmpty() }
            ?.let { Text(it, color = TvColors.Amber, fontSize = 17.sp) }
        Box(Modifier.weight(1f)) {
            val page = when {
                state.details != null -> "details:${state.activeSource?.id}:${state.details!!.title.key}"
                state.activeSource != null && state.managerPage == null -> "catalog:${state.activeSource!!.packageName}:${state.activeSource!!.id}"
                state.selectedRepository != null -> "repository:${state.selectedRepository}"
                else -> "home:${state.managerPage}"
            }
            pages.SaveableStateProvider(page) {
            when {
                state.details != null -> SourceDetails(state.details!!, state.busy) { viewModel.openChapter(it, onRead) }
                state.activeSource != null && state.managerPage == null -> SourceCatalog(state, viewModel)
                state.selectedRepository != null -> RepositoryExtensions(state, viewModel, onInstall)
                state.managerPage == SourceManagerPage.EXTENSIONS -> ExtensionsHome(state, viewModel, onInstall)
                state.managerPage == SourceManagerPage.REPOSITORIES -> SourcesHome(state, viewModel)
                else -> SourcePicker(state, viewModel)
            }
            }
        }
    }
}

@Composable
private fun SourcePicker(state: SourcesState, vm: SourcesViewModel) {
    val navigate = LocalContentNavigation.current
    var filter by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var choosingLanguage by remember { mutableStateOf(false) }
    val sources = filterSources(state.sources,filter,language)
    if (choosingLanguage) com.pelican.comictv.library.LibraryDialog("Filtrar fontes por idioma", {choosingLanguage=false}) {
        for (code in listOf("") + sourceLanguages(state.sources)) {
            com.pelican.comictv.ui.TvChoice(sourceLanguageLabel(code),language == code, {language=code;choosingLanguage=false})
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SourceSearchControls(filter, { filter = it }, "Buscar fonte · ${state.sources.size} disponíveis",
            com.pelican.comictv.ui.preferredContentEntryModifier(enabled = !state.busy)) {
            TvButton("Idioma: " + sourceLanguageLabel(language) + " · ${sources.size} fonte(s)", { choosingLanguage = true })
        }
        LazyVerticalGrid(GridCells.Adaptive(120.dp * TvColors.options.textScale), Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sources.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                SourceEmptyState(if (state.busy) "Carregando fontes" else "Nenhuma fonte encontrada",
                    if (state.busy) "Consultando as fontes instaladas…" else if (state.sources.isEmpty()) "Instale uma extensão na seção Extensões para começar." else "Tente buscar por outro nome ou idioma.")
            }
            items(sources, key = { it.packageName + ":" + it.id }) { source ->
                CompactExtensionCard(source.packageName, null, source.name, source.language,
                    selected = state.activeSource?.let { it.packageName == source.packageName && it.id == source.id } == true) {
                    navigate { vm.activateSource(source) }
                }
            }
        }
    }
}

@Composable
private fun ExtensionsHome(state: SourcesState, vm: SourcesViewModel, install: (File) -> Unit) {
    val transfers by vm.extensionDownloads.collectAsStateWithLifecycle()
    val navigate = LocalContentNavigation.current
    var filter by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("Instaladas") }
    val installed = remember(state.installed) { state.installed.associateBy { it.packageName } }
    val sourceIndex = remember(state.sources) { state.sources.groupBy { it.packageName } }
    val updates = remember(state.availableExtensions, installed) { state.availableExtensions.filter { c -> installed[c.entry.packageName]?.let { c.entry.versionCode > it.versionCode } == true } }
    val available = remember(state.availableExtensions, installed) { state.availableExtensions.filter { it.entry.packageName !in installed } }
    val firstTabFocus = remember { FocusRequester() }
    val firstTab = com.pelican.comictv.ui.preferredContentEntryModifier().focusRequester(firstTabFocus)
    var help by remember { mutableStateOf(false) }
    if (help) com.pelican.comictv.library.LibraryDialog("Como usar as extensões", { help = false }) {
        Text("OK baixa ou adiciona à fila. Outro OK cancela antes de concluir. Quando ficar pronta, OK inicia a instalação com a confirmação do Android.", color = TvColors.Muted)
        Text("A busca filtra nome, fonte ou idioma. A fila pausa quando você sai desta tela.", color = TvColors.Muted)
        TvButton("Fechar ajuda", { help = false })
    }
    fun matches(name: String, extra: String = "") = filter.isBlank() || (name + " " + extra).contains(filter.trim(), true)
    val filteredInstalled = remember(state.installed, sourceIndex, filter) {
        state.installed.filter { extension -> matches(extension.name,
            sourceIndex[extension.packageName].orEmpty().joinToString { it.name + " " + it.language }) }
    }
    val filteredCandidates = remember(tab, updates, available, filter) {
        (if (tab == "Atualizações") updates else available).filter { matches(it.entry.name, (it.entry.languages + it.entry.sourceNames).joinToString()) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExtensionQueueSummary(vm) { firstTabFocus.requestFocus() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Instaladas" to state.installed.size, "Atualizações" to updates.size, "Disponíveis" to available.size).forEach { (label, count) ->
                SourceModeTab(label, count, tab == label, if (label == "Instaladas") firstTab else Modifier) { tab = label }
            }
        }
        SourceSearchControls(filter, { filter = it }, "Buscar extensão por nome, fonte ou idioma") {
            TvButton("Ajuda", { help = true })
        }
        LazyVerticalGrid(GridCells.Adaptive(120.dp * TvColors.options.textScale), Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == "Instaladas") {
                val entries = filteredInstalled
                if (entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    SourceEmptyState(if (state.busy) "Carregando extensões" else "Nenhuma extensão instalada encontrada",
                        if (state.busy) "Consultando as extensões instaladas…" else "Use a aba Disponíveis para instalar ou tente outra busca.")
                }
                items(entries, key = { it.packageName }) { extension ->
                    val sources = sourceIndex[extension.packageName].orEmpty()
                    val status = when {
                        state.loadFailures.containsKey(extension.packageName) -> "Falha ao carregar"
                        !extension.compatible -> "Incompatível"
                        sources.isNotEmpty() -> "${sources.size} fonte(s) · Abrir"
                        else -> "Sem fontes carregadas"
                    }
                    CompactExtensionCard(extension.packageName, null, extension.name, status) {
                        if (sources.size == 1) navigate { vm.activateSource(sources.first()) }
                        else if (sources.isNotEmpty()) navigate { vm.openSourceManager() } else vm.refreshInstalled()
                    }
                }
            } else {
                val entries = filteredCandidates
                if (entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    SourceEmptyState(if (tab == "Atualizações") "Atualizações" else "Extensões disponíveis", when {
                        state.busy -> "Buscando extensões…"
                        !state.extensionCatalogLoaded -> "Atualize para carregar os repositórios."
                        state.repositories.isEmpty() -> "Adicione um repositório para encontrar extensões."
                        tab == "Atualizações" -> "Todas as extensões estão atualizadas."
                        else -> "Nenhuma extensão encontrada."
                    })
                }
                items(entries, key = { it.repository.baseUrl + ":" + it.entry.packageName }) { candidate ->
                    val entry = candidate.entry
                    CompactExtensionCard(null, entry.iconUrl, entry.name, extensionTransferLabel(candidate,transfers) + " · ${entry.languages.joinToString().ifBlank { "Todos" }}",
                        downloadProgress = extensionTransferProgress(candidate,transfers)) {
                        vm.toggleExtensionDownload(candidate, install)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactExtensionCard(packageName: String?, iconUrl: String?, name: String, status: String, selected: Boolean = false, downloadProgress: Float? = null, click: () -> Unit) {
    val active = selected
    Card(onClick = click, modifier = Modifier.fillMaxWidth().semantics { this.selected = active; contentDescription = name + ", " + status }, colors = CardDefaults.colors(containerColor = TvColors.Panel),
        scale = CardDefaults.scale(focusedScale = 1.04f), border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber)))) {
        Column(Modifier.fillMaxWidth().heightIn(min = 124.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    ExtensionIcon(packageName, iconUrl, name)
                    downloadProgress?.let {ExtensionTransferProgress(it)}
                }
                if (selected) Text("Ativa", color = TvColors.Amber, fontSize = 12.sp)
            }
            Text(name.removePrefix("Tachiyomi: ").removePrefix("Mihon: "), fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, lineHeight = 20.sp, overflow = TextOverflow.Ellipsis)
            Text(status, fontSize = 13.sp, lineHeight = 18.sp, color = TvColors.Muted, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ExtensionIcon(packageName: String?, iconUrl: String?, name: String) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, packageName) {
        value = if (packageName == null) null else withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(80, 80) }.getOrNull()
        }
    }
    Box(Modifier.size(32.dp).background(TvColors.Background, RoundedCornerShape(8.dp)).padding(3.dp), contentAlignment = Alignment.Center) {
        Text(name.take(2).uppercase(), color = TvColors.Amber, fontSize = 18.sp)
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else if (!iconUrl.isNullOrBlank()) AsyncImage(iconUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun CatalogIconButton(label: String, search: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Button(onClick = click, modifier = modifier.size(44.dp).semantics { contentDescription = label },
        contentPadding = PaddingValues(10.dp),
        border = if (TvColors.useNeutralButtons) ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.Amber))) else ButtonDefaults.border(),
        colors = ButtonDefaults.colors(containerColor = TvColors.ButtonSurface, focusedContainerColor = TvColors.Action, focusedContentColor = TvColors.OnAction)) {
        val color = LocalContentColor.current
        Canvas(Modifier.size(24.dp)) {
            if (search) {
                drawCircle(color, radius = size.width * .29f, center = Offset(size.width * .4f, size.height * .4f), style = Stroke(2.dp.toPx()))
                drawLine(color, Offset(size.width * .62f, size.height * .62f), Offset(size.width * .92f, size.height * .92f), 2.dp.toPx())
            } else {
                drawRect(color, topLeft = Offset(0f, size.height * .28f), size = Size(size.width, size.height * .62f), style = Stroke(2.dp.toPx()))
                drawRect(color, topLeft = Offset(0f, size.height * .12f), size = Size(size.width * .45f, size.height * .16f), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun SourcesHome(state: SourcesState, vm: SourcesViewModel) {
    var url by rememberSaveable { mutableStateOf("") }
    var technical by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { TvButton(if (technical) "Ocultar detalhes técnicos" else "Adicionar repositório / Detalhes técnicos", { technical = !technical }) }
        if (technical) item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Adicionar repositório", fontSize = 23.sp)
                SourceInput(url, { url = it }, "URL HTTPS do repositório", urlInput = true) {
                    if (url.isNotBlank() && !state.busy) vm.addRepository(url.trim())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SourceButton("Adicionar URL", url.isNotBlank() && !state.busy) { vm.addRepository(url.trim()) }
                    SourceButton("Adicionar Keiyoushi", !state.busy) { vm.addRepository("https://raw.githubusercontent.com/keiyoushi/extensions/repo") }
                }
                Text("A instalação de cada extensão será confirmada no Android.", fontSize = 16.sp, color = TvColors.Muted)
            }
        }
        item { Text("Seus repositórios", fontSize = 23.sp) }
        if (state.repositories.isEmpty()) item { Muted("Adicione um repositório para encontrar extensões.") }
        items(state.repositories, key = { it.baseUrl }) { repository ->
            Column(Modifier.fillMaxWidth().background(TvColors.Panel).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(repository.name, fontSize = 21.sp)
                if (technical) {
                    Muted(repository.baseUrl)
                    Muted("Assinatura do repositório: ${repository.fingerprint}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton("Ver extensões", { vm.selectRepository(repository.baseUrl) })
                    SourceButton("Remover repositório", !state.busy) { vm.removeRepository(repository.baseUrl) }
                }
            }
        }
        if (state.installed.isNotEmpty()) item { Text("Extensões no aparelho", fontSize = 23.sp) }
        items(state.installed, key = { it.packageName }) { extension ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${extension.name} · ${extension.versionName}", fontSize = 18.sp)
                Muted(if (extension.compatible) "API compatível · funcionamento depende da fonte" else "API incompatível com este aplicativo")
                if (technical) Muted(extension.packageName)
                state.loadFailures[extension.packageName]?.let { reason ->
                    Text("Falha ao abrir: $reason", color = TvColors.Amber, fontSize = 17.sp)
                    SourceButton("Tentar carregar novamente", !state.busy) { vm.refreshInstalled() }
                }
                if (technical) Muted("Certificado: ${extension.fingerprints.joinToString()}")
            }
        }
    }
}

@Composable
private fun RepositoryExtensions(state: SourcesState, vm: SourcesViewModel, install: (File) -> Unit) {
    val transfers by vm.extensionDownloads.collectAsStateWithLifecycle()
    var filter by rememberSaveable(state.selectedRepository) { mutableStateOf("") }
    val repository = state.repositories.firstOrNull { it.baseUrl == state.selectedRepository }
    val entries = state.extensions.filter { entry ->
        filter.isBlank() || (listOf(entry.name) + entry.languages + entry.sourceNames).any { it.contains(filter.trim(), ignoreCase = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ExtensionQueueSummary(vm)
        SourceInput(filter, { filter = it }, "Buscar em " + (repository?.name ?: "repositório") + " · " + entries.size + " extensões")
        LazyVerticalGrid(GridCells.Adaptive(120.dp * TvColors.options.textScale), Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                SourceEmptyState(if (state.busy) "Carregando extensões" else "Nenhuma extensão encontrada",
                    if (state.busy) "Consultando este repositório…" else "Tente outro nome ou idioma na busca.")
            }
            items(entries, key = { it.packageName }) { entry ->
                val installed = state.installed.firstOrNull { it.packageName == entry.packageName }
                val newer = installed != null && entry.versionCode > installed.versionCode
                val action = when { newer -> "Atualizar"; installed != null -> "Instalada"; else -> "Instalar" }
                val transferAction = if (repository != null && (installed == null || newer))
                    extensionTransferLabel(AvailableExtension(repository,entry),transfers) else action
                CompactExtensionCard(installed?.packageName, entry.iconUrl, entry.name,
                    transferAction + " · " + entry.versionName + "\n" + entry.languages.joinToString().ifBlank { "Todos os idiomas" },
                    downloadProgress = repository?.let {extensionTransferProgress(AvailableExtension(it,entry),transfers)}) {
                    if (installed == null || newer) vm.toggleRepositoryExtension(entry,install)
                    else if (!state.busy) {
                            val sources = state.sources.filter { it.packageName == entry.packageName }
                            if (sources.size == 1) vm.activateSource(sources.first())
                            else if (sources.isNotEmpty()) vm.openSourceManager()
                            else vm.refreshInstalled()
                    }
                }
            }
        }
    }
}

internal fun extensionTransferLabel(candidate: AvailableExtension, transfers: List<ExtensionTransfer>): String {
    val row=transfers.firstOrNull {it.candidate.entry.packageName == candidate.entry.packageName} ?: return "Baixar · OK adiciona à fila"
    if (row.candidate != candidate) return "Outra versão na fila · OK troca/cancela"
    return when(row.status) {
        ExtensionTransferStatus.Queued -> "Na fila · OK cancela"
        ExtensionTransferStatus.Downloading -> "Baixando " + (if(row.total > 0) "${(row.bytes*100/row.total).coerceIn(0,100)}%" else "${row.bytes/1024} KB") + " · OK cancela"
        ExtensionTransferStatus.Ready -> "Baixada · OK instalar"
        ExtensionTransferStatus.Failed -> "Falha · OK tentar novamente"
    }
}

internal fun extensionTransferProgress(candidate: AvailableExtension, transfers: List<ExtensionTransfer>): Float? {
    val row=transfers.firstOrNull {it.candidate == candidate} ?: return null
    return when(row.status) {
        ExtensionTransferStatus.Ready -> 1f
        ExtensionTransferStatus.Queued -> 0f
        ExtensionTransferStatus.Downloading -> if(row.total>0) (row.bytes.toDouble()/row.total).toFloat().coerceIn(0f,1f) else null
        ExtensionTransferStatus.Failed -> null
    }
}

@Composable internal fun ExtensionTransferProgress(progress: Float) {
    val fraction=progress.coerceIn(0f,1f)
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically,
        modifier=Modifier.semantics {progressBarRangeInfo=androidx.compose.ui.semantics.ProgressBarRangeInfo(fraction,0f..1f)}) {
        com.pelican.comictv.ui.ProgressTrack(fraction,Modifier.width(44.dp).height(3.dp))
        Text("${(fraction*100).toInt()}%",color=TvColors.Amber,fontSize=11.sp)
    }
}

@Composable private fun ExtensionQueueSummary(vm: SourcesViewModel, beforeClear: () -> Unit = {}) {
    val transfers by vm.extensionDownloads.collectAsStateWithLifecycle()
    if(transfers.isNotEmpty()) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Fila: ${transfers.count{it.status==ExtensionTransferStatus.Queued}} · Baixando: ${transfers.count{it.status==ExtensionTransferStatus.Downloading}} · Prontas: ${transfers.count{it.status==ExtensionTransferStatus.Ready}}",fontSize=16.sp)
            TvButton("Limpar fila", { beforeClear(); vm.clearExtensionDownloads() })
        }
        transfers.lastOrNull{it.error!=null}?.error?.let {Text(it,color=TvColors.Amber,fontSize=15.sp)}
    }
}

@Composable
private fun SourceCatalog(state: SourcesState, vm: SourcesViewModel) {
    val navigate = LocalContentNavigation.current
    var query by rememberSaveable(state.activeSource?.packageName, state.activeSource?.id) { mutableStateOf(state.query) }
    val searchOwner = remember { Any() }
    DisposableEffect(vm, searchOwner) { onDispose { vm.stopLiveSearch(searchOwner) } }
    LaunchedEffect(state.activeSource, state.latest) { query = state.query }
    val grid = rememberLazyGridState()
    val enteringSection = com.pelican.comictv.ui.LocalSectionContentEntry.current
    val sectionEntry = remember { enteringSection }
    val firstTitle = catalogFirstEntryModifier(state.titles.size, state.busy, state.catalogLoaded, grid)
    val warmEntries = remember(state.titles) { state.titles.map {com.pelican.comictv.ui.CoverWarmEntry(it.key,it.coverUrl)} }
    com.pelican.comictv.ui.WarmGridCovers(grid, warmEntries)
    val titleIndex = remember(state.titles) { state.titles.associateBy { it.key } }
    val currentTitleIndex by rememberUpdatedState(titleIndex)
    val previewsEnabled by rememberUpdatedState(!state.sourcesRefreshing)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.activeSource, state.sourceRevision, grid, vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { coroutineScope {
        val previews = VisibleTitlePreviews(this) { vm.resolveTitlePreview(it) }
        try {
            snapshotFlow {
                val titles = currentTitleIndex
                if (!previewsEnabled) emptyList() else grid.layoutInfo.visibleItemsInfo
                    .filter { it.offset.y + it.size.height > grid.layoutInfo.viewportStartOffset && it.offset.y < grid.layoutInfo.viewportEndOffset }
                    .mapNotNull { titles[it.key] }
            }.collect { previews.submit(it) }
        } finally { previews.close() }
    } } }
    val restore = remember { FocusRequester() }
    var focusedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreTarget by remember { mutableStateOf<String?>(null) }
    var positioned by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (sectionEntry) return@LaunchedEffect
        val index = state.titles.indexOfFirst { it.key == focusedTitle }
        if (index >= 0) {
            restoreTarget = state.titles[index].key
            grid.scrollToItem(index + 3)
            snapshotFlow { positioned == restoreTarget }.first { it }
            restore.requestFocus()
            restoreTarget = null
        }
    }
    var searchVisible by rememberSaveable { mutableStateOf(state.query.isNotBlank()) }
    var previousCatalog by rememberSaveable { mutableStateOf("${state.latest}:${state.query}") }
    LaunchedEffect(state.latest, state.query) {
        val catalog = "${state.latest}:${state.query}"
        if (catalog != previousCatalog) {
            previousCatalog = catalog
            focusedTitle = null
            restoreTarget = null
            grid.scrollToItem(0)
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(124.dp * TvColors.options.textScale), Modifier.fillMaxSize().focusRestorer(), state = grid,
        contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "catalog-heading", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SourceSectionHeading(state.activeSource?.name ?: "Fontes online")
                        Text("${if (state.latest) "Novidades" else "Catálogo"} · ${state.activeSource?.language.orEmpty()} · ${state.titles.size} títulos carregados",
                            fontSize = 14.sp, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CatalogIconButton("Trocar fonte", false, if (state.titles.isEmpty()) firstTitle else Modifier) { navigate { vm.openSourceManager() } }
                    Spacer(Modifier.width(8.dp))
                    CatalogIconButton(if (searchVisible) "Fechar busca" else "Buscar", true) { searchVisible = !searchVisible }
                }
                if (state.busy) Text("Carregando…", color = TvColors.Amber, fontSize = 16.sp)
                state.message?.let { Text(it, color = TvColors.Amber, fontSize = 16.sp) }
            }
        }
        item(key = "catalog-controls", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(0.dp)) }
        // Keep three header entries even while search is collapsed, so restoration has a stable offset.
        item(key = "catalog-search", span = { GridItemSpan(maxLineSpan) }) {
            if (searchVisible) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                SourceInput(query, { query = it.take(200); vm.searchWhileTyping(query, searchOwner) }, "Buscar nesta fonte · automático", Modifier.weight(1f), maxLength = 200) { vm.search(query.trim(), searchOwner) }
                SourceButton("Buscar agora", true) { vm.search(query.trim(), searchOwner) }
            }
        }
        if (state.titles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            SourceEmptyState(if (state.busy) "Buscando histórias" else "Nenhum título encontrado",
                if (state.busy) "Carregando o catálogo da fonte…" else "Tente outra busca ou escolha uma fonte pelo ícone de pasta.")
        }
        items(state.titles, key = { it.key }) { title ->
            val label = title.title.ifBlank { "Título não informado" }
            var loaded by remember(title.coverUrl) { mutableStateOf(false) }
            var failed by remember(title.coverUrl) { mutableStateOf(false) }
            val focus = if (restoreTarget == title.key) Modifier.focusRequester(restore).onGloballyPositioned { positioned = title.key } else Modifier
            Card(onClick = { vm.showDetails(title) }, modifier = focus
                .then(if (title.key == state.titles.firstOrNull()?.key) firstTitle else Modifier).fillMaxWidth()
                .semantics { contentDescription = label }
                .onFocusChanged { if (it.isFocused) focusedTitle = title.key }, colors = CardDefaults.colors(containerColor = TvColors.Panel),
                scale = CardDefaults.scale(focusedScale = 1.04f), border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber)))) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(TvColors.Background)) {
                        if (!loaded) Text(if (title.coverUrl.isNullOrBlank() || failed) "Capa indisponível" else "Carregando capa…",
                            Modifier.align(Alignment.Center).padding(10.dp), color = TvColors.Muted, fontSize = 13.sp)
                        if (!title.coverUrl.isNullOrBlank()) AsyncImage(title.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                            onSuccess = { loaded = true }, onError = { failed = true })
                    }
                    Text(label, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
                }
            }
        }
        if (state.hasNextPage) item(span = { GridItemSpan(maxLineSpan) }) { SourceButton("Carregar mais", !state.busy) { vm.loadMore() } }
    }
}

@Composable
internal fun SourceDetails(details: OnlineDetails, busy: Boolean, open: (OnlineChapter) -> Unit) {
    val summaryScroll = rememberLazyListState()
    val chapterScroll = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var orderName by rememberSaveable(details.title.key) {mutableStateOf(ChapterOrder.Ascending.name)}
    val order=restoreChapterOrder(orderName)
    val chapters=remember(details.chapters,order) {orderedChapters(details.chapters,order)}
    var summaryFocused by remember { mutableStateOf(false) }
    var expandedSummary by rememberSaveable(details.title.key) { mutableStateOf(false) }
    var summaryOverflows by remember(details.title.key, details.title.description) { mutableStateOf(false) }
    val summary = details.title.description?.trim()?.takeIf { it.isNotEmpty() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        LazyColumn(
            Modifier.width(280.dp).fillMaxHeight()
                .border(if (summaryFocused) 2.dp else 0.dp, TvColors.Amber)
                .onFocusChanged { summaryFocused = it.hasFocus }
                .textPanelScroll(summaryScroll),
            state = summaryScroll,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        details.title.coverUrl, "Capa de ${details.title.title}",
                        Modifier.width(190.dp).aspectRatio(2f / 3f), contentScale = ContentScale.Fit,
                    )
                }
            }
            item { Text(details.title.title, fontSize = 24.sp, lineHeight = 32.sp) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sinopse", fontSize = 18.sp, lineHeight = 26.sp)
                    if (summary != null && (summaryOverflows || expandedSummary)) {
                        TvButton(
                            if (expandedSummary) "Recolher sinopse" else "Mostrar sinopse completa",
                            { expandedSummary = !expandedSummary },
                        )
                    }
                    Text(
                        summary ?: "Sinopse não informada pela fonte.",
                        color = TvColors.Muted, fontSize = 16.sp, lineHeight = 24.sp,
                        maxLines = if (expandedSummary) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expandedSummary) summaryOverflows = it.hasVisualOverflow },
                    )

                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Capítulos · ${chapters.size}", fontSize = 23.sp, lineHeight = 30.sp)
                Muted(when(order) {
                    ChapterOrder.Source -> "Na sequência fornecida pela fonte"
                    ChapterOrder.Ascending -> "Numeração crescente"
                    ChapterOrder.Descending -> "Numeração decrescente"
                })
                ChapterOrderControls(order) { next ->
                    orderName=next.name
                    scrollScope.launch {chapterScroll.scrollToItem(0)}
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().focusRestorer(),
                state = chapterScroll,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (chapters.isEmpty()) item { Muted("Esta fonte não retornou capítulos.") }
                items(chapters, key = { it.key }) { chapter ->
                    SourceButton(chapter.title, !busy, Modifier.fillMaxWidth()) { if (!busy) open(chapter) }
                }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ChapterOrderControls(order: ChapterOrder, onChange: (ChapterOrder) -> Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        for (choice in ChapterOrder.entries) com.pelican.comictv.ui.TvChoice(choice.label,order==choice,{if(order!=choice) onChange(choice)})
    }
}
@Composable
internal fun SourceInput(value: String, change: (String) -> Unit, label: String, modifier: Modifier = Modifier, urlInput: Boolean = false, maxLength: Int? = null, submit: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TvColors.Muted, fontSize = 14.sp)
        com.pelican.comictv.ui.TvTextInput(value, change, Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .onFocusChanged { focused = it.hasFocus }
            .background(TvColors.Panel, RoundedCornerShape(8.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) TvColors.Amber else TvColors.Muted.copy(alpha = if (TvColors.options.highContrast) 1f else .45f), RoundedCornerShape(8.dp)).padding(12.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(TvColors.Amber), keyboardOptions = KeyboardOptions(keyboardType = if (urlInput) KeyboardType.Uri else KeyboardType.Text, imeAction = ImeAction.Search),
            onSubmit = submit, maxLength = maxLength)
    }
}

@Composable
private fun Muted(text: String) { Text(text, color = TvColors.Muted, fontSize = 16.sp, lineHeight = 23.sp) }

@Composable
private fun SourceButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Button(onClick = click, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.colors(containerColor = TvColors.ButtonSurface, focusedContainerColor = TvColors.Action, focusedContentColor = TvColors.OnAction),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.Amber)))) {
        Text(label, fontSize = 17.sp)
    }
}

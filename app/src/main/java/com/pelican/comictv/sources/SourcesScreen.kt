package com.pelican.comictv.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import java.io.File
import kotlinx.coroutines.flow.first

@Composable
fun SourcesScreen(viewModel: SourcesViewModel, onRead: (String) -> Unit, onInstall: (File) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pages = rememberSaveableStateHolder()
    val nested = state.details != null || state.selectedRepository != null || (state.managerPage != null && state.activeSource != null)
    BackHandler(enabled = nested) { viewModel.back() }
    if (state.activeSource != null && state.details == null && state.managerPage == null) {
        val source = state.activeSource!!
        pages.SaveableStateProvider("catalog:${source.packageName}:${source.id}") { SourceCatalog(state, viewModel) }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when {
                state.details != null -> "Detalhes"
                state.managerPage == SourceManagerPage.EXTENSIONS -> "Extensões"
                state.managerPage == SourceManagerPage.REPOSITORIES -> "Repositórios"
                else -> "Escolher fonte"
            }, Modifier.weight(1f), fontSize = 24.sp)
            if (state.managerPage == SourceManagerPage.EXTENSIONS) {
                SourceButton("Atualizar", !state.busy) { viewModel.refreshInstalled(); viewModel.refreshExtensionCatalogs() }
                TvButton("Repositórios", { viewModel.openSourceManager(SourceManagerPage.REPOSITORIES) })
            }
            if (state.managerPage == SourceManagerPage.REPOSITORIES) TvButton("Extensões", { viewModel.openExtensions() })
            if (state.selectedRepository != null) TvButton("Atualizar repositório", { viewModel.refresh() })
        }
        if (state.busy) Text("Carregando…", color = TvColors.Amber, fontSize = 18.sp)
        state.message?.let { Text(it, color = TvColors.Amber, fontSize = 17.sp) }
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
                state.selectedRepository != null -> RepositoryExtensions(state) { viewModel.prepareInstall(it, onInstall) }
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
    var filter by rememberSaveable { mutableStateOf("") }
    val sources = state.sources.filter { filter.isBlank() || it.name.contains(filter.trim(), true) || it.language.contains(filter.trim(), true) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SourceInput(filter, { filter = it }, "Buscar fonte · "+state.sources.size+" disponíveis")
        LazyVerticalGrid(GridCells.Adaptive(128.dp), Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sources.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Muted(if (state.busy) "Carregando fontes…" else if (state.sources.isEmpty()) "Instale extensões pela seção Extensões para começar." else "Nenhuma fonte encontrada.")
            }
            items(sources, key = { it.packageName + ":" + it.id }) { source ->
                Card(onClick = { vm.activateSource(source) }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.colors(containerColor = TvColors.Panel),
                    scale = CardDefaults.scale(focusedScale = 1.04f),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber)))) {
                    Column(Modifier.fillMaxWidth().height(128.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExtensionIcon(source.packageName, null, source.name)
                        Text(source.name, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(source.language + if (state.activeSource?.let { it.packageName == source.packageName && it.id == source.id } == true) " · Selecionada" else "", fontSize = 14.sp, color = TvColors.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionsHome(state: SourcesState, vm: SourcesViewModel, install: (File) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("Instaladas") }
    val installed = state.installed.associateBy { it.packageName }
    val updates = state.availableExtensions.filter { c -> installed[c.entry.packageName]?.let { c.entry.versionCode > it.versionCode } == true }
    val available = state.availableExtensions.filter { it.entry.packageName !in installed }
    fun matches(name: String, extra: String = "") = filter.isBlank() || (name + " " + extra).contains(filter.trim(), true)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Instaladas" to state.installed.size, "Atualizações" to updates.size, "Disponíveis" to available.size).forEach { (label, count) ->
                SourceButton("$label · $count${if (tab == label) " ✓" else ""}", true) { tab = label }
            }
        }
        SourceInput(filter, { filter = it }, "Buscar extensão por nome, fonte ou idioma")
        LazyVerticalGrid(GridCells.Adaptive(128.dp), Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == "Instaladas") {
                val entries = state.installed.filter { extension -> matches(extension.name, state.sources.filter { it.packageName == extension.packageName }.joinToString { it.name + " " + it.language }) }
                if (entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Muted("Nenhuma extensão instalada encontrada.") }
                items(entries, key = { it.packageName }) { extension ->
                    val sources = state.sources.filter { it.packageName == extension.packageName }
                    val status = when {
                        state.loadFailures.containsKey(extension.packageName) -> "Falha ao carregar"
                        !extension.compatible -> "Incompatível"
                        sources.isNotEmpty() -> "${sources.size} fonte(s) · Abrir"
                        else -> "Sem fontes carregadas"
                    }
                    CompactExtensionCard(extension.packageName, null, extension.name, status) {
                        if (sources.size == 1) vm.activateSource(sources.first()) else if (sources.isNotEmpty()) vm.openSourceManager() else vm.refreshInstalled()
                    }
                }
            } else {
                val entries = (if (tab == "Atualizações") updates else available).filter { matches(it.entry.name, (it.entry.languages + it.entry.sourceNames).joinToString()) }
                if (entries.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Muted(when {
                        state.busy -> "Buscando extensões…"
                        !state.extensionCatalogLoaded -> "Atualize para carregar os repositórios."
                        state.repositories.isEmpty() -> "Adicione um repositório para encontrar extensões."
                        tab == "Atualizações" -> "Todas as extensões estão atualizadas."
                        else -> "Nenhuma extensão encontrada."
                    })
                }
                items(entries, key = { it.repository.baseUrl + ":" + it.entry.packageName }) { candidate ->
                    val entry = candidate.entry
                    CompactExtensionCard(null, entry.iconUrl, entry.name, "${if (tab == "Atualizações") "Atualizar" else "Instalar"} · ${entry.languages.joinToString().ifBlank { "Todos" }}") {
                        if (!state.busy) vm.prepareAvailableInstall(candidate, install)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactExtensionCard(packageName: String?, iconUrl: String?, name: String, status: String, click: () -> Unit) {
    Card(onClick = click, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.colors(containerColor = TvColors.Panel),
        scale = CardDefaults.scale(focusedScale = 1.04f), border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber)))) {
        Column(Modifier.fillMaxWidth().height(128.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ExtensionIcon(packageName, iconUrl, name)
            Text(name, fontSize = 16.sp, maxLines = 2, lineHeight = 18.sp, overflow = TextOverflow.Ellipsis)
            Text(status, fontSize = 12.sp, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Box(Modifier.size(40.dp).background(TvColors.Ink), contentAlignment = Alignment.Center) {
        Text(name.take(2).uppercase(), color = TvColors.Amber, fontSize = 18.sp)
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else if (!iconUrl.isNullOrBlank()) AsyncImage(iconUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun CatalogIconButton(label: String, search: Boolean, click: () -> Unit) {
    Button(onClick = click, modifier = Modifier.size(44.dp).semantics { contentDescription = label },
        contentPadding = PaddingValues(10.dp), colors = ButtonDefaults.colors(containerColor = TvColors.Panel, focusedContainerColor = TvColors.Amber, focusedContentColor = TvColors.Ink)) {
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
private fun RepositoryExtensions(state: SourcesState, install: (ExtensionEntry) -> Unit) {
    var filter by rememberSaveable(state.selectedRepository) { mutableStateOf("") }
    val repository = state.repositories.firstOrNull { it.baseUrl == state.selectedRepository }
    val entries = state.extensions.filter { entry ->
        filter.isBlank() || (listOf(entry.name) + entry.languages + entry.sourceNames).any { it.contains(filter.trim(), ignoreCase = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repository?.let { Text(it.name, fontSize = 24.sp) }
        SourceInput(filter, { filter = it }, "Filtrar por nome ou idioma")
        LazyColumn(Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (entries.isEmpty()) item { Muted(if (state.busy) "Buscando extensões…" else "Nenhuma extensão encontrada.") }
            items(entries, key = { it.packageName }) { entry ->
                val installed = state.installed.firstOrNull { it.packageName == entry.packageName }
                val newer = installed != null && entry.versionCode > installed.versionCode
                Row(Modifier.fillMaxWidth().background(TvColors.Panel).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.name, fontSize = 22.sp)
                        Muted("${entry.versionName} · ${entry.languages.joinToString().ifBlank { "Todos os idiomas" }}")
                        if (entry.sourceNames.isNotEmpty()) Muted(entry.sourceNames.joinToString())
                        if (installed != null) Muted("Instalada: ${installed.versionName}")
                    }
                    SourceButton(when { newer -> "Atualizar"; installed != null -> "Instalada"; else -> "Instalar" }, !state.busy && (installed == null || newer)) { install(entry) }
                }
            }
        }
    }
}

@Composable
private fun SourceCatalog(state: SourcesState, vm: SourcesViewModel) {
    var query by rememberSaveable(state.activeSource?.id) { mutableStateOf(state.query) }
    val grid = rememberLazyGridState()
    val restore = remember { FocusRequester() }
    var focusedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreTarget by remember { mutableStateOf<String?>(null) }
    var positioned by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
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
    LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.fillMaxSize().focusRestorer(), state = grid,
        contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "catalog-heading", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.activeSource?.name ?: "Fontes online", fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (state.latest) "Novidades" else "Catálogo"} · ${state.activeSource?.language.orEmpty()}", fontSize = 14.sp, color = TvColors.Muted)
                    }
                    CatalogIconButton("Trocar fonte", false) { vm.openSourceManager() }
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
                SourceInput(query, { query = it }, "Buscar nesta fonte", Modifier.weight(1f)) { if (!state.busy) vm.search(query.trim()) }
                SourceButton("Buscar", !state.busy && query.isNotBlank()) { vm.search(query.trim()) }
            }
        }
        if (state.titles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Muted(if (state.busy) "Buscando histórias…" else "Nenhum título encontrado.") }
        items(state.titles, key = { it.key }) { title ->
            LaunchedEffect(state.activeSource, title.key) {
                if (title.coverUrl.isNullOrBlank()) vm.resolveTitlePreview(title)
            }
            val label = title.title.ifBlank { "Título não informado" }
            var loaded by remember(title.coverUrl) { mutableStateOf(false) }
            var failed by remember(title.coverUrl) { mutableStateOf(false) }
            val focus = if (restoreTarget == title.key) Modifier.focusRequester(restore).onGloballyPositioned { positioned = title.key } else Modifier
            Card(onClick = { vm.showDetails(title) }, modifier = focus.fillMaxWidth()
                .semantics { contentDescription = label }
                .onFocusChanged { if (it.isFocused) focusedTitle = title.key }, colors = CardDefaults.colors(containerColor = TvColors.Panel),
                scale = CardDefaults.scale(focusedScale = 1.04f), border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber)))) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(TvColors.Panel)) {
                    if (!loaded) Text(if (title.coverUrl.isNullOrBlank() || failed) "Capa indisponível" else "Carregando capa…",
                        Modifier.align(Alignment.Center).padding(10.dp), color = TvColors.Muted, fontSize = 14.sp)
                    if (!title.coverUrl.isNullOrBlank()) AsyncImage(title.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        onSuccess = { loaded = true }, onError = { failed = true })
                    Text(label, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f), Color.Black)))
                        .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
                        minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, lineHeight = 20.sp)
                }
            }
        }
        if (state.hasNextPage) item(span = { GridItemSpan(maxLineSpan) }) { SourceButton("Carregar mais", !state.busy) { vm.loadMore() } }
    }
}

@Composable
private fun SourceDetails(details: OnlineDetails, busy: Boolean, open: (OnlineChapter) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AsyncImage(details.title.coverUrl, details.title.title, Modifier.width(150.dp).aspectRatio(2f / 3f), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(details.title.title, fontSize = 28.sp)
                    Text(details.title.description?.takeIf { it.isNotBlank() } ?: "Sinopse não informada pela fonte.", color = TvColors.Muted, fontSize = 18.sp, lineHeight = 27.sp)
                }
            }
        }
        item { Text("Capítulos · ${details.chapters.size}", fontSize = 23.sp); Muted("Na sequência fornecida pela fonte") }
        if (details.chapters.isEmpty()) item { Muted("Esta fonte não retornou capítulos.") }
        items(details.chapters, key = { it.key }) { chapter -> SourceButton(chapter.title, !busy, Modifier.fillMaxWidth()) { open(chapter) } }
    }
}

@Composable
private fun SourceInput(value: String, change: (String) -> Unit, label: String, modifier: Modifier = Modifier, urlInput: Boolean = false, submit: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = TvColors.Muted, fontSize = 16.sp)
        BasicTextField(value, change, Modifier.fillMaxWidth().heightIn(min = 50.dp)
            .onFocusChanged { focused = it.hasFocus }
            .background(TvColors.Panel).border(if (focused) 3.dp else 1.dp, if (focused) TvColors.Amber else TvColors.Muted).padding(14.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp), singleLine = true,
            cursorBrush = SolidColor(TvColors.Amber), keyboardOptions = KeyboardOptions(keyboardType = if (urlInput) KeyboardType.Uri else KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }))
    }
}

@Composable
private fun Muted(text: String) { Text(text, color = TvColors.Muted, fontSize = 16.sp, lineHeight = 23.sp) }

@Composable
private fun SourceButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Button(onClick = click, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.colors(containerColor = TvColors.Panel, focusedContainerColor = TvColors.Amber, focusedContentColor = TvColors.Ink),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.Amber)))) {
        Text(label, fontSize = 17.sp)
    }
}

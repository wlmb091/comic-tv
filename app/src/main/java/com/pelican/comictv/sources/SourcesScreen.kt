package com.pelican.comictv.sources

import androidx.activity.compose.BackHandler
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
    val nested = state.details != null || state.activeSource != null || state.selectedRepository != null
    BackHandler(enabled = nested) { viewModel.back() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Fontes online", state.activeSource?.let { "${it.name} · ${it.language}" }
            ?: "Repositórios, extensões e histórias para explorar")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (nested) TvButton("Voltar", { viewModel.back() })
            if (state.activeSource == null) TvButton("Atualizar instaladas", { viewModel.refreshInstalled() })
            if (state.selectedRepository != null && state.activeSource == null) TvButton("Atualizar repositório", { viewModel.refresh() })
        }
        if (state.busy) Text("Carregando…", color = TvColors.Amber, fontSize = 18.sp)
        state.message?.let { Text(it, color = TvColors.Amber, fontSize = 17.sp) }
        Box(Modifier.weight(1f)) {
            val page = when {
                state.details != null -> "details:${state.activeSource?.id}:${state.details!!.title.key}"
                state.activeSource != null -> "catalog:${state.activeSource!!.packageName}:${state.activeSource!!.id}"
                state.selectedRepository != null -> "repository:${state.selectedRepository}"
                else -> "home"
            }
            pages.SaveableStateProvider(page) {
            when {
                state.details != null -> SourceDetails(state.details!!, state.busy) { viewModel.openChapter(it, onRead) }
                state.activeSource != null -> SourceCatalog(state, viewModel)
                state.selectedRepository != null -> RepositoryExtensions(state) { viewModel.prepareInstall(it, onInstall) }
                else -> SourcesHome(state, viewModel)
            }
            }
        }
    }
}

@Composable
private fun SourcesHome(state: SourcesState, vm: SourcesViewModel) {
    var url by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().focusRestorer(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
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
                Muted(repository.baseUrl)
                Muted("Assinatura do repositório: ${repository.fingerprint}")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton("Ver extensões", { vm.selectRepository(repository.baseUrl) })
                    SourceButton("Remover repositório", !state.busy) { vm.removeRepository(repository.baseUrl) }
                }
            }
        }
        item { Text("Fontes instaladas", fontSize = 23.sp) }
        if (state.sources.isEmpty()) item { Muted("Nenhuma fonte disponível. Instale uma extensão e atualize a lista.") }
        items(state.sources, key = { "${it.packageName}:${it.id}" }) { source ->
            TvButton("${source.name} · ${source.language} — Explorar", { vm.activateSource(source) }, Modifier.fillMaxWidth())
        }
        if (state.installed.isNotEmpty()) item { Text("Extensões no aparelho", fontSize = 23.sp) }
        items(state.installed, key = { it.packageName }) { extension ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${extension.name} · ${extension.versionName}", fontSize = 18.sp)
                Muted(if (extension.compatible) "API compatível · funcionamento depende da fonte" else "API incompatível com este aplicativo")
                Muted("Certificado: ${extension.fingerprints.joinToString()}")
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
        repository?.let { Text(it.name, fontSize = 24.sp); Muted("Assinatura: ${it.fingerprint}") }
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
                        Muted(entry.packageName)
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
            grid.scrollToItem(index)
            snapshotFlow { positioned == restoreTarget }.first { it }
            restore.requestFocus()
            restoreTarget = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(if (!state.latest && state.query.isBlank()) "Populares ✓" else "Populares", { vm.showCatalog(false) })
            TvButton(if (state.latest && state.query.isBlank()) "Recentes ✓" else "Recentes", { vm.showCatalog(true) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SourceInput(query, { query = it }, "Buscar nesta fonte", Modifier.weight(1f)) { if (!state.busy) vm.search(query.trim()) }
            SourceButton("Buscar", !state.busy && query.isNotBlank()) { vm.search(query.trim()) }
        }
        LazyVerticalGrid(GridCells.Adaptive(145.dp), Modifier.fillMaxSize().focusRestorer(), state = grid, contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            if (state.titles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Muted(if (state.busy) "Buscando histórias…" else "Nenhum título encontrado.") }
            items(state.titles, key = { it.key }) { title ->
                val focus = if (restoreTarget == title.key) Modifier.focusRequester(restore).onGloballyPositioned { positioned = title.key } else Modifier
                Card(onClick = { vm.showDetails(title) }, modifier = focus.onFocusChanged { if (it.isFocused) focusedTitle = title.key }, colors = CardDefaults.colors(containerColor = TvColors.Panel),
                    scale = CardDefaults.scale(focusedScale = 1.04f), border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber)))) {
                    Column {
                        AsyncImage(title.coverUrl, title.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(TvColors.Panel), contentScale = ContentScale.Fit)
                        Text(title.title, Modifier.padding(12.dp), maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                    }
                }
            }
            if (state.hasNextPage) item(span = { GridItemSpan(maxLineSpan) }) { SourceButton("Carregar mais", !state.busy) { vm.loadMore() } }
        }
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

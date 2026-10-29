package com.pelican.comictv.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Border
import coil3.compose.AsyncImage
import com.pelican.comictv.library.ComicEntity
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.library.libraryCardKeys
import com.pelican.comictv.sources.*
import com.pelican.comictv.ui.*
import kotlinx.coroutines.*
import java.io.File
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Keep bounded first-page results when details covers this destination; do not put covers in saved state. */
internal class GlobalSearchSession : ViewModel() {
    val batch = mutableStateOf(SearchBatch())
    var requestKey: String? = null
    private var request: Job? = null
    fun search(key: String, sources: List<SourceHandle>, query: String, latest: Boolean, runner: GlobalSearch) {
        if (requestKey == key) return
        request?.cancel()
        requestKey = key
        batch.value = SearchBatch(total = sources.size, running = sources.isNotEmpty())
        request = viewModelScope.launch {
            delay(400)
            runner.search(sources, query, latest).collect { if (requestKey == key) batch.value = it }
        }
    }
    fun clear() { request?.cancel(); requestKey = null; batch.value = SearchBatch() }
    fun pause() { request?.cancel(); batch.value = batch.value.copy(running = false) }
}

/** Extra provider results can be supplied without mixing metadata lookup with extension discovery. */
@Composable fun GlobalSearchScreen(comics: List<ComicEntity>, offlineIds: Set<String>, sources: SourcesViewModel,
    onLocal: (String) -> Unit, onOnline: (SearchHit) -> Unit, onExtensions: () -> Unit,
    onBack: () -> Unit, onBrowserOpen: (() -> Unit)? = null, localCover: (String) -> Any? = { null },
    extraResults: @Composable (String) -> Unit = {}) {
    val sourceState by sources.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf(SearchCategory.ALL.name) }
    val category = SearchCategory.valueOf(categoryName)
    var latest by rememberSaveable { mutableStateOf(false) }
    var browseRequested by rememberSaveable { mutableStateOf(false) }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    var sourceGroup by rememberSaveable { mutableIntStateOf(0) }
    var lastFocus by rememberSaveable { mutableStateOf("query") }
    var restoredFocus by remember { mutableStateOf(false) }
    val queryFocus = remember { FocusRequester() }
    val list = rememberLazyGridState()
    var optionsKey by remember { mutableStateOf<String?>(null) }
    var optionsTitle by remember { mutableStateOf("") }
    var pendingClassification by remember { mutableStateOf<Pair<String, SearchCategory>?>(null) }
    val categorySaver = listSaver<MutableMap<String, String>, String>(
        save = { it.flatMap { entry -> listOf(entry.key, entry.value) } },
        restore = { it.chunked(2).associate { pair -> pair[0] to pair[1] }.toMutableMap() })
    val classifications = rememberSaveable(saver = categorySaver) { mutableStateMapOf<String, String>() as MutableMap<String, String> }
    // Map restored by the saver is wrapped by a revision so classification remains observable.
    var classificationRevision by remember { mutableIntStateOf(0) }
    fun visible(key: String): Boolean { classificationRevision; return categoryVisible(category, classifications[key]?.let(SearchCategory::valueOf)) }
    LaunchedEffect(pendingClassification) {
        pendingClassification?.let { (key, type) ->
            // The dialog has closed: park on a persistent field before removing a filtered card.
            list.scrollToItem(0)
            withFrameNanos { }
            queryFocus.requestFocus(); lastFocus = "query"
            withFrameNanos { }
            classifications[key] = type.name; classificationRevision++
            pendingClassification = null
        }
    }
    val trustedSources = remember(sourceState.sources, sourceState.installed, sourceState.repositories) { sources.globalSearchSources() }
    val groupCount = ((trustedSources.size + 19) / 20).coerceAtLeast(1)
    val group = sourceGroup.coerceIn(0, groupCount - 1)
    val selectedSources = remember(trustedSources, group) { trustedSources.drop(group * 20).take(20) }
    val session: GlobalSearchSession = viewModel()
    var batch by session.batch
    var links by remember { mutableStateOf(emptyMap<String, String>()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val runner = remember(sources) { GlobalSearch(sources::globalSearchPage) }
    LaunchedEffect(comics.map { it.id }) { links = sources.globalSearchLibraryLinks(comics.map { it.id }) }
    val onlineRequested = query.isNotBlank() || browseRequested
    LaunchedEffect(query, latest, browseRequested, selectedSources, retry, lifecycle) {
        if (!onlineRequested) { session.clear(); return@LaunchedEffect }
        val requestKey = "$query|$latest|$browseRequested|$retry|" + selectedSources.joinToString { "${it.packageName}:${it.id}" }
        session.search(requestKey, selectedSources, query.trim(), latest && query.isBlank(), runner)
    }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val local = remember(comics, query) { comics.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) } }
    val installed = sourceState.installed.map { it.packageName }.toSet()
    val candidates = sourceState.availableExtensions.filter { it.entry.packageName !in installed }
    val matchingCandidates = candidates.filter { (listOf(it.entry.name) + it.entry.sourceNames).any { name -> name.contains(query.trim(), true) } }
    @Composable fun FocusButton(key: String, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
        val requester = remember { FocusRequester() }
        LaunchedEffect(Unit) { if (!restoredFocus && lastFocus == key) { requester.requestFocus(); restoredFocus = true } }
        TvButton(text, onClick, modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) lastFocus = key })
    }
    @Composable fun SearchCard(key: String, title: String, source: String, status: String, cover: Any?, hit: SearchHit? = null, onClick: () -> Unit) {
        val requester = remember { FocusRequester() }
        LaunchedEffect(Unit) { if (!restoredFocus && lastFocus == key) { requester.requestFocus(); restoredFocus = true } }
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth().focusRequester(requester)
            .onFocusChanged { if (it.isFocused) lastFocus = key }
            .then(libraryCardKeys(onClick, { optionsKey = key; optionsTitle = title })),
            colors = CardDefaults.colors(containerColor = TvColors.Panel, focusedContainerColor = TvColors.Panel),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusAccent))),
            scale = CardDefaults.scale(focusedScale = 1.03f)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(TvColors.Background), contentAlignment = Alignment.Center) {
                if (!TvColors.options.highContrast) {
                    if (hit != null) SearchSourceCover(hit, sources)
                    else AsyncImage(cover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                else Text("HQ", fontSize = 30.sp)
            }
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                Text(source, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                Text(status, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        }
    }
    SearchPageGrid(list, header = {
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Buscar", fontSize = 30.sp, modifier = Modifier.weight(1f))
            onBrowserOpen?.let { FocusButton("browser", "Navegador", it) }
            FocusButton("back", "Voltar", onBack)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchCategory.entries.forEach { tab -> FocusButton("tab:${tab.name}", if (category == tab) "● ${tab.label}" else tab.label, { categoryName = tab.name }) }
        }
        LaunchedEffect(Unit) { if (!restoredFocus && lastFocus == "query") { queryFocus.requestFocus(); restoredFocus = true } }
        Column(Modifier.fillMaxWidth().border(2.dp, TvColors.FocusAccent).padding(16.dp)) {
            Text("Título, editora, personagem ou gênero · OK abre o teclado", fontSize = 14.sp)
            TvTextInput(query, { query = it.take(300); browseRequested = false }, Modifier.fillMaxWidth().heightIn(min = 45.dp)
                .focusRequester(queryFocus).onFocusChanged { if (it.isFocused) lastFocus = "query" },
                textStyle = TextStyle(color = Color.White, fontSize = 25.sp), maxLength = 300)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Marvel", "DC", "Batman", "Superman", "Aventura", "Fantasia", "Terror", "Ficção científica").forEach { tag ->
                FocusButton("tag:$tag", tag, { query = tag; browseRequested = false })
            }
            FocusButton("clear", "Limpar", { query = ""; browseRequested = false })
        }
        Text("Tags consultam as fontes. Segure OK numa capa para classificar o tipo nesta sessão; sem classificação aparece em Todos.", fontSize = 13.sp)
      }
    }) {
            item("modes", span = { GridItemSpan(maxLineSpan) }) { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton("catalog", "Catálogo", { query = ""; latest = false; browseRequested = true })
                FocusButton("latest", "Novidades", { query = ""; latest = true; browseRequested = true })
                FocusButton("retry", "Buscar novamente", { retry++ })
            } }
            item("local-heading", span = { GridItemSpan(maxLineSpan) }) { Text("Na biblioteca · ${local.size}", fontSize = 22.sp) }
            items(local.filter { visible("local:${it.id}") }, key = { "local:${it.id}" }) { comic ->
                SearchCard("local:${comic.id}", comic.title, "Na biblioteca",
                    "${if (comic.id in offlineIds || !comic.id.startsWith("online-")) "Offline · " else ""}Página ${comic.currentPage + 1}/${comic.pageCount}",
                    localCover(comic.id), null, { onLocal(comic.id) })
            }
            if (local.isEmpty()) item("no-local", span = { GridItemSpan(maxLineSpan) }) { Text("Nenhum título correspondente na biblioteca.") }
            item("source-heading", span = { GridItemSpan(maxLineSpan) }) { Text("${if (query.isBlank()) if (latest) "Novidades" else "Catálogo" else "Nas fontes instaladas"} · ${batch.finished}/${batch.total} fontes${if (batch.running) " · Buscando…" else ""}", fontSize = 22.sp) }
            if (trustedSources.size > 20) item("source-groups", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fontes ${group * 20 + 1}–${(group * 20 + 20).coerceAtMost(trustedSources.size)} de ${trustedSources.size}. Cada grupo consulta até 20 fontes instaladas, incluindo as não selecionadas.", fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (group > 0) FocusButton("previous-group", "Grupo anterior", { sourceGroup = group - 1 })
                        if (group + 1 < groupCount) FocusButton("next-group", "Próximas fontes", { sourceGroup = group + 1 })
                    }
                }
            }
            items(batch.hits.filter { visible(it.identity) }, key = { it.identity }) { hit ->
                val linkedId = links[hit.identity]
                val possible = linkedId == null && comics.any { it.title.equals(hit.title.title, true) }
                SearchCard(hit.identity, hit.title.title, "${hit.source.name} · ${hit.source.language}",
                    if (linkedId != null) "Na biblioteca" else if (possible) "Possível correspondência na biblioteca" else "Disponível nesta fonte",
                    hit.title.coverUrl, hit, { if (linkedId != null) onLocal(linkedId) else onOnline(hit) })
            }
            if (!onlineRequested || (!batch.running && batch.hits.isEmpty())) item("empty", span = { GridItemSpan(maxLineSpan) }) { Text(if (!onlineRequested) "Digite uma consulta ou escolha Catálogo / Novidades para consultar as fontes instaladas." else if (trustedSources.isEmpty()) "Nenhuma fonte instalada e confiável disponível. Abra Extensões para configurar." else "Nenhum resultado nas primeiras páginas consultadas. Tente outra consulta ou fonte.") }
            if (batch.errors.isNotEmpty()) item("errors", span = { GridItemSpan(maxLineSpan) }) { Text("Resultados parciais.\n" + batch.errors.joinToString("\n"), fontSize = 14.sp) }
            if (batch.timedOut || batch.limited) item("limits", span = { GridItemSpan(maxLineSpan) }) { Text("${if (batch.timedOut) "Tempo total de busca esgotado. " else ""}Limite: 20 fontes, 12 títulos por fonte, primeira página. Refine a consulta ou busque na fonte desejada.", fontSize = 14.sp) }
            item("extra", span = { GridItemSpan(maxLineSpan) }) { extraResults(query) }
            item("extensions", span = { GridItemSpan(maxLineSpan) }) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Descobrir extensões", fontSize = 22.sp)
                Text("Índices de extensões não confirmam a existência de títulos. Uma fonte não instalada não pode ser consultada. Instalação e confiança são escolhas explícitas em Extensões.", fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusButton("extensions", "Abrir Extensões", onExtensions)
                    FocusButton("refresh-index", "Atualizar índices", sources::refreshExtensionCatalogs)
                }
                if (!sourceState.extensionCatalogLoaded) Text("Atualize os índices dos repositórios configurados para ver fontes disponíveis.")
                (matchingCandidates.ifEmpty { candidates }.take(12)).forEach { candidate ->
                    Text("${candidate.entry.name} · ${candidate.repository.name}\nFontes: ${candidate.entry.sourceNames.joinToString().ifBlank { "não informadas no índice" }} · Títulos não indexados", fontSize = 14.sp)
                }
            } }
    }
    optionsKey?.let { key ->
        LibraryDialog("Classificar: $optionsTitle", { optionsKey = null }) {
            Text("Tipo informado por você, válido nesta sessão. Não inferido da capa, título ou direção de leitura.", fontSize = 14.sp)
            SearchCategory.entries.forEach { type ->
                TvButton(if (type == SearchCategory.ALL) "Não classificado" else type.label, {
                    optionsKey = null; pendingClassification = key to type
                }, Modifier.fillMaxWidth())
            }
            TvButton("Cancelar", { optionsKey = null })
        }
    }
}

/** A single scroll owner: the address/filter header must leave the viewport with results. */
@Composable internal fun SearchPageGrid(
    state: LazyGridState,
    header: @Composable () -> Unit,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), state = state,
        modifier = Modifier.fillMaxSize().background(TvColors.Background),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(28.dp)) {
        item("search-header", span = { GridItemSpan(maxLineSpan) }) { header() }
        content()
    }
}

/** Fetch visible covers through the extension client (headers/cookies), never the generic image client. */
@Composable private fun SearchSourceCover(hit: SearchHit, sources: SourcesViewModel) {
    val directory = File(LocalContext.current.cacheDir, "search-covers")
    var failed by remember(hit.identity, hit.title.coverUrl) { mutableStateOf(false) }
    val file by produceState<File?>(null, hit.identity, hit.title.coverUrl) {
        failed = false
        try { value = sources.globalSearchCover(hit, directory); failed = value == null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    if (file != null) AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
        onError = { failed = true })
    if (file == null || failed) Text(if (failed) "Capa indisponível" else "Carregando capa…", fontSize = 13.sp)
}

package com.pelican.comictv.details

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import com.pelican.comictv.sources.RelatedComic
import com.pelican.comictv.sources.RelatedResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.library.*
import com.pelican.comictv.sources.OnlineEditionInfo
import com.pelican.comictv.ui.*
import kotlinx.coroutines.CancellationException

@Composable fun DetailsScreen(
    id: String,
    repository: LibraryRepository,
    onRead: () -> Unit,
    onBack: () -> Unit,
    loadOnlineInfo: suspend (String) -> OnlineEditionInfo? = { null },
    loadRelated: suspend (String) -> RelatedResult = { RelatedResult(emptyList(), "Relacionados indisponíveis.") },
    onRelated: (RelatedComic) -> Unit = {},
    showRelated: Boolean = false,
    offlineAvailable: Boolean = false, onDownload: (() -> Unit)? = null,
    onEditions: (() -> Unit)? = null,
    onDeleteOnline: (suspend (String) -> Boolean)? = null,
) {
    var comic by remember(id) { mutableStateOf<ComicEntity?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var onlineInfo by remember(id) { mutableStateOf<OnlineEditionInfo?>(null) }
    var metadataLoading by remember(id) { mutableStateOf(id.startsWith("online-")) }
    var expandedSummary by remember(id) { mutableStateOf(false) }
    val scrollScope = rememberCoroutineScope()
    val pageScroll = rememberLazyListState()
    val relatedFocus = remember { FocusRequester() }
    var related by remember(id) { mutableStateOf<RelatedResult?>(null) }
    var relatedBusy by remember(id) { mutableStateOf(false) }
    var relatedJob by remember(id) { mutableStateOf<Job?>(null) }
    fun discoverRelated() {
        if (!showRelated || relatedBusy || related != null) return
        relatedBusy = true
        relatedJob = scrollScope.launch {
            try { related = loadRelated(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { related = RelatedResult(emptyList(), "Não foi possível consultar os relacionados. Tente novamente.") }
            finally { relatedBusy = false }
        }
    }
    fun afterDiscoveryStopped(action: () -> Unit) {
        scrollScope.launch { relatedJob?.cancelAndJoin(); action() }
    }
    var confirmRemoval by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    var changingHistory by remember { mutableStateOf(false) }
    val summaryScroll = rememberLazyListState()
    var summaryFocused by remember { mutableStateOf(false) }
    val first = remember(id) { FocusRequester() }
    val isOnline = id.startsWith("online-")
    var moreOptions by remember(id) { mutableStateOf(false) }
    val moreFocus = remember(id) { FocusRequester() }
    val historyFocus = remember(id) { FocusRequester() }
    val deleteFocus = remember(id) { FocusRequester() }
    var restoreMore by remember { mutableIntStateOf(0) }
    var restoreOption by remember { mutableStateOf<Pair<Int, String>?>(null) }
    fun clearPendingFocus() { restoreMore = 0; restoreOption = null }
    fun closeMore() { restoreOption = null; moreOptions = false; restoreMore++ }
    LaunchedEffect(restoreMore, moreOptions) {
        if (restoreMore > 0 && !moreOptions) { withFrameNanos { }; moreFocus.requestFocus(); restoreMore = 0 }
    }
    LaunchedEffect(restoreOption, moreOptions, confirmRemoval, confirmDelete) {
        if (moreOptions && restoreOption != null && !confirmRemoval && !confirmDelete) {
            withFrameNanos { }
            if (restoreOption?.second == "delete") deleteFocus.requestFocus() else historyFocus.requestFocus()
            restoreOption = null
        }
    }
    LaunchedEffect(id) {
        try {
            comic = repository.getComic(id)
            if (comic == null) error = "Esta edição não está mais na biblioteca."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Não foi possível carregar a edição." }
    }
    // Optional stored metadata must never delay loading or focusing the reading action.
    LaunchedEffect(id, loadOnlineInfo) {
        if (isOnline) {
            metadataLoading = true
            try { onlineInfo = loadOnlineInfo(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { onlineInfo = null }
            finally { metadataLoading = false }
        }
    }
    LaunchedEffect(comic != null, error) {
        if (!moreOptions && !confirmRemoval && !confirmDelete && (comic != null || error != null)) first.requestFocus()
    }
    if (moreOptions) comic?.let { item ->
        val menuFirst = remember { FocusRequester() }
        LibraryDialog("Mais opções", ::closeMore) {
            Text(item.title, color = TvColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TvButton("Voltar aos detalhes", ::closeMore, Modifier.focusRequester(menuFirst))
            OrganizationActions(item, repository, showFavorite = false)
            com.pelican.comictv.cinema.CinemaArtworkActions(id, repository)
            if (isOnline && onDownload != null) {
                if (offlineAvailable) Text("✓ Disponível offline", color = TvColors.Amber)
                TvButton(if (offlineAvailable) "Gerenciar download offline" else "Baixar para ler offline", {
                    clearPendingFocus()
                    moreOptions = false
                    afterDiscoveryStopped(onDownload)
                })
            }
            if (item.lastRead > 0) TvButton(if (item.hiddenFromHistory) "Restaurar no histórico" else "Remover do histórico", {
                if (!item.hiddenFromHistory) confirmRemoval = true
                else if (!changingHistory) scrollScope.launch {
                    changingHistory = true
                    try { repository.setHistoryHidden(id, false); comic = repository.getComic(id) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Não foi possível alterar o histórico." }
                    finally { changingHistory = false }
                }
            }, Modifier.focusRequester(historyFocus))
            if (!isOnline || onDeleteOnline != null) TvButton("Excluir da biblioteca", { confirmDelete = true }, Modifier.focusRequester(deleteFocus))
            error?.let { Text(it, color = TvColors.Amber) }
        }
        LaunchedEffect(Unit) { menuFirst.requestFocus() }
    }
    if (confirmRemoval) ConfirmHistoryRemoval(comic?.title.orEmpty(),
        onDismiss = { confirmRemoval = false; restoreOption = ((restoreOption?.first ?: 0) + 1) to "history" },
        onConfirm = { repository.setHistoryHidden(id, true); comic = repository.getComic(id) })
    if (confirmDelete) ConfirmImportedRemoval(comic?.title.orEmpty(), { confirmDelete = false; restoreOption = ((restoreOption?.first ?: 0) + 1) to "delete" },
        { if (isOnline) requireNotNull(onDeleteOnline)(id) else repository.deleteImported(id) },
        { confirmDelete = false; onBack() }, online = isOnline)
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        BrandedBackground(repository.cover(id), Modifier.matchParentSize())
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        // The cover sets the shared height; both side panels remain independently scrollable.
        val coverWidth = minOf(maxHeight * (2f / 3f), maxWidth * .36f)
        val panelHeight = coverWidth * 1.5f
        LazyColumn(Modifier.fillMaxSize(), state = pageScroll, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "overview") {
        Row(Modifier.fillMaxWidth().height(panelHeight),
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (isOnline) "SUA BIBLIOTECA / EDIÇÃO ONLINE" else "SUA BIBLIOTECA / EDIÇÃO LOCAL",
                    fontSize = 13.sp, color = TvColors.Amber)
                Text(onlineInfo?.title?.takeIf { it.isNotBlank() } ?: comic?.title
                    ?: if (error != null) "Edição indisponível" else "Carregando edição…",
                    fontSize = 26.sp, lineHeight = 34.sp)
                onlineInfo?.let { info ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (info.chapter.isNotBlank()) Text(info.chapter, fontSize = 20.sp)
                        Text(listOf(info.sourceName, info.language).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 16.sp, color = TvColors.Muted)
                    }
                }
                comic?.let { item ->
                    val count = item.pageCount.coerceAtLeast(0)
                    val read = if (item.lastRead > 0) (item.currentPage + 1).coerceIn(0, count) else 0
                    val progress = if (count > 0) read.toFloat() / count else 0f
                    val status = when {
                        item.lastRead == 0L -> "Não iniciada"
                        count > 0 && read >= count -> "Concluída"
                        else -> "Em andamento"
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(status + " • " + (progress * 100).toInt() + "% lido", fontSize = 18.sp)
                        Text(if (read > 0) "Página " + read + " de " + count else count.toString() + " páginas",
                            color = TvColors.Muted, fontSize = 16.sp)
                        ProgressTrack(progress, Modifier.fillMaxWidth())
                    }
                }
                error?.let { Text(it, color = TvColors.Amber, fontSize = 16.sp) }
                comic?.let { item ->
                    TvButton(if (item.lastRead > 0) "Continuar leitura" else "Começar leitura",
                        { clearPendingFocus(); afterDiscoveryStopped(onRead) }, Modifier.focusRequester(first), primary = true)
                    if (isOnline && onEditions != null) {
                        TvButton("Capítulos / edições", { clearPendingFocus(); afterDiscoveryStopped(onEditions) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrganizationActions(item, repository, showCollections = false, compactFavorite = true)
                        TvButton("Mais opções", { clearPendingFocus(); moreOptions = true }, Modifier.focusRequester(moreFocus))
                    }
                    if (offlineAvailable) Text("✓ Disponível offline", color = TvColors.Amber, fontSize = 15.sp)
                }
                TvButton("Voltar à biblioteca", { clearPendingFocus(); onBack() },
                    if (comic == null) Modifier.focusRequester(first) else Modifier)
                if (showRelated) TvButton("Relacionados ↓", { scrollScope.launch {
                    pageScroll.animateScrollToItem(1)
                    relatedFocus.requestFocus()
                } })
            }
            AsyncImage(repository.cover(id), "Capa da edição",
                Modifier.width(coverWidth).fillMaxHeight(), contentScale = ContentScale.Fit)
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight().background(TvColors.Panel)
                    .border(if (summaryFocused) 2.dp else 0.dp, TvColors.Amber)
                    .onFocusChanged { summaryFocused = it.hasFocus }
                    .textPanelScroll(summaryScroll),
                state = summaryScroll, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sobre esta edição", fontSize = 20.sp, lineHeight = 28.sp)
                        val summary = onlineInfo?.description?.trim()?.takeIf { it.isNotEmpty() }
                        if (summary != null) TvButton(
                            if (expandedSummary) "Recolher resumo" else "Mostrar resumo completo",
                            { expandedSummary = !expandedSummary })
                        Text(summary ?: when {
                            metadataLoading -> "Carregando informações salvas…"
                            isOnline -> "Esta edição não tem sinopse salva."
                            else -> "Edição importada de um arquivo local. Nenhuma sinopse está disponível."
                        }, fontSize = 17.sp, lineHeight = 26.sp, color = TvColors.Muted,
                            maxLines = if (expandedSummary) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        }
        if (showRelated) {
        item(key = "related-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Relacionados", fontSize = 26.sp)
                TvButton(if (relatedBusy) "Buscando relações…" else if (related == null) "Buscar relacionados" else "Atualizar relacionados",
                    { if (!relatedBusy) { related = null; discoverRelated() } },
                    Modifier.focusRequester(relatedFocus).onFocusChanged { if (it.isFocused) discoverRelated() })
                Text(related?.message ?: "Escritores, desenhistas, personagens e editoras em comum, quando informados pela fonte.", color = TvColors.Muted, fontSize = 16.sp)
            }
        }
        item(key = "related-list") {
            RelatedComicsRow(related?.comics.orEmpty(), repository) { selected ->
                afterDiscoveryStopped { onRelated(selected) }
            }
        }
        }
        }
    }
    }
}

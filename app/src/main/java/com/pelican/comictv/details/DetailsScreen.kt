package com.pelican.comictv.details

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.pelican.comictv.sources.*
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
    onAboutWork: ((String) -> Unit)? = null,
    loadChapters: (suspend (String) -> OnlineEditions)? = null,
    readChapterOrder: suspend (OnlineEditions) -> ChapterOrder = { ChapterOrder.Ascending },
    writeChapterOrder: suspend (OnlineEditions, ChapterOrder) -> Unit = { _, _ -> },
    openChapter: (suspend (OnlineEditions, OnlineChapter) -> String)? = null,
    onReadChapter: (String) -> Unit = {},
    onDeleteOnline: (suspend (String) -> Boolean)? = null,
) {
    var comic by remember(id) { mutableStateOf<ComicEntity?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var onlineInfo by remember(id) { mutableStateOf<OnlineEditionInfo?>(null) }
    var metadataLoading by remember(id) { mutableStateOf(id.startsWith("online-")) }
    var expandedSummary by remember(id) { mutableStateOf(false) }
    val scrollScope = rememberCoroutineScope()
    val chapters = remember(id) { DetailsChapterController() }
    val allComics by repository.observeComics().collectAsStateWithLifecycle(emptyList())
    val sagaRevision by repository.sagaRevision.collectAsStateWithLifecycle()
    var owned by remember(id) { mutableStateOf<List<ComicEntity>>(emptyList()) }
    var interacted by remember(id) { mutableStateOf(false) }
    var chapterReturn by rememberSaveable(id, stateSaver = listSaver<ChapterFocusReturn, String>(
        save = { listOf(it.pendingId.orEmpty(), it.departed.toString(), it.restoreId.orEmpty()) },
        restore = { ChapterFocusReturn(it[0].ifEmpty { null }, it[1].toBoolean(), it[2].ifEmpty { null }) },
    )) { mutableStateOf(ChapterFocusReturn()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> chapterReturn = chapterReturn.paused()
                Lifecycle.Event.ON_RESUME -> chapterReturn = chapterReturn.resumed()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun navigateToReader(destination: String, action: () -> Unit) {
        interacted = true
        chapterReturn = chapterReturn.readerOpening(destination)
        try { action() }
        catch (e: Exception) { chapterReturn = ChapterFocusReturn(); throw e }
    }
    fun retryChapters() {
        val load = loadChapters ?: return
        chapters.run { val context = load(id); chapters.order = readChapterOrder(context); chapters.context = context }
    }
    LaunchedEffect(id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            coroutineScope {
                chapters.scope = this
                if (loadChapters != null) retryChapters()
                try { awaitCancellation() }
                finally { chapters.scope = null; withContext(NonCancellable) { chapters.stop() } }
            }
        }
    }
    LaunchedEffect(allComics) { allComics.firstOrNull { it.id == id }?.let { comic = it } }
    LaunchedEffect(id, allComics, sagaRevision) {
        try { owned = repository.sagaGroups(allComics).firstOrNull { group -> group.editions.any { it.id == id } }?.editions.orEmpty() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { owned = allComics.filter { it.id == id } }
    }
    val chapterRows = chapters.context?.let { detailChapters(it, chapters.order, allComics) }
        ?: owned.map { DetailChapter(it.id, it.title, progress = it) }
    val largeCover = rememberDetailsCover(repository, id)
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
        scrollScope.launch { chapters.stop(); relatedJob?.cancelAndJoin(); action() }
    }
    var confirmRemoval by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    var changingHistory by remember { mutableStateOf(false) }
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
    val overviewScroll = rememberScrollState()
    LaunchedEffect(comic != null, error) {
        if (!interacted && chapterReturn.restoreId == null && !expandedSummary && !moreOptions && !confirmRemoval && !confirmDelete && (comic != null || error != null)) {
            withFrameNanos { }
            first.requestFocus()
            pageScroll.scrollToItem(0)
            overviewScroll.scrollTo(0)
        }
    }
    BackHandler(enabled = !moreOptions && !confirmRemoval && !confirmDelete && !expandedSummary) { afterDiscoveryStopped(onBack) }
    val summaryFocus = remember(id) { FocusRequester() }
    if (expandedSummary) {
        FullSummaryDialog(onlineInfo?.description?.trim()?.takeIf { it.isNotEmpty() } ?: when {
            metadataLoading -> "Carregando informações salvas…"
            isOnline -> "Esta edição não tem sinopse salva."
            else -> "Edição importada de um arquivo local. Nenhuma sinopse está disponível."
        }) { expandedSummary = false; scrollScope.launch { withFrameNanos { }; summaryFocus.requestFocus() } }
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
        { confirmDelete = false; afterDiscoveryStopped(onBack) }, online = isOnline)
    Box(Modifier.fillMaxSize().background(TvColors.Background).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown) interacted = true
        false
    }) {
        BrandedBackground(repository.cover(id), Modifier.matchParentSize())
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        // The cover sets the shared height; both side panels remain independently scrollable.
        val coverWidth = minOf(maxHeight * (2f / 3f), maxWidth * .36f)
        val panelHeight = coverWidth * 1.5f
        LazyColumn(Modifier.fillMaxSize(), state = pageScroll, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(key = "overview") {
        Row(Modifier.fillMaxWidth().height(panelHeight),
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Returning from the chapter panel goes back to the last used action, never the nearest by height.
            Column(Modifier.weight(1f).fillMaxHeight().focusRestorer().focusGroup().verticalScroll(overviewScroll).padding(8.dp),
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
                        { clearPendingFocus(); afterDiscoveryStopped { navigateToReader(id, onRead) } }, Modifier.focusRequester(first), primary = true)
                    if (onAboutWork != null) TvButton("Sobre a obra · metadados e notas", { clearPendingFocus(); afterDiscoveryStopped { onAboutWork(item.title) } })
                    TvButton("Resumo completo", { interacted = true; chapterReturn = ChapterFocusReturn(); expandedSummary = true }, Modifier.focusRequester(summaryFocus))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrganizationActions(item, repository, showCollections = false, compactFavorite = true)
                        TvButton("Mais opções", { interacted = true; chapterReturn = ChapterFocusReturn(); clearPendingFocus(); moreOptions = true }, Modifier.focusRequester(moreFocus))
                    }
                    if (offlineAvailable) Text("✓ Disponível offline", color = TvColors.Amber, fontSize = 15.sp)
                }
                TvButton("Voltar à biblioteca", { clearPendingFocus(); afterDiscoveryStopped(onBack) },
                    if (comic == null) Modifier.focusRequester(first) else Modifier)
                if (showRelated) TvButton("Relacionados ↓", { scrollScope.launch {
                    pageScroll.animateScrollToItem(1)
                    relatedFocus.requestFocus()
                } })
            }
            Column(Modifier.width(coverWidth).fillMaxHeight()) {
                DetailCoverImage(largeCover, "Capa da edição", Modifier.weight(1f).fillMaxWidth())
                if (largeCover == repository.cover(id)) Text("Capa fornecida pela fonte / biblioteca · resolução disponível",
                    fontSize = 12.sp, color = TvColors.Muted)
            }
            DetailsChapterPanel(id, chapterRows, chapters, isOnline, interacted, chapterReturn.restoreId,
                onRestored = { chapterReturn = chapterReturn.consumed() }, onRetry = ::retryChapters,
                onOrder = { order -> chapters.context?.let { context ->
                    chapters.run { writeChapterOrder(context, order); chapters.order = order }
                } }, onRead = { row ->
                    interacted = true
                    val context = chapters.context
                    if (row.id == id) afterDiscoveryStopped { navigateToReader(id, onRead) }
                    else if (row.progress != null) afterDiscoveryStopped { navigateToReader(row.id) { onReadChapter(row.id) } }
                    else if (context != null && row.chapter != null && openChapter != null) {
                        chapters.run {
                            relatedJob?.cancelAndJoin()
                            val next = openChapter(context, row.chapter)
                            navigateToReader(next) { onReadChapter(next) }
                        }
                    }
                }, modifier = Modifier.weight(1f).fillMaxHeight())
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

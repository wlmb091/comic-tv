package com.pelican.comictv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable fun LibraryScreen(
    comics: List<ComicEntity>, repository: LibraryRepository, focusedId: String?,
    onFocus: (String) -> Unit, onOpen: (String) -> Unit, onImport: () -> Unit,
    onRead: (String) -> Unit = onOpen,
    requestInitialFocus: Boolean = false, onInitialFocus: () -> Unit = {},
    query: String = "", filter: String = "all", collections: List<CollectionEntity> = emptyList(),
    memberships: List<CollectionMembership> = emptyList(), onQuery: (String) -> Unit = {}, onFilter: (String) -> Unit = {},
    offlineIds: Set<String> = emptySet(), onDownload: ((String) -> Unit)? = null,
    onDeleteOnline: (suspend (String) -> Boolean)? = null,
    searchRequest: Int = 0,
    searchRequested: Boolean = false, onSearchHandled: () -> Unit = {},
) {
    val preset = TvColors.options.preset
    val cinema = preset == ColorPreset.Cinema
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val footerControls = preset in listOf(ColorPreset.Flix, ColorPreset.Prime, ColorPreset.Max, ColorPreset.Cinema)
    val topSearch = preset == ColorPreset.Flix || preset == ColorPreset.Prime
    val grid = rememberLazyGridState()
    val pageAtTop by remember(grid) { derivedStateOf { grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0 } }
    val filterList = rememberLazyListState()
    val recentList = rememberLazyListState()
    val firstControl = preferredContentEntryModifier(enabled = pageAtTop,
        prepare = { grid.scrollToItem(0); filterList.scrollToItem(0) })
    val enteringSection = LocalSectionContentEntry.current
    val sectionEntry = remember { enteringSection }
    val restore = remember { FocusRequester() }
    var history by rememberSaveable { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var targetRecent by remember { mutableStateOf(false) }
    var positioned by remember { mutableStateOf<String?>(null) }
    var featuredId by remember { mutableStateOf(focusedId) }
    fun focusComic(id: String) { featuredId = id; onFocus(id) }
    val featured = featuredComic(comics, featuredId)
    val recent = remember(comics) { comics.filter { it.lastRead > 0 && !it.hiddenFromHistory }.sortedByDescending { it.lastRead } }
    var optionsFor by remember { mutableStateOf<ComicEntity?>(null) }
    var optionsRecent by remember { mutableStateOf(false) }
    val menuScope = rememberCoroutineScope()
    val headerFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val manageFocus = remember { FocusRequester() }
    suspend fun focusHeaderControl() {
        if (topSearch) searchFocus.requestFocus() else headerFocus.requestFocus()
    }
    var searching by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var restoreControl by remember { mutableIntStateOf(0) }
    var restoreManage by remember { mutableIntStateOf(0) }
    var searchFromShell by remember { mutableStateOf(false) }
    var searchOriginIndex by remember { mutableIntStateOf(0) }
    var searchOriginOffset by remember { mutableIntStateOf(0) }
    var restored by remember { mutableStateOf(false) }
    val shellSearchAvailable = LocalShellLibrarySearch.current != null
    val searchEntries = remember(comics) { comics.map { LibrarySearchEntry(it.id, it.title, it.favorite) } }
    val memberIndex = remember(memberships) { memberships.groupBy { it.collectionId }.mapValues { (_, rows) -> rows.map { it.comicId }.toSet() } }
    val matching = remember(searchEntries, query, filter, memberIndex) { filterLibrary(searchEntries, query, filter, memberIndex).toSet() }
    val visible = remember(history, recent, comics, matching) { (if (history) recent else comics).filter { it.id in matching } }
    val headerItems = libraryGridHeaderItems(history, recent.size)
    suspend fun revealHeader() {
        grid.scrollToItem(0)
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "library-header" } }.first { it }
        withFrameNanos { }
    }
    suspend fun revealFooter() {
        grid.scrollToItem(libraryGridFooterIndex(history, recent.size, visible.size))
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "library-footer" } }.first { it }
        withFrameNanos { }
    }
    fun openSearch(fromShell: Boolean = false) {
        if (fromShell) restored = true // A late library emission must not restore focus over this dialog.
        searchFromShell = fromShell
        searchOriginIndex = grid.firstVisibleItemIndex
        searchOriginOffset = grid.firstVisibleItemScrollOffset
        searching = true
    }
    suspend fun restoreComic(id: String, fromRecent: Boolean) {
        val index = (if (fromRecent) recent else visible).indexOfFirst { it.id == id }
        if (index < 0 || (fromRecent && history)) {
            revealHeader()
            focusHeaderControl()
            return
        }
        targetRecent = fromRecent
        positioned = null
        target = id
        val key = if (fromRecent) "recent-row" else id
        if (grid.layoutInfo.visibleItemsInfo.none { it.key == key }) {
            grid.scrollToItem(if (fromRecent) 2 else headerItems + index)
        }
        if (fromRecent && recentList.layoutInfo.visibleItemsInfo.none { it.key == id }) recentList.scrollToItem(index)
        snapshotFlow { positioned == id }.first { it }
        restore.requestFocus()
        target = null
    }
    // Only new shell events open the dialog; returning from details must not replay an old event.
    var handledSearchRequest by remember { mutableIntStateOf(searchRequest) }
    LaunchedEffect(searchRequest) {
        if (searchRequest != handledSearchRequest) {
            handledSearchRequest = searchRequest
            openSearch(fromShell = true)
        }
    }
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            openSearch(fromShell = true)
            onSearchHandled()
        }
    }
    if (searching) LibrarySearchDialog(query, onQuery,
        results = (if (history) recent else comics).filter { it.id in matching }.map { LibrarySearchEntry(it.id, it.title, it.favorite) }) {
        searching = false; restoreControl++
    }
    if (managing) ManageCollections(repository) {
        managing = false
        menuScope.launch {
            if (footerControls) {
                revealFooter()
                filterList.scrollToItem(collections.size + 2)
                snapshotFlow { filterList.layoutInfo.visibleItemsInfo.any { it.key == "manage" } }.first { it }
                manageFocus.requestFocus()
            } else {
                revealHeader()
                restoreManage++
            }
        }
    }
    LaunchedEffect(restoreControl) {
        if (restoreControl > 0) {
            if (footerControls && !topSearch && !searchFromShell) {
                revealFooter()
                searchFocus.requestFocus()
            } else {
                grid.scrollToItem(searchOriginIndex.coerceAtMost(libraryGridFooterIndex(history, recent.size, visible.size)), searchOriginOffset)
                withFrameNanos { }
                if (!searchFromShell) searchFocus.requestFocus()
                else {
                    val recentOrigin = !history && grid.layoutInfo.visibleItemsInfo.any { it.key == "recent-row" } &&
                        recentList.layoutInfo.visibleItemsInfo.any { it.key == featuredId }
                    val gridOrigin = grid.layoutInfo.visibleItemsInfo.firstOrNull { row -> visible.any { it.id == row.key } }?.key as? String
                    when {
                        recentOrigin && featuredId != null -> restoreComic(featuredId!!, true)
                        gridOrigin != null -> restoreComic(featuredId?.takeIf { id -> grid.layoutInfo.visibleItemsInfo.any { it.key == id } } ?: gridOrigin, false)
                        else -> { revealHeader(); focusHeaderControl() }
                    }
                }
            }
        }
    }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            revealHeader()
            focusHeaderControl()
            onInitialFocus()
        }
    }
    optionsFor?.let { selected ->
        HistoryOptions(selected, repository, onRead = { optionsFor = null; focusComic(selected.id); onRead(selected.id) }, onOpen = { optionsFor = null; focusComic(selected.id); onOpen(selected.id) },
            onDeleteOnline = onDeleteOnline,
            onDownload = onDownload?.let { callback -> {optionsFor = null;focusComic(selected.id);callback(selected.id)} },offlineAvailable = selected.id in offlineIds,
            onDismiss = { menuScope.launch {
                optionsFor = null
                withFrameNanos { }
                restoreComic(selected.id, optionsRecent)
            } })
    }
    val warmEntries = remember(visible, repository) { visible.map {CoverWarmEntry(it.id,repository.cover(it.id))} }
    WarmGridCovers(grid, warmEntries)
    // Capture the entry target once: database emissions must not take focus from navigation.
    val entryId = remember { focusedId }
    LaunchedEffect(comics, history, query, filter, memberships, searching, searchRequested) {
        if (sectionEntry || restored || entryId == null || searching || searchRequested) return@LaunchedEffect
        if (comics.isEmpty()) return@LaunchedEffect
        val index = visible.indexOfFirst { it.id == entryId }
        if (index < 0) {
            grid.scrollToItem(0)
            withFrameNanos { }
            focusHeaderControl()
            restored = true
            return@LaunchedEffect
        }
        target = entryId
        targetRecent = false
        positioned = null
        grid.scrollToItem(headerItems + index)
        snapshotFlow { positioned == entryId }.first { it }
        restore.requestFocus()
        target = null
        restored = true
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val streamingHeroHeight = when (preset) {
        ColorPreset.Cinema -> maxHeight - 32.dp
        ColorPreset.Prime -> maxHeight * .70f
        else -> maxHeight * .63f
    }
    // Flix/Max are hosted by TvShell so the image continues behind their navigation.
    if (cinema || preset == ColorPreset.Prime) CinemaBackdrop(featured?.id, repository, Modifier.matchParentSize())
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp), state = grid,
        modifier = Modifier.fillMaxSize().focusRestorer().testTag("library-grid"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (footerControls) 8.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "library-header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(if (footerControls) 10.dp else 18.dp)) {
                if (footerControls) {
                    if (preset == ColorPreset.Max) ComicWordmark(Modifier.width(180.dp * textScale).heightIn(min = 38.dp).testTag("library-brand"))
                    if (topSearch) FlowRow(Modifier.fillMaxWidth().testTag("library-top-search"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                        TvChoice("Buscar", searching || query.isNotBlank(), { openSearch() },
                            Modifier.focusRequester(searchFocus).then(firstControl), compact = true, transparentBackground = true)
                    }
                    LibraryHero(featured, repository,
                        onRead = { focusComic(it); onRead(it) }, onOpen = { focusComic(it); onOpen(it) }, onImport = onImport,
                        modifier = Modifier.heightIn(min = streamingHeroHeight),
                        readModifier = if (topSearch) Modifier else Modifier.focusRequester(headerFocus).then(firstControl),
                        comics = comics, onSelect = ::focusComic)
                } else {
                    LibraryHeader(comics.size, recent.size, history, searching || query.isNotBlank(),
                        { openSearch() }, { history = false }, { history = true },
                        Modifier.focusRequester(headerFocus), Modifier.focusRequester(searchFocus))
                    LibraryFilters(collections, filter, onFilter, { managing = true }, restoreManage,
                        returnFocusEnabled = pageAtTop, entryModifier = firstControl, listState = filterList)
                    if (!history) LibraryHero(featured, repository,
                        onRead = { focusComic(it); onRead(it) }, onOpen = { focusComic(it); onOpen(it) }, onImport = onImport)
                }
            }
        }
        if (!history && recent.isNotEmpty()) {
            item(key = "recent-heading", span = { GridItemSpan(maxLineSpan) }) {
                Text("Continuar lendo", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            item(key = "recent-row", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    state = recentList,
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(recent, key = { it.id }) { comic ->
                        val restoreModifier = if (targetRecent && target == comic.id) Modifier.focusRequester(restore)
                            .onGloballyPositioned { positioned = comic.id } else Modifier
                        Card(
                            onClick = { focusComic(comic.id); onOpen(comic.id) },
                            modifier = restoreModifier.then(libraryCardKeys({ focusComic(comic.id); onOpen(comic.id) }, { optionsRecent = true; optionsFor = comic })).width(288.dp).onFocusChanged { if (it.isFocused) focusComic(comic.id) },
                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber))),
                            shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                            scale = CardDefaults.scale(focusedScale = 1.025f),
                            colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                        ) {
                            Row(Modifier.fillMaxWidth().glassPanel().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(repository.cover(comic.id), null, Modifier.width(44.dp).height(64.dp), contentScale = ContentScale.Fit)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(comic.title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                                        lineHeight = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                    Text(readingStatus(comic), color = TvColors.Muted, fontSize = 13.sp)
                                    ProgressTrack(readingFraction(comic), Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "collection-heading", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = when { filter == "favorites" -> "Favoritos"; filter.startsWith("collection:") -> collections.firstOrNull { "collection:" + it.id == filter }?.name ?: "Coleção"; else -> if (history) "Histórico" else "Sua coleção" }
                Text(label + " · " + visible.size + (if (query.isNotBlank()) " · Busca: “${query.trim()}”" else ""),
                    fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                if (query.isNotBlank()) TvButton("Limpar busca", { onQuery("") })
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val filtered = query.isNotBlank() || filter != "all"
                    Text(if (filtered) "Nenhum quadrinho encontrado" else if (history) "Nenhuma leitura no histórico" else "Sua coleção começa aqui", fontSize = 24.sp)
                    Text(
                        if (filtered) "Tente outro título ou selecione Todas para mudar o filtro."
                        else if (history) "Abra uma edição para encontrá-la aqui, inclusive depois de terminar."
                        else "Importe CBZ, ZIP, CBR, RAR, PDF ou EPUB para começar.",
                        color = TvColors.Muted, fontSize = 17.sp,
                    )
                    if (history) TvButton("Ver biblioteca", { history = false })
                    if (filtered) TvButton("Mostrar todas", { onQuery(""); onFilter("all") })

                }
            }
        }
        items(visible, key = { it.id }) { comic ->
            val restoreModifier = if (!targetRecent && target == comic.id) Modifier.focusRequester(restore)
                .onGloballyPositioned { positioned = comic.id } else Modifier
            Card(
                onClick = { focusComic(comic.id); onOpen(comic.id) },
                modifier = restoreModifier.then(libraryCardKeys({ focusComic(comic.id); onOpen(comic.id) }, { optionsRecent = false; optionsFor = comic })).testTag("library-cover-${comic.id}").onFocusChanged { if (it.isFocused) focusComic(comic.id) },
                scale = CardDefaults.scale(focusedScale = 1.03f),
                shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber))),
                colors = CardDefaults.colors(containerColor = TvColors.Panel),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(TvColors.Panel)) {
                    AsyncImage(repository.cover(comic.id), comic.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (comic.id in offlineIds) Text("OFFLINE",Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = .85f)).padding(5.dp),fontSize = 11.sp,color = TvColors.Amber)
                    if (comic.favorite) Text("★", Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .8f)).padding(6.dp)
                        .semantics { contentDescription = "Favorito" }, color = TvColors.Amber, fontSize = 19.sp)
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f), Color.Black)))
                        .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(comic.title, minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp, lineHeight = 20.sp)
                        if (comic.lastRead > 0) ProgressTrack(readingFraction(comic), Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (footerControls) {
            item(key = "library-footer", span = { GridItemSpan(maxLineSpan) }) {
                LibraryFooter(collections, filter, onFilter, { managing = true },
                    comics.size, recent.size, history, searching || query.isNotBlank(),
                    { openSearch() }, { history = false }, { history = true },
                    showSearch = preset == ColorPreset.Max && !shellSearchAvailable,
                    searchModifier = Modifier.focusRequester(searchFocus), manageModifier = Modifier.focusRequester(manageFocus),
                    filterListState = filterList)
            }
        }
    }
    }
}

/** Header + collection heading; the optional recent heading/row precede every cover. */
internal fun libraryGridHeaderItems(history: Boolean, recentCount: Int): Int =
    if (!history && recentCount > 0) 4 else 2

internal fun libraryGridFooterIndex(history: Boolean, recentCount: Int, visibleCount: Int): Int =
    libraryGridHeaderItems(history, recentCount) + visibleCount.coerceAtLeast(1)

private fun readingFraction(comic: ComicEntity): Float =
    (comic.currentPage + 1f) / comic.pageCount.coerceAtLeast(1)

private fun readingStatus(comic: ComicEntity): String =
    if (comic.pageCount > 0 && comic.currentPage >= comic.pageCount - 1) "Concluído"
    else "Página ${comic.currentPage + 1} de ${comic.pageCount}"

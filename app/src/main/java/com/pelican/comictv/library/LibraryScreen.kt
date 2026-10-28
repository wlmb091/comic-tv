package com.pelican.comictv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
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
    val theme = LocalThemeSpec.current
    val cinema = theme.navigation.layout == ThemeNavigationPolicy.ImmersiveMenu
    val horizontalCards = theme.cards.collectionStyle == CollectionCardStyle.Horizontal
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val footerControls = theme.footerControls
    val topSearch = theme.topSearch
    val grid = rememberLazyGridState()
    val pageAtTop by remember(grid) { derivedStateOf { grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0 } }
    val filterList = rememberLazyListState()
    val recentList = rememberLazyListState()
    // Hoisted above the lazy item so its scroll survives lazy disposal and Details navigation.
    val explorerList = rememberLazyListState()
    var explorerOriginKey by rememberSaveable { mutableStateOf<String?>(null) }
    var explorerRestoreKey by remember { mutableStateOf<String?>(null) }
    var explorerRestoreRequest by remember { mutableIntStateOf(0) }
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
    fun clearExplorerOrigin() { explorerOriginKey = null; explorerRestoreKey = null }
    fun focusComic(id: String) { clearExplorerOrigin(); featuredId = id; onFocus(id) }
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
    var searchExplorerOrigin by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    val shellSearchAvailable = LocalShellLibrarySearch.current != null
    val searchEntries = remember(comics) { comics.map { LibrarySearchEntry(it.id, it.title, it.favorite) } }
    val memberIndex = remember(memberships) { memberships.groupBy { it.collectionId }.mapValues { (_, rows) -> rows.map { it.comicId }.toSet() } }
    val matching = remember(searchEntries, query, filter, memberIndex) { filterLibrary(searchEntries, query, filter, memberIndex).toSet() }
    val visible = remember(history, recent, comics, matching) { (if (history) recent else comics).filter { it.id in matching } }
    val showExplorer = theme.exploreCollections && comics.isNotEmpty() && !history
    val headerItems = libraryGridHeaderItems(history, recent.size, showExplorer)
    val explorerEntries = remember(theme.exploreCollections, comics, collections, memberships) {
        if (theme.exploreCollections) maxExploreEntries(comics, collections, memberships) else emptyList()
    }
    suspend fun revealHeader() {
        grid.scrollToItem(0)
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "library-header" } }.first { it }
        withFrameNanos { }
    }
    suspend fun revealFooter() {
        grid.scrollToItem(libraryGridFooterIndex(history, recent.size, visible.size, showExplorer))
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "library-footer" } }.first { it }
        withFrameNanos { }
    }
    suspend fun restoreExplorer(key: String) {
        if (!showExplorer || explorerEntries.none { it.key == key }) {
            clearExplorerOrigin()
            revealHeader()
            focusHeaderControl()
            return
        }
        // Reveal only on an explicit return. Emissions never create new restore requests.
        if (grid.layoutInfo.visibleItemsInfo.none { it.key == "max-explore" }) grid.scrollToItem(1)
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "max-explore" } }.first { it }
        explorerOriginKey = key
        explorerRestoreKey = key
        explorerRestoreRequest++
    }
    fun openSearch(fromShell: Boolean = false) {
        if (fromShell) restored = true // A late library emission must not restore focus over this dialog.
        searchFromShell = fromShell
        searchExplorerOrigin = explorerOriginKey
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
            grid.scrollToItem(if (fromRecent) libraryGridRecentRowIndex(showExplorer) else headerItems + index)
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
                grid.scrollToItem(searchOriginIndex.coerceAtMost(libraryGridFooterIndex(history, recent.size, visible.size, showExplorer)), searchOriginOffset)
                withFrameNanos { }
                if (!searchFromShell) searchFocus.requestFocus()
                else {
                    val recentOrigin = !history && grid.layoutInfo.visibleItemsInfo.any { it.key == "recent-row" } &&
                        recentList.layoutInfo.visibleItemsInfo.any { it.key == featuredId }
                    val gridOrigin = grid.layoutInfo.visibleItemsInfo.firstOrNull { row -> visible.any { it.id == row.key } }?.key as? String
                    when {
                        searchExplorerOrigin != null -> restoreExplorer(searchExplorerOrigin!!)
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
    val entryExplorerKey = remember { explorerOriginKey }
    LaunchedEffect(comics, history, query, filter, memberships, searching, searchRequested) {
        if (sectionEntry || restored || searching || searchRequested) return@LaunchedEffect
        if (comics.isEmpty()) return@LaunchedEffect
        if (entryExplorerKey != null) {
            restored = true
            if (explorerOriginKey == entryExplorerKey) restoreExplorer(entryExplorerKey)
            return@LaunchedEffect
        }
        if (entryId == null) return@LaunchedEffect
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
    val streamingHeroHeight = when (theme.hero.heightPolicy) {
        HeroHeightPolicy.ViewportInset -> maxHeight - theme.hero.viewportInsetDp.dp
        HeroHeightPolicy.ReservedReadingRow -> (maxHeight - theme.hero.viewportInsetDp.dp)
            .coerceAtLeast(theme.hero.minimumHeightDp.dp)
        HeroHeightPolicy.Fraction -> maxHeight * theme.hero.viewportFraction
    }
    // Flix/Prime/Max share TvShell's full-viewport artwork, including navigation.
    if (cinema) CinemaBackdrop(featured?.id, repository, Modifier.matchParentSize())
    LazyVerticalGrid(
        columns = GridCells.Adaptive(theme.cards.gridMinimumWidthDp.dp * (if (theme.cards.scaleGridWithText) textScale else 1f)), state = grid,
        modifier = Modifier.fillMaxSize().focusRestorer().testTag("library-grid"),
        contentPadding = PaddingValues(horizontal = theme.cards.gridHorizontalPaddingDp.dp,
            vertical = (theme.cards.gridVerticalPaddingDp ?: if (footerControls) 8 else 16).dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "library-header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(if (footerControls) 10.dp else 18.dp)) {
                if (footerControls) {
                    if (theme.navigation.libraryBrand) ComicWordmark(Modifier.width(180.dp * textScale).heightIn(min = 38.dp).testTag("library-brand"))
                    if (topSearch) FlowRow(Modifier.fillMaxWidth().testTag("library-top-search"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                        TvChoice("Buscar", searching || query.isNotBlank(), { openSearch() },
                            Modifier.focusRequester(searchFocus).then(firstControl), compact = true, transparentBackground = true)
                    }
                    LibraryHero(featured, repository,
                        onRead = { focusComic(it); onRead(it) }, onOpen = { focusComic(it); onOpen(it) }, onImport = onImport,
                        modifier = Modifier.heightIn(min = streamingHeroHeight),
                        readModifier = if (topSearch) Modifier else Modifier.focusRequester(headerFocus).then(firstControl)
                            .onFocusChanged { if (it.isFocused) clearExplorerOrigin() },
                        comics = comics, onSelect = ::focusComic, onActionFocused = ::clearExplorerOrigin)
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
        if (showExplorer) {
            item(key = "max-explore", span = { GridItemSpan(maxLineSpan) }) {
                MaxExploreRow(comics, collections, memberships, repository, filter, onFilter,
                    onOpen = { featuredId = it; onFocus(it); onOpen(it) }, state = explorerList,
                    onItemFocused = { key, comicId ->
                        explorerOriginKey = key
                        comicId?.let { featuredId = it; onFocus(it) }
                    }, restoreKey = explorerRestoreKey, restoreRequest = explorerRestoreRequest,
                    onRestoreHandled = { explorerRestoreKey = null })
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
                            modifier = restoreModifier.then(libraryCardKeys({ focusComic(comic.id); onOpen(comic.id) }, { optionsRecent = true; optionsFor = comic })).width(theme.cards.recentWidthDp.dp).testTag("library-recent-${comic.id}").onFocusChanged { if (it.isFocused) focusComic(comic.id) },
                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                            shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(theme.shapes.controlRadiusDp.dp)),
                            scale = CardDefaults.scale(focusedScale = theme.focus.recentScale),
                            colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                        ) {
                            RecentCardContent(comic, repository)
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
                scale = CardDefaults.scale(focusedScale = theme.focus.collectionScale),
                shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(theme.shapes.controlRadiusDp.dp)),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                colors = if (horizontalCards) CardDefaults.colors(
                    containerColor = Color.Transparent, focusedContainerColor = Color.Transparent)
                    else CardDefaults.colors(containerColor = TvColors.Panel),
            ) {
                CollectionCardContent(comic, repository, comic.id in offlineIds)
            }
        }
        if (footerControls) {
            item(key = "library-footer", span = { GridItemSpan(maxLineSpan) }) {
                LibraryFooter(collections, filter, onFilter, { managing = true },
                    comics.size, recent.size, history, searching || query.isNotBlank(),
                    { openSearch() }, { history = false }, { history = true },
                    showSearch = theme.navigation.layout == ThemeNavigationPolicy.BottomDock && !shellSearchAvailable,
                    searchModifier = Modifier.focusRequester(searchFocus), manageModifier = Modifier.focusRequester(manageFocus),
                    filterListState = filterList)
            }
        }
    }
    }
}

internal fun libraryGridRecentRowIndex(explorer: Boolean = false): Int = if (explorer) 3 else 2

/** Header + collection heading; the optional recent heading/row precede every cover. */
internal fun libraryGridHeaderItems(history: Boolean, recentCount: Int, explorer: Boolean = false): Int =
    (if (!history && recentCount > 0) 4 else 2) + if (explorer) 1 else 0

internal fun libraryGridFooterIndex(history: Boolean, recentCount: Int, visibleCount: Int, explorer: Boolean = false): Int =
    libraryGridHeaderItems(history, recentCount, explorer) + visibleCount.coerceAtLeast(1)

internal fun readingFraction(comic: ComicEntity): Float =
    (comic.currentPage + 1f) / comic.pageCount.coerceAtLeast(1)

internal fun readingStatus(comic: ComicEntity): String =
    if (comic.pageCount > 0 && comic.currentPage >= comic.pageCount - 1) "Concluído"
    else "Página ${comic.currentPage + 1} de ${comic.pageCount}"

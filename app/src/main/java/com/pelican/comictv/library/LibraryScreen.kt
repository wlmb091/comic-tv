package com.pelican.comictv.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.pelican.comictv.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    homeSources: com.pelican.comictv.sources.HomeSourcePreviews = com.pelican.comictv.sources.HomeSourcePreviews(),
    featuredIdOverride: String? = null,
    onHeroInteractionChanged: (Boolean) -> Unit = {},
    onHeroVisibleChanged: (Boolean) -> Unit = {},
    onSourceSection: (Boolean) -> Unit = {},
    onSourceTitle: (com.pelican.comictv.sources.OnlineTitle, Boolean) -> Unit = { _, _ -> },
) {
    val theme = LocalThemeSpec.current
    val cinema = theme.navigation.layout == ThemeNavigationPolicy.ImmersiveMenu
    val horizontalCards = theme.cards.collectionStyle == CollectionCardStyle.Horizontal
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val footerControls = theme.footerControls
    val topSearch = theme.topSearch
    val portrait = LocalAppOrientation.current.portrait
    val maxLayout = theme.navigation.layout == ThemeNavigationPolicy.BottomDock
    var activeCollectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var fullLibrary by rememberSaveable { mutableStateOf(false) }
    val streaming = theme.artwork
    val collectionList = rememberLazyListState()
    val catalogList = rememberLazyListState()
    val latestList = rememberLazyListState()
    var homeOriginRow by rememberSaveable { mutableStateOf<String?>(null) }
    var homeOriginItem by rememberSaveable { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    var fullPageOrigin by rememberSaveable { mutableStateOf<String?>(null) }
    var fullPageFocusRequest by remember { mutableIntStateOf(0) }
    var homeReturnRequest by remember { mutableIntStateOf(0) }
    var sourceRestoreRow by remember { mutableStateOf<String?>(null) }
    var sourceRestoreItem by remember { mutableStateOf<String?>(null) }
    val collectionGrid = rememberLazyGridState()
    var collectionFocusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var collectionFocusRequest by remember { mutableIntStateOf(0) }
    val navigateWithinContent = LocalContentNavigation.current
    val grid = rememberLazyGridState()
    val pageAtTop by remember(grid) { derivedStateOf { grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0 } }
    val heroVisible by remember(grid) { derivedStateOf {
        val layout = grid.layoutInfo
        layout.visibleItemsInfo.any { it.key == "library-header" &&
            homeHeroVisible(it.offset.y, it.size.height, layout.viewportStartOffset, layout.viewportEndOffset) }
    } }
    val filterList = rememberLazyListState()
    val recentList = rememberLazyListState()
    // Hoisted above the lazy item so its scroll survives lazy disposal and Details navigation.
    val explorerList = rememberLazyListState()
    var explorerOriginKey by rememberSaveable { mutableStateOf<String?>(null) }
    var explorerRestoreKey by remember { mutableStateOf<String?>(null) }
    var explorerRestoreRequest by remember { mutableIntStateOf(0) }
    val firstControl = if (activeCollectionId == null) preferredContentEntryModifier(enabled = pageAtTop,
        prepare = { grid.scrollToItem(0); filterList.scrollToItem(0) }) else Modifier
    val enteringSection = LocalSectionContentEntry.current
    val sectionEntry = remember { enteringSection }
    val restore = remember { FocusRequester() }
    var history by rememberSaveable { mutableStateOf(false) }
    val homeRows = streaming && !history && !fullLibrary
    val comicHome = TvColors.options.preset == ColorPreset.Amber && !history && !fullLibrary && query.isBlank() && filter == "all"
    val previewHome = homeRows || comicHome
    val homeSourceRows = previewHome
    var target by remember { mutableStateOf<String?>(null) }
    var targetRecent by remember { mutableStateOf(false) }
    var positioned by remember { mutableStateOf<String?>(null) }
    var featuredId by remember { mutableStateOf(focusedId) }
    fun clearExplorerOrigin() { explorerOriginKey = null; explorerRestoreKey = null }
    fun focusComic(id: String) { restored = true; clearExplorerOrigin(); homeOriginRow = null; homeOriginItem = null; featuredId = id; onFocus(id) }
    fun focusHomeComic(id: String, row: String) { focusComic(id); homeOriginRow = row; homeOriginItem = id }
    val featured = featuredComic(comics, featuredIdOverride ?: featuredId)
    val recent = remember(comics) { comics.filter { it.lastRead > 0 && !it.hiddenFromHistory }.sortedByDescending { it.lastRead } }
    var optionsFor by remember { mutableStateOf<ComicEntity?>(null) }
    var optionsRecent by remember { mutableStateOf(false) }
    var optionsCollection by remember { mutableStateOf(false) }
    val menuScope = rememberCoroutineScope()
    val headerFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val emptyResultsFocus = remember { FocusRequester() }
    val manageFocus = remember { FocusRequester() }
    suspend fun focusHeaderControl() {
        if (topSearch) searchFocus.requestFocus() else headerFocus.requestFocus()
    }
    var searching by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var restoreControl by remember { mutableIntStateOf(0) }
    var restoreManage by remember { mutableIntStateOf(0) }
    var searchFromShell by remember { mutableStateOf(false) }
    var revealSearchResults by remember { mutableStateOf(false) }
    var searchOriginIndex by remember { mutableIntStateOf(0) }
    var searchOriginOffset by remember { mutableIntStateOf(0) }
    var searchExplorerOrigin by remember { mutableStateOf<String?>(null) }
    val shellSearchAvailable = LocalShellLibrarySearch.current != null
    val searchEntries = remember(comics) { comics.map { LibrarySearchEntry(it.id, it.title, it.favorite) } }
    val memberIndex = remember(memberships) { memberships.groupBy { it.collectionId }.mapValues { (_, rows) -> rows.map { it.comicId }.toSet() } }
    val matching = remember(searchEntries, query, filter, memberIndex) { filterLibrary(searchEntries, query, filter, memberIndex).toSet() }
    val sagaRevision by repository.sagaRevision.collectAsState()
    var sagaGroups by remember { mutableStateOf<List<SagaGroup>>(emptyList()) }
    var sagasLoaded by remember { mutableStateOf(false) }
    var sagaError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(comics, sagaRevision, collections) {
        try { sagaGroups = repository.sagaGroups(comics); sagaError = null; sagasLoaded = true }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { sagaError = "Não foi possível carregar as sagas. As edições continuam disponíveis."; sagasLoaded = true }
    }
    var activeSagaKey by rememberSaveable { mutableStateOf<String?>(null) }
    val sagaEditionsGrid = rememberLazyGridState()
    var sagaEditionFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var sagaOriginId by rememberSaveable { mutableStateOf<String?>(null) }
    val sagaByRepresentative = remember(sagaGroups) { sagaGroups.associateBy { it.representative.id } }
    val visible = remember(history, recent, comics, matching, sagaGroups) {
        if (history) recent.filter { it.id in matching }
        else if (sagaGroups.isEmpty()) comics.filter { it.id in matching }
        else sagaGroups.filter { group -> group.editions.any { it.id in matching } }.map { if (it.key.startsWith("edition:")) it.representative else it.display }
    }
    fun collectionCover(id: String): Any? = if (history || id !in sagaByRepresentative) repository.cover(id)
        else sagaByRepresentative.getValue(id).cover
    fun isSaga(id: String): Boolean = !history && sagaByRepresentative[id]?.key?.startsWith("edition:") == false
    val latestVisible by rememberUpdatedState(visible)
    fun openCollectionComic(id: String) {
        val saga = if (history) null else sagaByRepresentative[id]
        if (saga != null && !saga.key.startsWith("edition:")) {
            sagaOriginId = id; sagaEditionFocus = null; activeSagaKey = saga.key
        } else onOpen(id)
    }

    val showExplorer = theme.exploreCollections && (comics.isNotEmpty() || collections.isNotEmpty()) && !history
    val collectionVisible = homeRows || !maxLayout || history || fullLibrary
    val headerItems = libraryGridHeaderItems(history, recent.size, showExplorer, collectionVisible)
    val footerIndex = if (previewHome) homeGridFooterIndex(recent.size, showExplorer)
        else libraryGridFooterIndex(history, recent.size, visible.size, showExplorer, collectionVisible, homeSourceRows)
    val explorerEntries = remember(theme.exploreCollections, comics, collections, memberships) {
        if (theme.exploreCollections) maxExploreEntries(comics, collections, memberships) else emptyList()
    }
    suspend fun revealHeader() {
        grid.scrollToItem(0)
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "library-header" } }.first { it }
        withFrameNanos { }
    }
    suspend fun revealFooter() {
        grid.scrollToItem(footerIndex)
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
        if (grid.layoutInfo.visibleItemsInfo.none { it.key == "max-explore" }) grid.scrollToItem(homeExplorerRowIndex(recent.size))
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "max-explore" } }.first { it }
        explorerOriginKey = key
        explorerRestoreKey = key
        explorerRestoreRequest++
    }
    fun openSearch(fromShell: Boolean = false) {
        if (fromShell) restored = true // A late library emission must not restore focus over this dialog.
        revealSearchResults = false
        searchFromShell = fromShell
        searchExplorerOrigin = explorerOriginKey
        searchOriginIndex = grid.firstVisibleItemIndex
        searchOriginOffset = grid.firstVisibleItemScrollOffset
        searching = true
    }
    suspend fun restoreComic(id: String, fromRecent: Boolean) {
        val index = (if (fromRecent) recent else latestVisible).indexOfFirst { it.id == id }
        if (index < 0 || (previewHome && index >= (if (comicHome) COMIC_HOME_PREVIEW_LIMIT else HOME_PREVIEW_LIMIT)) || (fromRecent && history)) {
            revealHeader()
            focusHeaderControl()
            return
        }
        targetRecent = fromRecent
        positioned = null
        target = id
        val key = if (fromRecent) "recent-row" else if (previewHome) "home-collection-row" else id
        if (grid.layoutInfo.visibleItemsInfo.none { it.key == key }) {
            grid.scrollToItem(if (fromRecent) libraryGridRecentRowIndex(showExplorer) else if (previewHome) headerItems else headerItems + index)
        }
        if (fromRecent && !comicHome && recentList.layoutInfo.visibleItemsInfo.none { it.key == id }) recentList.scrollToItem(index.coerceAtMost(if (homeRows) HOME_PREVIEW_LIMIT - 1 else Int.MAX_VALUE))
        if (!fromRecent && homeRows && collectionList.layoutInfo.visibleItemsInfo.none { it.key == id }) collectionList.scrollToItem(index.coerceAtMost(HOME_PREVIEW_LIMIT - 1))
        snapshotFlow { positioned == id }.first { it }
        restore.requestFocus()
        target = null
    }
    fun openFullLibrary(historyPage: Boolean) {
        fullPageOrigin = homeOriginRow?.takeIf { it in listOf("recent-more", "collection-more") }
        navigateWithinContent { fullLibrary = true; history = historyPage; fullPageFocusRequest++ }
    }
    LaunchedEffect(fullPageFocusRequest) {
        if (fullPageFocusRequest > 0) {
            if (visible.isNotEmpty()) restoreComic(visible.first().id, false)
            else { revealHeader(); focusHeaderControl() }
        }
    }
    LaunchedEffect(homeReturnRequest) {
        if (homeReturnRequest > 0) {
            val recentOrigin = homeOriginRow == "recent-more"
            targetRecent = recentOrigin
            positioned = null
            target = if (recentOrigin) "recent-more" else "collection-more"
            grid.scrollToItem(if (recentOrigin) libraryGridRecentRowIndex(showExplorer) else headerItems)
            val list = if (recentOrigin) recentList else collectionList
            if (!comicHome) list.scrollToItem(if (recentOrigin) recent.size.coerceAtMost(HOME_PREVIEW_LIMIT) else visible.size.coerceAtMost(HOME_PREVIEW_LIMIT))
            snapshotFlow { positioned == target }.first { it }
            restore.requestFocus()
            target = null
        }
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
        results = (if (history) recent else comics).filter { it.id in matching }.map { LibrarySearchEntry(it.id, it.title, it.favorite) },
        onViewResults = {
            if (streaming && activeCollectionId == null && query.isNotBlank()) {
                fullLibrary = true
                revealSearchResults = true
            }
            searching = false; restoreControl++
        }) {
        searching = false; restoreControl++
    }
    if (managing) ManageCollections(repository) {
        managing = false
        menuScope.launch {
            if (footerControls) {
                revealFooter()
                filterList.scrollToItem(libraryFooterManageIndex(collections.size, (theme.navigation.layout == ThemeNavigationPolicy.Sidebar && !theme.navigation.searchInNavigation) || (theme.navigation.layout == ThemeNavigationPolicy.BottomDock && !shellSearchAvailable)))
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
            if (activeCollectionId != null) { collectionFocusRequest++; return@LaunchedEffect }
            if (revealSearchResults) {
                revealSearchResults = false
                if (visible.isNotEmpty()) restoreComic(visible.first().id, false)
                else {
                    grid.scrollToItem(headerItems)
                    snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "empty" } }.first { it }
                    withFrameNanos { }
                    emptyResultsFocus.requestFocus()
                }
                return@LaunchedEffect
            }
            if (footerControls && !topSearch && !searchFromShell) {
                revealFooter()
                searchFocus.requestFocus()
            } else {
                grid.scrollToItem(searchOriginIndex.coerceAtMost(footerIndex), searchOriginOffset)
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
        if (requestInitialFocus && activeCollectionId == null) {
            restored = true
            revealHeader()
            focusHeaderControl()
            onInitialFocus()
        }
    }
    optionsFor?.let { selected ->
        fun focusSelected() {
            if (optionsCollection && activeCollectionId != null) { collectionFocusedId = selected.id; onFocus(selected.id) }
            else if (previewHome) focusHomeComic(selected.id, if (optionsRecent) "recent" else "collection")
            else focusComic(selected.id)
        }
        HistoryOptions(selected, repository, onRead = { optionsFor = null; focusSelected(); onRead(selected.id) }, onOpen = { optionsFor = null; focusSelected(); onOpen(selected.id) },
            onDeleteOnline = onDeleteOnline,
            onDownload = onDownload?.let { callback -> {optionsFor = null;focusSelected();callback(selected.id);if (optionsCollection && activeCollectionId != null) collectionFocusRequest++} },offlineAvailable = selected.id in offlineIds,
            onDismiss = { menuScope.launch {
                optionsFor = null
                withFrameNanos { }
                if (optionsCollection && activeCollectionId != null) {
                    collectionFocusedId = selected.id
                    collectionFocusRequest++
                } else if (activeSagaKey == null) {
                    val current = try { repository.sagaGroups(comics).also { sagaGroups = it } }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { sagaGroups }
                    withFrameNanos { }
                    val origin = if (optionsRecent || history) selected.id else
                        current.firstOrNull { group -> group.editions.any { it.id == selected.id } }?.representative?.id ?: selected.id
                    restoreComic(origin, optionsRecent)
                }
            } })
    }
    val activeSaga = sagaGroups.firstOrNull { it.key == activeSagaKey }
    if (activeSaga != null && optionsFor == null) SagaEditionsDialog(activeSaga, repository, sagaEditionFocus, grid = sagaEditionsGrid,
        onFocus = { sagaEditionFocus = it }, onOpen = onOpen,
        onOptions = { optionsRecent = false; optionsCollection = false; optionsFor = it },
        onDismiss = {
            activeSagaKey = null
            val origin = sagaOriginId
            if (origin != null) menuScope.launch { restoreComic(origin, false) }
        })
    LaunchedEffect(sagaGroups, activeSagaKey, optionsFor, sagasLoaded) {
        if (sagasLoaded && activeSagaKey != null && activeSaga == null && optionsFor == null) {
            activeSagaKey = null
            val origin = sagaGroups.firstOrNull { group -> group.editions.any { it.id == sagaEditionFocus } }
                ?.representative?.id ?: sagaOriginId
            if (origin != null) restoreComic(origin, false) else { revealHeader(); focusHeaderControl() }
        }
    }
    val warmEntries = remember(visible, repository) { visible.map {CoverWarmEntry(it.id,repository.cover(it.id))} }
    WarmGridCovers(grid, warmEntries)
    // Capture the entry target once: database emissions must not take focus from navigation.
    val entryId = remember { focusedId }
    val entryExplorerKey = remember { explorerOriginKey }
    LaunchedEffect(comics, history, query, filter, memberships, searching, searchRequested, sagasLoaded) {
        if (!sagasLoaded || activeSagaKey != null || activeCollectionId != null || sectionEntry || restored || searching || searchRequested) return@LaunchedEffect
        if (homeSourceRows && homeOriginRow in listOf("catalog", "latest")) {
            restored = true
            grid.scrollToItem(homeSourceRowIndex(recent.size, showExplorer, homeOriginRow == "latest", if (previewHome) 1 else visible.size))
            sourceRestoreItem = homeOriginItem
            sourceRestoreRow = homeOriginRow
            return@LaunchedEffect
        }
        if (comics.isEmpty()) return@LaunchedEffect
        if (entryExplorerKey != null) {
            restored = true
            if (explorerOriginKey == entryExplorerKey) restoreExplorer(entryExplorerKey)
            return@LaunchedEffect
        }
        if (entryId == null) return@LaunchedEffect
        // Recent editions are independent of the grouped collection's representative IDs.
        if (homeOriginRow == "recent") {
            restored = true
            restoreComic(entryId, true)
            return@LaunchedEffect
        }
        val index = visible.indexOfFirst { it.id == entryId }
        if (index < 0) {
            grid.scrollToItem(0)
            withFrameNanos { }
            focusHeaderControl()
            restored = true
            return@LaunchedEffect
        }
        if (previewHome) {
            restored = true
            if (homeOriginRow == "recent") restoreComic(entryId, true)
            else if (homeOriginRow == "collection") restoreComic(entryId, false)
            else { revealHeader(); focusHeaderControl() }
            return@LaunchedEffect
        }
        if (!collectionVisible) {
            restored = true
            if (recent.any { it.id == entryId }) restoreComic(entryId, true)
            else { revealHeader(); focusHeaderControl() }
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
    fun returnHome() {
        activeCollectionId = null
        menuScope.launch { withFrameNanos { }; explorerOriginKey?.let { restoreExplorer(it) } }
    }
    BackHandler(enabled = (streaming || TvColors.options.preset == ColorPreset.Amber) && !searching && !managing && (activeCollectionId != null || fullLibrary)) {
        if (activeCollectionId != null) returnHome()
        else {
            navigateWithinContent {
                fullLibrary = false; history = false
                homeOriginRow = fullPageOrigin
                fullPageOrigin = null
                if (homeOriginRow in listOf("recent-more", "collection-more")) homeReturnRequest++
                else menuScope.launch { withFrameNanos { }; revealHeader(); focusHeaderControl() }
            }
        }
    }
    LaunchedEffect(activeCollectionId, collections) {
        if (activeCollectionId != null && collections.none { it.id == activeCollectionId }) returnHome()
    }
    var headerHasFocus by remember { mutableStateOf(false) }
    var heroKeyHeld by remember { mutableStateOf(false) }
    var heroKeyActivity by remember { mutableIntStateOf(0) }
    // A key-up can be consumed by an overlay or disappear on focus transfer.
    // Repeated key-down events renew this lease; a lost release cannot freeze autoplay.
    LaunchedEffect(heroKeyActivity) {
        if (heroKeyHeld) { delay(800); heroKeyHeld = false }
    }
    val heroInteractionCallback by rememberUpdatedState(onHeroInteractionChanged)
    val heroInteraction = activeSagaKey != null || heroKeyHeld || searching || managing || optionsFor != null || fullLibrary || activeCollectionId != null || !heroVisible
    LaunchedEffect(heroInteraction) { heroInteractionCallback(heroInteraction) }
    DisposableEffect(Unit) { onDispose { heroInteractionCallback(false) } }
    val heroVisibleCallback by rememberUpdatedState(onHeroVisibleChanged)
    LaunchedEffect(heroVisible) { heroVisibleCallback(heroVisible) }
    DisposableEffect(Unit) { onDispose { heroVisibleCallback(true) } }
    LaunchedEffect(headerHasFocus) {
        // A header action is below the non-focusable title/poster; restore the entire header.
        // Only actual focus entry triggers this, never new database/carousel emissions.
        if (headerHasFocus && footerControls) grid.scrollToItem(0)
    }
    val activeCollection = collections.firstOrNull { it.id == activeCollectionId }
    if (maxLayout && activeCollection != null) {
        MaxCollectionPage(activeCollection, comics.filter { it.id in memberIndex[activeCollection.id].orEmpty() },
            repository, offlineIds, collectionGrid, collectionFocusedId, collectionFocusRequest,
            restoreEnabled = !searching && optionsFor == null,
            onFocus = { collectionFocusedId = it; onFocus(it) }, onOpen = onOpen,
            onOptions = { optionsCollection = true; optionsFor = it }, onBack = ::returnHome)
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    var brandHeightPx by remember { mutableIntStateOf(0) }
    var followingHeadingPx by remember { mutableIntStateOf(0) }
    var followingRowPx by remember { mutableIntStateOf(0) }
    val focusSafety = remember(portrait) { if (portrait) HomeFocusSafety(baseHorizontalDp = 24) else HomeFocusSafety() }
    val gridTop = HomeSafeArea.verticalDp.toFloat()
    val gridBottom = HomeSafeArea.bottomInsetDp(focusSafety.overflowDp).toFloat()
    val requestedHeroHeight = when (theme.hero.heightPolicy) {
        HeroHeightPolicy.ViewportInset -> maxHeight - theme.hero.viewportInsetDp.dp
        HeroHeightPolicy.ReservedReadingRow -> (maxHeight - theme.hero.viewportInsetDp.dp)
            .coerceAtLeast(theme.hero.minimumHeightDp.dp)
        HeroHeightPolicy.Fraction -> maxHeight * theme.hero.viewportFraction
    }
    val firstRowEstimate = if (!history && recent.isNotEmpty()) {
            val cards = theme.cards
            val cardHeight = if (cards.continueStyle in setOf(ContinueCardStyle.ArtLarge, ContinueCardStyle.Art16x9))
                cards.recentHeightDp.coerceAtLeast(114) * textScale
            else maxOf(cards.recentHeightDp.toFloat(), cards.recentThumbnailHeightDp + 2f * cards.recentPaddingDp,
                (2f * theme.typography.cardLineHeightSp + 1.4f * theme.typography.cardStatusSp) * textScale + 18f + 2f * cards.recentPaddingDp)
            24f * textScale + cardHeight + 8f
        } else if (showExplorer) 12f + 24f * textScale + 8f + 88f + 8f + 36f * textScale + 8f else 0f
    val measuredFollowing = with(density) { (if (recent.isNotEmpty()) followingHeadingPx.toDp().value else 0f) + followingRowPx.toDp().value }
    val brandHeight = if (theme.navigation.libraryBrand) with(density) { brandHeightPx.toDp().value }.coerceAtLeast(38f) else 0f
    val streamingHeroHeight = if (footerControls && !cinema) homeHeroViewportBudget(maxHeight.value,
        requestedHeroHeight.value, brandHeight, maxOf(firstRowEstimate, measuredFollowing), gridTop, gridBottom,
        rowGapsDp = if (showExplorer && recent.isEmpty()) 10f else 20f).dp else requestedHeroHeight
    // Flix/Prime/Max share TvShell's full-viewport artwork, including navigation.
    if (cinema) CinemaBackdrop(featured?.id, repository, Modifier.matchParentSize(), dimmed = !heroVisible)
    // Max Home grows upward from its permanent dock; full pages keep normal reading order.
    val invertedHome = maxLayout && homeRows && !portrait
    @Composable fun CollectionHeading() {
        Column(Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sagaError?.let { Text(it, color = TvColors.Amber) }
            val label = when { filter == "favorites" -> "Favoritos"; filter.startsWith("collection:") -> collections.firstOrNull { "collection:" + it.id == filter }?.name ?: "Coleção"; else -> if (history) "Histórico" else "Sua coleção" }
            Text(label + " · " + visible.size + (if (query.isNotBlank()) " · Busca: “${query.trim()}”" else ""),
                fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            if (query.isNotBlank()) TvButton("Limpar busca", { onQuery("") })
        }
    }
    @Composable fun RecentHeading() {
        Text("Continuar lendo", Modifier.onSizeChanged { followingHeadingPx = it.height },
            fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
    var consumeHeaderUp by remember { mutableStateOf(false) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val homeBringIntoView = remember {
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                homeHeaderBringIntoViewDistance(offset, size, containerSize,
                    headerHasFocus && headerHeightPx + grid.layoutInfo.beforeContentPadding <= containerSize)
        }
    }
    CompositionLocalProvider(LocalHomeFocusSafety provides focusSafety,
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
        if (footerControls) homeBringIntoView else androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(theme.cards.gridMinimumWidthDp.dp * (if (theme.cards.scaleGridWithText) textScale else 1f)), state = grid,
        modifier = Modifier.fillMaxSize().focusRestorer().testTag("library-grid")
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) { heroKeyHeld = true; heroKeyActivity++ }
                else if (event.type == KeyEventType.KeyUp) heroKeyHeld = false
                false
            },
        contentPadding = PaddingValues(start = focusSafety.horizontalDp.dp, end = focusSafety.horizontalDp.dp, top = gridTop.dp, bottom = if (invertedHome) focusSafety.overflowDp.dp else gridBottom.dp),
        reverseLayout = invertedHome,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = if (invertedHome) Arrangement.spacedBy(10.dp, Alignment.Bottom) else Arrangement.spacedBy(10.dp),
    ) {
        item(key = "library-header", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.testTag("library-header").onSizeChanged { headerHeightPx = it.height }
                .onFocusChanged {
                    headerHasFocus = it.hasFocus
                    if (!it.hasFocus) heroKeyHeld = false
                }
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.DirectionUp || invertedHome) false
                    else if (event.type == KeyEventType.KeyUp && consumeHeaderUp) {
                        consumeHeaderUp = false
                        true
                    } else if (event.type == KeyEventType.KeyDown && (consumeHeaderUp || !pageAtTop)) {
                        consumeHeaderUp = true
                        menuScope.launch { grid.scrollToItem(0) }
                        true
                    } else false
                }.focusGroup(),
                verticalArrangement = Arrangement.spacedBy(if (footerControls) 10.dp else 18.dp)) {
                if (footerControls) {
                    if (theme.navigation.libraryBrand) ComicWordmark(Modifier.padding(start = if (theme.navigation.layout == ThemeNavigationPolicy.Sidebar) 10.dp else 0.dp).width(180.dp * textScale).heightIn(min = 38.dp)
                        .onSizeChanged { brandHeightPx = it.height }.testTag("library-brand"))
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
                        comics = comics, onSelect = ::focusComic, onActionFocused = ::clearExplorerOrigin,
                        minimumHeightOverrideDp = streamingHeroHeight.value,
                        posterHeightLimitDp = (streamingHeroHeight.value - theme.hero.verticalPaddingDp * 2).coerceAtLeast(150f))
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
            // Keep both items in the upward layout: indices stay stable and the heading stays above its row.
            item(key = "recent-heading", span = { GridItemSpan(maxLineSpan) }) {
                if (invertedHome) Spacer(Modifier.height(0.dp)) else RecentHeading()
            }
            item(key = "recent-row", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (invertedHome) RecentHeading()
                if (comicHome) {
                    ComicHomePreviewGrid(recent, { it.id },
                        onMore = { homeOriginRow = "recent-more"; openFullLibrary(true) },
                        modifier = Modifier.testTag("recent-row-viewport").onSizeChanged { followingRowPx = it.height },
                        moreModifier = Modifier.testTag("home-recent-more").then(if (target == "recent-more")
                            Modifier.focusRequester(restore).onGloballyPositioned { positioned = "recent-more" } else Modifier)) { comic ->
                        val restoreModifier = if (targetRecent && target == comic.id) Modifier.focusRequester(restore)
                            .onGloballyPositioned { positioned = comic.id } else Modifier
                        Card(onClick = { focusHomeComic(comic.id, "recent"); onOpen(comic.id) },
                            modifier = restoreModifier.homeFocusCard(theme.focus.recentScale, theme.focus.cardBorderDp.toFloat()).fillMaxWidth().testTag("library-recent-${comic.id}")
                                .then(libraryCardKeys({ focusHomeComic(comic.id, "recent"); onOpen(comic.id) }, { optionsCollection = false; optionsRecent = true; optionsFor = comic }))
                                .onFocusChanged { if (it.isFocused) focusHomeComic(comic.id, "recent") },
                            scale = CardDefaults.scale(focusedScale = theme.focus.recentScale),
                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                            colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent)) {
                            CollectionCardContent(comic, repository, comic.id in offlineIds)
                        }
                    }
                } else
                LazyRow(
                    state = recentList,
                    modifier = Modifier.homeRowViewport().fillMaxWidth().testTag("recent-row-viewport").onSizeChanged { followingRowPx = it.height },
                    contentPadding = focusSafety.rowPadding,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(if (homeRows) homePreviewItems(recent) { it.id } else recent, key = { it.id }) { comic ->
                        val restoreModifier = if (targetRecent && target == comic.id) Modifier.focusRequester(restore)
                            .onGloballyPositioned { positioned = comic.id } else Modifier
                        Card(
                            onClick = { focusHomeComic(comic.id, "recent"); onOpen(comic.id) },
                            modifier = restoreModifier.homeFocusCard(theme.focus.recentScale, theme.focus.cardBorderDp.toFloat()).then(libraryCardKeys({ focusHomeComic(comic.id, "recent"); onOpen(comic.id) }, { optionsCollection = false; optionsRecent = true; optionsFor = comic })).width(theme.cards.recentWidthDp.dp).testTag("library-recent-${comic.id}").onFocusChanged { if (it.isFocused) focusHomeComic(comic.id, "recent") },
                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                            shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(theme.shapes.controlRadiusDp.dp)),
                            scale = CardDefaults.scale(focusedScale = theme.focus.recentScale),
                            colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                        ) {
                            RecentCardContent(comic, repository)
                        }
                    }
                    if (homeRows) item(key = "recent-more") { TvButton("Ver mais", { homeOriginRow = "recent-more"; openFullLibrary(true) }, Modifier.testTag("home-recent-more")
                        .then(if (target == "recent-more") Modifier.focusRequester(restore).onGloballyPositioned { positioned = "recent-more" } else Modifier)) }
                }
                }
            }
        }

        if (showExplorer) {
            item(key = "max-explore", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.onSizeChanged { if (recent.isEmpty()) followingRowPx = it.height }) {
                MaxExploreRow(comics, collections, memberships, repository, filter, onFilter,
                    onCollectionOpen = { id -> navigateWithinContent {
                        collectionFocusedId = null; activeCollectionId = id; collectionFocusRequest++
                    } },
                    onOpen = { featuredId = it; onFocus(it); onOpen(it) }, state = explorerList,
                    onItemFocused = { key, comicId ->
                        homeOriginRow = null; homeOriginItem = null
                        explorerOriginKey = key
                        comicId?.let { featuredId = it; onFocus(it) }
                    }, restoreKey = explorerRestoreKey, restoreRequest = explorerRestoreRequest,
                    onRestoreHandled = { explorerRestoreKey = null })
                }
            }
        }

        if (collectionVisible) {
        item(key = "collection-heading", span = { GridItemSpan(maxLineSpan) }) {
            if (invertedHome) Spacer(Modifier.height(0.dp)) else CollectionHeading()
        }
        if (visible.isEmpty() && !previewHome) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val filtered = query.isNotBlank() || filter != "all"
                    Text(if (filtered) "Nenhum quadrinho encontrado" else if (history) "Nenhuma leitura no histórico" else "Sua coleção começa aqui", fontSize = 24.sp)
                    Text(
                        if (filtered) "Tente outro título ou selecione Todas para mudar o filtro."
                        else if (history) "Abra uma edição para encontrá-la aqui, inclusive depois de terminar."
                        else "Importe CBZ, ZIP, CBR, RAR, 7z, CB7, PDF ou EPUB para começar.",
                        color = TvColors.Muted, fontSize = 17.sp,
                    )
                    if (history) TvButton("Ver biblioteca", { history = false })
                    if (filtered) TvButton("Mostrar todas", { onQuery(""); onFilter("all") }, Modifier.focusRequester(emptyResultsFocus))

                }
            }
        }
        if (comicHome) {
            item(key = "home-collection-row", span = { GridItemSpan(maxLineSpan) }) {
                ComicHomePreviewGrid(visible, { it.id },
                    onMore = { homeOriginRow = "collection-more"; openFullLibrary(false) },
                    modifier = Modifier.testTag("home-collection-row"),
                    moreModifier = Modifier.testTag("home-collection-more").then(if (target == "collection-more")
                        Modifier.focusRequester(restore).onGloballyPositioned { positioned = "collection-more" } else Modifier)) { comic ->
                    val restoreModifier = if (!targetRecent && target == comic.id) Modifier.focusRequester(restore)
                        .onGloballyPositioned { positioned = comic.id } else Modifier
                    Card(onClick = { focusHomeComic(comic.id, "collection"); openCollectionComic(comic.id) },
                        modifier = restoreModifier.homeFocusCard(theme.focus.collectionScale, theme.focus.cardBorderDp.toFloat()).fillMaxWidth().testTag("home-collection-${comic.id}")
                            .then(libraryCardKeys({ focusHomeComic(comic.id, "collection"); openCollectionComic(comic.id) }, { optionsCollection = false; optionsRecent = false; optionsFor = comics.firstOrNull { it.id == comic.id } ?: comic }))
                            .onFocusChanged { if (it.isFocused) focusHomeComic(comic.id, "collection") },
                        scale = CardDefaults.scale(focusedScale = theme.focus.collectionScale),
                        border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                        colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent)) {
                        CollectionCardContent(comic, repository, comic.id in offlineIds, collectionCover(comic.id), saga = isSaga(comic.id))
                    }
                }
            }
        } else
        if (homeRows) {
            item(key = "home-collection-row", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (invertedHome) CollectionHeading()
                // No focusRestorer: entering from another row keeps the D-pad column, not the last visited card.
                LazyRow(state = collectionList, modifier = Modifier.homeRowViewport().testTag("home-collection-row"), contentPadding = focusSafety.rowPadding,
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(homePreviewItems(visible) { it.id }, key = { it.id }) { comic ->
                        val restoreModifier = if (!targetRecent && target == comic.id) Modifier.focusRequester(restore)
                            .onGloballyPositioned { positioned = comic.id } else Modifier
                        Card(onClick = { focusHomeComic(comic.id, "collection"); openCollectionComic(comic.id) },
                            modifier = restoreModifier.homeFocusCard(theme.focus.collectionScale, theme.focus.cardBorderDp.toFloat()).testTag("home-collection-${comic.id}").width(if (horizontalCards) 260.dp else theme.cards.gridMinimumWidthDp.dp)
                                .then(libraryCardKeys({ focusHomeComic(comic.id, "collection"); openCollectionComic(comic.id) }, { optionsCollection = false; optionsRecent = false; optionsFor = comics.firstOrNull { it.id == comic.id } ?: comic }))
                                .onFocusChanged { if (it.isFocused) focusHomeComic(comic.id, "collection") },
                            scale = CardDefaults.scale(focusedScale = theme.focus.collectionScale),
                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                            colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent)) {
                            CollectionCardContent(comic, repository, comic.id in offlineIds, collectionCover(comic.id), saga = isSaga(comic.id))
                        }
                    }
                    item(key = "collection-more") { TvButton("Ver mais", { homeOriginRow = "collection-more"; openFullLibrary(false) }, Modifier.testTag("home-collection-more")
                        .then(if (target == "collection-more") Modifier.focusRequester(restore).onGloballyPositioned { positioned = "collection-more" } else Modifier)) }
                }
                }
            }
        } else items(visible, key = { it.id }) { comic ->
            val restoreModifier = if (!targetRecent && target == comic.id) Modifier.focusRequester(restore)
                .onGloballyPositioned { positioned = comic.id } else Modifier
            Card(
                onClick = { focusComic(comic.id); openCollectionComic(comic.id) },
                modifier = restoreModifier.homeFocusCard(theme.focus.collectionScale, theme.focus.cardBorderDp.toFloat()).then(libraryCardKeys({ focusComic(comic.id); openCollectionComic(comic.id) }, { optionsCollection = false; optionsRecent = false; optionsFor = comics.firstOrNull { it.id == comic.id } ?: comic })).testTag("library-cover-${comic.id}").onFocusChanged { if (it.isFocused) focusComic(comic.id) },
                scale = CardDefaults.scale(focusedScale = theme.focus.collectionScale),
                shape = CardDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(theme.shapes.controlRadiusDp.dp)),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                colors = if (horizontalCards) CardDefaults.colors(
                    containerColor = Color.Transparent, focusedContainerColor = Color.Transparent)
                    else CardDefaults.colors(containerColor = TvColors.Panel),
            ) {
                CollectionCardContent(comic, repository, comic.id in offlineIds, collectionCover(comic.id), saga = isSaga(comic.id))
            }
        }
        }
        if (homeSourceRows) {
            listOf(false, true).forEach { latest ->
                val row = if (latest) "latest" else "catalog"
                item(key = "home-$row", span = { GridItemSpan(maxLineSpan) }) {
                    HomeSourceRow(if (latest) "Novidades" else "Catálogo", if (latest) homeSources.latest else homeSources.catalog,
                        if (latest) latestList else catalogList,
                        onOpen = { onSourceTitle(it, latest) }, onMore = { onSourceSection(latest) },
                        onFocus = { restored = true; homeOriginRow = row; homeOriginItem = it; clearExplorerOrigin() },
                        gridPreview = comicHome, restoreKey = if (sourceRestoreRow == row) sourceRestoreItem else null,
                        onRestored = { sourceRestoreRow = null; sourceRestoreItem = null })
                }
            }
        }
        if (footerControls) {
            item(key = "library-footer", span = { GridItemSpan(maxLineSpan) }) {
                LibraryFooter(collections, filter, { key ->
                    if (key == "all") {
                        fullLibrary = false; history = false; onQuery(""); onFilter("all")
                        activeCollectionId = null; clearExplorerOrigin(); homeOriginRow = null; homeOriginItem = null
                    } else { fullLibrary = true; onFilter(key) }
                }, { managing = true },
                    comics.size, recent.size, history, searching || query.isNotBlank(),
                    { openSearch() }, { fullLibrary = true; history = false }, { history = true },
                    showSearch = (theme.navigation.layout == ThemeNavigationPolicy.Sidebar && !theme.navigation.searchInNavigation) || (theme.navigation.layout == ThemeNavigationPolicy.BottomDock && !shellSearchAvailable),
                    searchModifier = Modifier.focusRequester(searchFocus), manageModifier = Modifier.focusRequester(manageFocus),
                    filterListState = filterList)
            }
        }
    }
    }
    }
}

internal fun libraryGridRecentRowIndex(explorer: Boolean = false): Int = 2

/** Header + collection heading; the optional recent heading/row precede every cover. */
internal fun libraryGridHeaderItems(history: Boolean, recentCount: Int, explorer: Boolean = false, collectionVisible: Boolean = true): Int =
    1 + (if (collectionVisible) 1 else 0) + (if (!history && recentCount > 0) 2 else 0) + (if (explorer) 1 else 0)

internal fun libraryGridFooterIndex(history: Boolean, recentCount: Int, visibleCount: Int, explorer: Boolean = false,
    collectionVisible: Boolean = true, sourceRows: Boolean = false): Int =
    libraryGridHeaderItems(history, recentCount, explorer, collectionVisible) +
        (if (collectionVisible) visibleCount.coerceAtLeast(1) else 0) + (if (sourceRows) 2 else 0)

internal fun readingFraction(comic: ComicEntity): Float =
    (comic.currentPage + 1f) / comic.pageCount.coerceAtLeast(1)

internal fun readingStatus(comic: ComicEntity): String =
    if (comic.pageCount > 0 && comic.currentPage >= comic.pageCount - 1) "Concluído"
    else "Página ${comic.currentPage + 1} de ${comic.pageCount}"

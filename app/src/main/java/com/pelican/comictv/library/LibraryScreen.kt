package com.pelican.comictv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
) {
    val grid = rememberLazyGridState()
    val restore = remember { FocusRequester() }
    var history by rememberSaveable { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var positioned by remember { mutableStateOf<String?>(null) }
    var featuredId by remember { mutableStateOf(focusedId) }
    fun focusComic(id: String) { featuredId = id; onFocus(id) }
    val featured = featuredComic(comics, featuredId)
    val recent = comics.filter { it.lastRead > 0 && !it.hiddenFromHistory }.sortedByDescending { it.lastRead }
    var optionsFor by remember { mutableStateOf<ComicEntity?>(null) }
    val menuScope = rememberCoroutineScope()
    val headerFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var searching by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var restoreControl by remember { mutableIntStateOf(0) }
    var restoreManage by remember { mutableIntStateOf(0) }
    val searchEntries = remember(comics) { comics.map { LibrarySearchEntry(it.id, it.title, it.favorite) } }
    val memberIndex = remember(memberships) { memberships.groupBy { it.collectionId }.mapValues { (_, rows) -> rows.map { it.comicId }.toSet() } }
    val matching = remember(searchEntries, query, filter, memberIndex) { filterLibrary(searchEntries, query, filter, memberIndex).toSet() }
    if (searching) LibrarySearchDialog(query, onQuery) { searching = false; restoreControl++ }
    if (managing) ManageCollections(repository) {
        managing = false
        menuScope.launch { grid.scrollToItem(0); withFrameNanos { }; restoreManage++ }
    }
    LaunchedEffect(restoreControl) { if (restoreControl > 0) { grid.scrollToItem(0); withFrameNanos { }; searchFocus.requestFocus() } }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            grid.scrollToItem(0)
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "library-header" } }.first { it }
            headerFocus.requestFocus()
            onInitialFocus()
        }
    }
    optionsFor?.let { selected ->
        HistoryOptions(selected, repository, onRead = { optionsFor = null; focusComic(selected.id); onRead(selected.id) }, onOpen = { optionsFor = null; focusComic(selected.id); onOpen(selected.id) },
            onDismiss = { menuScope.launch {
                grid.scrollToItem(0)
                optionsFor = null
                withFrameNanos { }
                headerFocus.requestFocus()
            } })
    }
    val visible = (if (history) recent else comics).filter { it.id in matching }
    // Capture the entry target once: database emissions must not take focus from navigation.
    val entryId = remember { focusedId }
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(comics, history, query, filter, memberships) {
        if (restored || entryId == null) return@LaunchedEffect
        if (comics.isEmpty()) return@LaunchedEffect
        val index = visible.indexOfFirst { it.id == entryId }
        if (index < 0) {
            grid.scrollToItem(0)
            withFrameNanos { }
            headerFocus.requestFocus()
            restored = true
            return@LaunchedEffect
        }
        target = entryId
        positioned = null
        val headerItems = if (!history && recent.isNotEmpty()) 4 else 2
        grid.scrollToItem(headerItems + index)
        snapshotFlow { positioned == entryId }.first { it }
        restore.requestFocus()
        target = null
        restored = true
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp), state = grid,
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "library-header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComicWordmark(Modifier.width(180.dp).heightIn(min = 38.dp))
                    Spacer(Modifier.weight(1f))
                    LibraryTab("Buscar", searching || query.isNotBlank(), { searching = true }, Modifier.focusRequester(searchFocus))
                    LibraryTab("Biblioteca · " + comics.size, !history, { history = false }, Modifier.focusRequester(headerFocus))
                    LibraryTab("Histórico · " + recent.size, history, { history = true })
                }
                LibraryFilters(collections, filter, onFilter, { managing = true }, restoreManage)
                if (!history) LibraryHero(featured, repository,
                    onRead = { focusComic(it); onRead(it) }, onOpen = { focusComic(it); onOpen(it) }, onImport = onImport)
            }
        }
        if (!history && recent.isNotEmpty()) {
            item(key = "recent-heading", span = { GridItemSpan(maxLineSpan) }) {
                Text("Continuar lendo", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            item(key = "recent-row", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(recent, key = { it.id }) { comic ->
                        Card(
                            onClick = { focusComic(comic.id); onOpen(comic.id) },
                            modifier = libraryCardKeys({ focusComic(comic.id); onOpen(comic.id) }, { optionsFor = comic }).width(280.dp).onFocusChanged { if (it.isFocused) focusComic(comic.id) },
                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber))),
                            colors = CardDefaults.colors(containerColor = TvColors.Panel),
                        ) {
                            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AsyncImage(repository.cover(comic.id), null, Modifier.width(38.dp).height(56.dp), contentScale = ContentScale.Fit)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(comic.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
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
                Text(label + " · " + visible.size + (if (query.isNotBlank()) " · Busca: “${query.trim()}”" else ""), fontSize = 19.sp)
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
            val restoreModifier = if (target == comic.id) Modifier.focusRequester(restore)
                .onGloballyPositioned { positioned = comic.id } else Modifier
            Card(
                onClick = { focusComic(comic.id); onOpen(comic.id) },
                modifier = restoreModifier.then(libraryCardKeys({ focusComic(comic.id); onOpen(comic.id) }, { optionsFor = comic })).onFocusChanged { if (it.isFocused) focusComic(comic.id) },
                scale = CardDefaults.scale(focusedScale = 1.03f),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber))),
                colors = CardDefaults.colors(containerColor = TvColors.Panel),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(TvColors.Panel)) {
                    AsyncImage(repository.cover(comic.id), comic.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
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
    }
}

private fun readingFraction(comic: ComicEntity): Float =
    (comic.currentPage + 1f) / comic.pageCount.coerceAtLeast(1)

private fun readingStatus(comic: ComicEntity): String =
    if (comic.pageCount > 0 && comic.currentPage >= comic.pageCount - 1) "Concluído"
    else "Página ${comic.currentPage + 1} de ${comic.pageCount}"

@Composable private fun LibraryTab(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier.heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(containerColor = Color.Transparent,
            contentColor = if (active) TvColors.Amber else TvColors.Muted,
            focusedContainerColor = TvColors.Action, focusedContentColor = TvColors.OnAction)) {
        Text(label, fontSize = 14.sp)
    }
}

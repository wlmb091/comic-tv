package com.pelican.comictv.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first

@Composable internal fun LibraryFilters(collections: List<CollectionEntity>, filter: String,
    onFilter: (String) -> Unit, onManage: () -> Unit, restoreManage: Int = 0, returnFocusEnabled: Boolean = true,
    entryModifier: Modifier? = null, listState: LazyListState = rememberLazyListState(), modifier: Modifier = Modifier,
    manageModifier: Modifier = Modifier) {
    val list = listState
    val manage = remember { FocusRequester() }
    LazyRow(modifier.testTag("library-filters"), state = list, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(3.dp)) {
        item(key = "all") {
            val first = entryModifier ?: preferredContentEntryModifier(enabled = returnFocusEnabled)
            FilterButton("Todas", "all", filter, onFilter, first)
        }
        item(key = "favorites") { FilterButton("★ Favoritos", "favorites", filter, onFilter) }
        items(collections, key = { it.id }) { FilterButton(it.name, "collection:" + it.id, filter, onFilter) }
        item(key = "manage") { TvButton("Gerenciar coleções", onManage, Modifier.focusRequester(manage).then(manageModifier), transparentBackground = true) }
    }
    LaunchedEffect(restoreManage) {
        if (restoreManage > 0) {
            list.scrollToItem(collections.size + 2)
            snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == "manage" } }.first { it }
            manage.requestFocus()
        }
    }
}

/** Prime and Max keep filters and library actions on one line, including at130% text. */
@Composable internal fun LibraryControlsRow(
    collections: List<CollectionEntity>, filter: String, onFilter: (String) -> Unit, onManage: () -> Unit,
    libraryCount: Int, historyCount: Int, history: Boolean, searchActive: Boolean,
    onSearch: () -> Unit, onLibrary: () -> Unit, onHistory: () -> Unit,
    restoreManage: Int = 0, returnFocusEnabled: Boolean = true,
    entryModifier: Modifier = Modifier, searchModifier: Modifier = Modifier,
    filterListState: LazyListState = rememberLazyListState(),
) {
    Row(Modifier.fillMaxWidth().testTag("library-controls-row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (TvColors.options.preset == ColorPreset.Max) {
            ComicWordmark(Modifier.width(180.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).testTag("library-brand"))
        }
        LibraryFilters(collections, filter, onFilter, onManage, restoreManage, returnFocusEnabled,
            entryModifier, filterListState, Modifier.weight(.54f))
        // Independent scrolling preserves one controls line without shrinking text or targets.
        Row(Modifier.weight(.46f).horizontalScroll(rememberScrollState())
            .padding(3.dp).testTag("library-header-actions"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryHeaderActions(libraryCount, historyCount, history, searchActive,
                onSearch, onLibrary, onHistory, searchModifier = searchModifier)
        }
    }
}

@Composable private fun FilterButton(label: String, key: String, filter: String, onFilter: (String) -> Unit, modifier: Modifier = Modifier) {
    TvChoice(label, filter == key, { onFilter(key) }, modifier, transparentBackground = true)
}

/** Secondary browsing controls follow the complete collection, outside the featured image. */
@Composable internal fun LibraryFooter(
    collections: List<CollectionEntity>, filter: String, onFilter: (String) -> Unit, onManage: () -> Unit,
    libraryCount: Int, historyCount: Int, history: Boolean, searchActive: Boolean,
    onSearch: () -> Unit, onLibrary: () -> Unit, onHistory: () -> Unit,
    showSearch: Boolean, searchModifier: Modifier = Modifier, manageModifier: Modifier = Modifier,
    filterListState: LazyListState = rememberLazyListState(),
) {
    LazyRow(Modifier.fillMaxWidth().testTag("library-footer").padding(top = 12.dp, bottom = 12.dp),
        state = filterListState, contentPadding = PaddingValues(3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showSearch) item(key = "search") { TvChoice("Buscar", searchActive, onSearch, searchModifier.heightIn(min = 48.dp), compact = true, transparentBackground = true) }
        item(key = "library") { TvChoice("Biblioteca · $libraryCount", !history, onLibrary, Modifier.heightIn(min = 48.dp), compact = true, transparentBackground = true) }
        item(key = "history") { TvChoice("Histórico · $historyCount", history, onHistory, Modifier.heightIn(min = 48.dp), compact = true, transparentBackground = true) }
        item(key = "all") { TvChoice("Todas", filter == "all", { onFilter("all") }, Modifier.heightIn(min = 48.dp), compact = true, transparentBackground = true) }
        item(key = "favorites") { TvChoice("★ Favoritos", filter == "favorites", { onFilter("favorites") }, Modifier.heightIn(min = 48.dp), compact = true, transparentBackground = true) }
        items(collections, key = { it.id }) { collection -> TvChoice(collection.name, filter == "collection:${collection.id}", { onFilter("collection:${collection.id}") }, Modifier.heightIn(min = 48.dp), compact = true, transparentBackground = true) }
        item(key = "manage") { TvChoice("Gerenciar coleções", false, onManage, manageModifier.heightIn(min = 48.dp), compact = true, transparentBackground = true) }
    }
}

@Composable internal fun LibrarySearchDialog(query: String, onQuery: (String) -> Unit,
    results: List<LibrarySearchEntry> = emptyList(), onViewResults: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LibraryDialog("Buscar na biblioteca", { keyboard?.hide(); onDismiss() }) {
        Text("Busca por título na lista de capas. O filtro selecionado é mantido.", color = TvColors.Muted)
        LibraryTextInput(query, onQuery, "Título do quadrinho", Modifier.focusRequester(first)) { keyboard?.hide(); (onViewResults ?: onDismiss)() }
        Text(if (results.isEmpty()) "Nenhum título combina com a busca." else "${results.size} título(s) encontrado(s)", color = TvColors.Amber)
        results.take(4).forEach { Text(it.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        if (results.size > 4) Text("Mais ${results.size - 4} na lista de capas", color = TvColors.Muted)
        TvButton("Ver resultados", { keyboard?.hide(); (onViewResults ?: onDismiss)() })
        TvButton("Limpar busca", { onQuery("") })
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}

internal fun libraryFooterManageIndex(collectionCount: Int, showSearch: Boolean): Int = collectionCount + 4 + if (showSearch) 1 else 0

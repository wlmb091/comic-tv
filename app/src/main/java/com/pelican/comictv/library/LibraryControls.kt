package com.pelican.comictv.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first

@Composable internal fun LibraryFilters(collections: List<CollectionEntity>, filter: String,
    onFilter: (String) -> Unit, onManage: () -> Unit, restoreManage: Int = 0) {
    val list = rememberLazyListState()
    val manage = remember { FocusRequester() }
    LazyRow(Modifier.testTag("library-filters"), state = list, horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(3.dp)) {
        item(key = "all") { FilterButton("Todas", "all", filter, onFilter) }
        item(key = "favorites") { FilterButton("★ Favoritos", "favorites", filter, onFilter) }
        items(collections, key = { it.id }) { FilterButton(it.name, "collection:" + it.id, filter, onFilter) }
        item(key = "manage") { TvButton("Gerenciar coleções", onManage, Modifier.focusRequester(manage)) }
    }
    LaunchedEffect(restoreManage) {
        if (restoreManage > 0) {
            list.scrollToItem(collections.size + 2)
            snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == "manage" } }.first { it }
            manage.requestFocus()
        }
    }
}

@Composable private fun FilterButton(label: String, key: String, filter: String, onFilter: (String) -> Unit) {
    TvButton(label, { onFilter(key) }, Modifier.semantics { selected = filter == key }, primary = filter == key)
}

@Composable internal fun LibrarySearchDialog(query: String, onQuery: (String) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LibraryDialog("Buscar na biblioteca", { keyboard?.hide(); onDismiss() }) {
        Text("Busca por título na lista de capas. O filtro selecionado é mantido.", color = TvColors.Muted)
        LibraryTextInput(query, onQuery, "Título do quadrinho", Modifier.focusRequester(first)) { keyboard?.hide(); onDismiss() }
        TvButton("Ver resultados", { keyboard?.hide(); onDismiss() })
        TvButton("Limpar busca", { onQuery("") })
    }
    LaunchedEffect(Unit) { first.requestFocus(); keyboard?.show() }
}

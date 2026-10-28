package com.pelican.comictv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first

/** A distinct collection destination; its grid state is owned by LibraryScreen, not Home. */
@Composable internal fun MaxCollectionPage(
    collection: CollectionEntity, comics: List<ComicEntity>, repository: LibraryRepository,
    offlineIds: Set<String>, state: LazyGridState, focusedId: String?, focusRequest: Int,
    restoreEnabled: Boolean = true,
    onFocus: (String) -> Unit, onOpen: (String) -> Unit, onOptions: (ComicEntity) -> Unit, onBack: () -> Unit,
) {
    val theme = LocalThemeSpec.current
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val back = remember { FocusRequester() }
    val entry = preferredContentEntryModifier(prepare = { state.scrollToItem(0) })
    val requesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val positioned = remember { mutableStateMapOf<String, Boolean>() }
    val focusedRemoved = focusedId != null && comics.none { it.id == focusedId }
    LaunchedEffect(focusRequest, focusedRemoved, restoreEnabled) {
        if (!restoreEnabled) return@LaunchedEffect
        val id = focusedId?.let { candidate ->
            comics.firstOrNull { it.id == candidate }?.id
                ?: comics.getOrNull((state.firstVisibleItemIndex - 1).coerceIn(0, (comics.size - 1).coerceAtLeast(0)))?.id
        }
        if (id == null) {
            state.scrollToItem(0)
            withFrameNanos { }
            back.requestFocus()
        } else {
            val index = comics.indexOfFirst { it.id == id } + 1
            if (state.layoutInfo.visibleItemsInfo.none { it.key == id }) state.scrollToItem(index)
            snapshotFlow { positioned[id] == true && requesters[id] != null }.first { it }
            withFrameNanos { }
            requesters[id]?.requestFocus()
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(theme.cards.gridMinimumWidthDp.dp * (if (theme.cards.scaleGridWithText) textScale else 1f)), state = state,
        modifier = Modifier.fillMaxSize().testTag("max-collection-page"),
        contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "collection-page-header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Voltar", onBack, Modifier.focusRequester(back).then(entry))
                Text(collection.name + " · " + comics.size, fontSize = 26.sp)
            }
        }
        if (comics.isEmpty()) item(key = "collection-page-empty", span = { GridItemSpan(maxLineSpan) }) {
            Text("Esta coleção ainda não tem edições.", Modifier.testTag("max-collection-empty"), fontSize = 20.sp)
        }
        items(comics, key = { it.id }) { comic ->
            val requester = remember(comic.id) { FocusRequester() }
            DisposableEffect(comic.id) {
                requesters[comic.id] = requester
                onDispose { requesters.remove(comic.id); positioned.remove(comic.id) }
            }
            Card(onClick = { onOpen(comic.id) },
                modifier = Modifier.then(libraryCardKeys({ onOpen(comic.id) }, { onOptions(comic) }))
                    .testTag("max-collection-cover-${comic.id}").focusRequester(requester)
                    .onGloballyPositioned { positioned[comic.id] = true }
                    .onFocusChanged { if (it.isFocused) onFocus(comic.id) },
                shape = CardDefaults.shape(RoundedCornerShape(theme.shapes.controlRadiusDp.dp)),
                colors = CardDefaults.colors(containerColor = TvColors.Panel),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(theme.focus.cardBorderDp.dp, TvColors.FocusAccent))),
                scale = CardDefaults.scale(focusedScale = theme.focus.collectionScale)) {
                CollectionCardContent(comic, repository, comic.id in offlineIds)
            }
        }
    }
}

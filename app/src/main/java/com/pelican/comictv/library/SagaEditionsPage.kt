package com.pelican.comictv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.floor

internal fun sagaEditionTarget(editions: List<ComicEntity>, requested: String?): String? =
    editions.firstOrNull { it.id == requested }?.id
        ?: editions.filter { it.lastRead > 0 }.maxByOrNull { it.lastRead }?.id
        ?: editions.firstOrNull()?.id

internal fun sagaEditionStatus(edition: ComicEntity): String {
    if (edition.lastRead <= 0 && edition.currentPage <= 0) return "Não iniciado"
    val page = (edition.currentPage + 1).coerceIn(1, edition.pageCount.coerceAtLeast(1))
    val state = if (edition.pageCount > 0 && page >= edition.pageCount) "Lido" else "Em leitura"
    return if (edition.pageCount > 0) "$state · Página $page de ${edition.pageCount}" else state
}

internal fun sagaEditionColumns(widthDp: Float, fontScale: Float): Int =
    floor((widthDp - 48f + 24f) / (160f * fontScale.coerceAtLeast(1f) + 24f)).toInt().coerceAtLeast(1)

/** A single scroll owner: the command header leaves the screen along with the covers. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun SagaEditionsPage(
    sagaKey: String, editions: List<ComicEntity>, grid: LazyGridState, focusedId: String?,
    headerFocus: FocusRequester, coverMode: Boolean, coverModel: (String) -> Any?,
    onFocus: (String) -> Unit, onOpen: (ComicEntity) -> Unit, onOptions: (ComicEntity) -> Unit,
    header: @Composable (focusEditions: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable(sagaKey) { mutableStateOf(sagaEditionTarget(editions, focusedId)) }
    val ids = editions.map { it.id }
    val requests = remember(ids) { ids.associateWith { FocusRequester() } }
    suspend fun focusEdition(id: String?) {
        val target = sagaEditionTarget(editions, id)
        if (target == null) { headerFocus.requestFocus(); return }
        val index = editions.indexOfFirst { it.id == target } + 1 // full-span header is item zero
        if (grid.layoutInfo.visibleItemsInfo.none { it.key == target }) grid.scrollToItem(index)
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == target } }.first { it }
        withFrameNanos { }
        requests[target]?.requestFocus()
    }
    // Progress/cover emissions must not move focus. Only re-entry or changing the picker mode does.
    LaunchedEffect(sagaKey, coverMode, ids) { focusEdition(selected) }
    val overflowPx = with(LocalDensity.current) { 24.dp.toPx() }
    val bringIntoView = remember(overflowPx) { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
            offset < overflowPx -> offset - overflowPx
            offset + size > containerSize - overflowPx -> offset + size - containerSize + overflowPx
            else -> 0f
        }
    } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = sagaEditionColumns(maxWidth.value, LocalDensity.current.fontScale)
        LazyVerticalGrid(GridCells.Fixed(columns), state = grid,
            modifier = Modifier.fillMaxSize().testTag("saga-editions-grid"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item(key = "saga-header", span = { GridItemSpan(maxLineSpan) }) {
                header { scope.launch { focusEdition(selected) } }
            }
            itemsIndexed(editions, key = { _, edition -> edition.id }) { index, edition ->
                val open = { onOpen(edition) }
                Card(onClick = open,
                    modifier = Modifier.fillMaxWidth().focusRaised().focusRequester(requests.getValue(edition.id))
                        .testTag("saga-edition-${edition.id}")
                        .onFocusChanged { if (it.isFocused) { selected = edition.id; onFocus(edition.id) } }
                        .onPreviewKeyEvent { event ->
                            if (index < columns && event.key == Key.DirectionUp) {
                                if (event.type == KeyEventType.KeyDown) scope.launch {
                                    grid.scrollToItem(0)
                                    snapshotFlow { grid.layoutInfo.visibleItemsInfo.any { it.key == "saga-header" } }.first { it }
                                    withFrameNanos { }; headerFocus.requestFocus()
                                }
                                true
                            } else false
                        }
                        .focusProperties {
                            if (index % columns == 0) left = FocusRequester.Cancel
                            if (index % columns == columns - 1 || index == editions.lastIndex) right = FocusRequester.Cancel
                            if (index + columns > editions.lastIndex) down = FocusRequester.Cancel
                        }
                        .then(if (coverMode) Modifier else libraryCardKeys(open, { onOptions(edition) })),
                    scale = CardDefaults.scale(focusedScale = 1.04f),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusAccent))),
                    colors = CardDefaults.colors(containerColor = TvColors.Panel, focusedContainerColor = TvColors.Panel)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var failed by remember(edition.id) { mutableStateOf(false) }
                        Box(Modifier.fillMaxWidth().height(210.dp).background(TvColors.Background), contentAlignment = Alignment.Center) {
                            if (failed) Text("Capa indisponível", color = TvColors.Muted, fontSize = 14.sp)
                            else AsyncImage(coverModel(edition.id), "Capa de ${edition.title}", Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit, onError = { failed = true })
                        }
                        Text(edition.title, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, minLines = 3, maxLines = 3,
                            overflow = TextOverflow.Ellipsis)
                        Text(sagaEditionStatus(edition), color = TvColors.Muted, fontSize = 14.sp, minLines = 2, maxLines = 2)
                        if (edition.lastRead > 0 || edition.currentPage > 0)
                            ProgressTrack(readingFraction(edition), Modifier.fillMaxWidth())
                        if (coverMode) Text("OK · Usar esta capa", color = TvColors.FocusAccent, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    }
}

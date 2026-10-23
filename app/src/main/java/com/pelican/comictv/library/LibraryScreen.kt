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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first

@Composable fun LibraryScreen(
    comics: List<ComicEntity>, repository: LibraryRepository, focusedId: String?,
    onFocus: (String) -> Unit, onOpen: (String) -> Unit, onImport: () -> Unit,
) {
    val grid = rememberLazyGridState()
    val restore = remember { FocusRequester() }
    var history by rememberSaveable { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var positioned by remember { mutableStateOf<String?>(null) }
    val recent = comics.filter { it.lastRead > 0 }.sortedByDescending { it.lastRead }
    val visible = if (history) recent else comics
    // Capture the entry target once: database emissions must not take focus from navigation.
    val entryId = remember { focusedId }
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(comics, history) {
        if (restored || entryId == null) return@LaunchedEffect
        val index = visible.indexOfFirst { it.id == entryId }
        if (index < 0) return@LaunchedEffect
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sua biblioteca", fontSize = 26.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Biblioteca · ${comics.size}", { history = false }, primary = !history)
                    TvButton("Histórico · ${recent.size}", { history = true }, primary = history)
                }
            }
        }
        if (!history && recent.isNotEmpty()) {
            item(key = "recent-heading", span = { GridItemSpan(maxLineSpan) }) {
                Text("Continuar lendo · ${recent.size} no histórico →", fontSize = 19.sp)
            }
            item(key = "recent-row", span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(recent, key = { it.id }) { comic ->
                        Card(
                            onClick = { onFocus(comic.id); onOpen(comic.id) },
                            modifier = Modifier.width(280.dp).onFocusChanged { if (it.isFocused) onFocus(comic.id) },
                            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber))),
                            colors = CardDefaults.colors(containerColor = TvColors.Panel),
                        ) {
                            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AsyncImage(repository.cover(comic.id), null, Modifier.width(40.dp).height(60.dp), contentScale = ContentScale.Fit)
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
            Text(if (history) "Todas as leituras · mais recentes primeiro" else "Sua coleção", fontSize = 19.sp)
        }
        if (visible.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (history) "Nenhuma leitura no histórico" else "Sua coleção começa aqui", fontSize = 24.sp)
                    Text(
                        if (history) "Abra uma edição para encontrá-la aqui, inclusive depois de terminar."
                        else "Importe CBZ, ZIP, CBR, RAR, PDF ou EPUB para começar.",
                        color = TvColors.Muted, fontSize = 17.sp,
                    )
                    if (history) TvButton("Ver biblioteca", { history = false })
                    else TvButton("Importar quadrinhos", onImport, primary = true)
                }
            }
        }
        items(visible, key = { it.id }) { comic ->
            val restoreModifier = if (target == comic.id) Modifier.focusRequester(restore)
                .onGloballyPositioned { positioned = comic.id } else Modifier
            Card(
                onClick = { onFocus(comic.id); onOpen(comic.id) },
                modifier = restoreModifier.onFocusChanged { if (it.isFocused) onFocus(comic.id) },
                scale = CardDefaults.scale(focusedScale = 1.03f),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber))),
                colors = CardDefaults.colors(containerColor = TvColors.Panel),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(TvColors.Panel)) {
                    AsyncImage(repository.cover(comic.id), comic.title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
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

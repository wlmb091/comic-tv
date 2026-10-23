package com.pelican.comictv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
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
    val empty = remember { FocusRequester() }
    var target by remember { mutableStateOf<String?>(null) }
    var positioned by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(comics.isNotEmpty()) {
        if (comics.isEmpty()) { empty.requestFocus(); return@LaunchedEffect }
        val index = comics.indexOfFirst { it.id == focusedId }.coerceAtLeast(0)
        target = comics[index].id
        grid.scrollToItem(index)
        snapshotFlow { positioned == target }.first { it }
        restore.requestFocus()
        target = null
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScreenTitle("Sua biblioteca", if (comics.isEmpty()) "Um lugar para todas as suas histórias." else "${comics.size} edições · escolha sua próxima leitura")
        if (comics.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                Text("Sua coleção começa aqui", fontSize = 28.sp)
                Spacer(Modifier.height(14.dp))
                Text("Importe CBZ, ZIP, CBR, RAR, PDF ou EPUB para começar.\nSua leitura será salva automaticamente.", color = TvColors.Muted, fontSize = 18.sp, lineHeight = 28.sp)
                Spacer(Modifier.height(28.dp))
                TvButton("Importar quadrinhos", onImport, Modifier.focusRequester(empty), primary = true)
            }
        } else {
            val recent = comics.filter { it.lastRead > 0 }.sortedByDescending { it.lastRead }.take(2)
            if (recent.isNotEmpty()) {
                Text("Continuar lendo", fontSize = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    recent.forEach { comic ->
                        Card(onClick = { onFocus(comic.id); onOpen(comic.id) }, modifier = Modifier.weight(1f),
                            colors = CardDefaults.colors(containerColor = TvColors.Panel)) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                AsyncImage(repository.cover(comic.id), null, Modifier.width(48.dp).height(68.dp), contentScale = ContentScale.Crop)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(comic.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                                    Text("Página ${comic.currentPage + 1} de ${comic.pageCount}", color = TvColors.Muted, fontSize = 13.sp)
                                    ProgressTrack((comic.currentPage + 1f) / comic.pageCount, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
            Text("Sua coleção", fontSize = 21.sp)
            LazyVerticalGrid(columns = GridCells.Fixed(4), state = grid,
                modifier = Modifier.weight(1f).focusRestorer(), contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                items(comics, key = { it.id }) { comic ->
                    val restoreModifier = if (target == comic.id) Modifier.focusRequester(restore).onGloballyPositioned { positioned = comic.id } else Modifier
                    Card(onClick = { onFocus(comic.id); onOpen(comic.id) }, modifier = restoreModifier
                        .onFocusChanged { if (it.isFocused) onFocus(comic.id) },
                        scale = CardDefaults.scale(focusedScale = 1.04f),
                        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, TvColors.Amber))),
                        colors = CardDefaults.colors(containerColor = TvColors.Panel)) {
                        Column {
                            AsyncImage(repository.cover(comic.id), comic.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                                .background(TvColors.Panel), contentScale = ContentScale.Fit)
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(comic.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
                                if (comic.lastRead > 0) ProgressTrack((comic.currentPage + 1f) / comic.pageCount, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

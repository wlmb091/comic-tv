package com.pelican.comictv.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.*
import java.io.File
import kotlinx.coroutines.launch

@Composable fun OnlineEditionsScreen(
    viewModel: OnlineEditionsViewModel,
    onRead: (String) -> Unit,
    onBack: () -> Unit,
    previewFile: File? = null,
    initialStep: Int = 0,
    fallbackCover: File? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    val current = remember { FocusRequester() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var locate by remember { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var initialFocus by remember { mutableStateOf(false) }
    val context = state.context
    val sequence = remember(context, state.reversed) {
        context?.let { chapterSequence(it.chapters, it.current.key, state.reversed) }
    }
    val exit = { viewModel.leave(); onBack() }
    val read: (String) -> Unit = { destination -> viewModel.leave(); onRead(destination) }
    BackHandler { exit() }
    LaunchedEffect(context, state.busy, initialStep, attempted) {
        if (!attempted && context != null && !state.busy && initialStep != 0) {
            attempted = true
            viewModel.openNeighbor(initialStep, read)
        }
    }
    Row(Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            Modifier.width(300.dp).fillMaxHeight().background(TvColors.Background),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CAPÍTULOS", color = TvColors.Amber, fontSize = 13.sp)
                    Text(context?.title?.title ?: "Edições online", fontSize = 23.sp, maxLines = 3)
                    TvButton("Voltar à leitura", exit, Modifier.fillMaxWidth().focusRequester(first)
                        .onGloballyPositioned {
                            if (!initialFocus) { initialFocus = true; first.requestFocus() }
                        })
                    if (state.busy) Text("Consultando a fonte…", color = TvColors.Amber, fontSize = 15.sp)
                    state.message?.let { Text(it, fontSize = 15.sp, color = TvColors.Amber) }
                    if (context != null && sequence != null) {
                        Text("Atual: ${context.current.title}", fontSize = 16.sp, maxLines = 3)
                        Text(
                            if (sequence.index < 0) "A edição atual não aparece na lista atualizada. Escolha abaixo."
                            else "${sequence.index + 1} de ${sequence.chapters.size} capítulos",
                            color = TvColors.Muted, fontSize = 14.sp,
                        )
                        if (!state.busy) {
                            TvButton(if (state.reversed) "Ordem invertida ⇅" else "Ordem da fonte ⇅",
                                viewModel::toggleOrder, Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                sequence.previous?.let { chapter ->
                                    TvButton("Anterior", { viewModel.open(chapter, read) }, Modifier.weight(1f))
                                }
                                sequence.next?.let { chapter ->
                                    TvButton("Próximo", { viewModel.open(chapter, read) }, Modifier.weight(1f))
                                }
                            }
                            if (sequence.index >= 0) TvButton("Localizar atual", {
                                locate = true
                                scope.launch { list.scrollToItem(sequence.index + 1) }
                            }, Modifier.fillMaxWidth())
                        }
                        Text("A sequência segue a ordem selecionada na fonte.", color = TvColors.Muted, fontSize = 13.sp)
                    }
                    if (!state.busy) TvButton("Atualizar lista", viewModel::retry, Modifier.fillMaxWidth())
                }
            }
            if (context != null && sequence != null) {
                itemsIndexed(sequence.chapters, key = { _, chapter -> chapter.key }) { index, chapter ->
                    val selected = chapter.key == context.current.key
                    val modifier = if (selected) Modifier.focusRequester(current).onGloballyPositioned {
                        if (locate) { locate = false; current.requestFocus() }
                    } else Modifier
                    TvButton("${index + 1}. ${chapter.title}${if (selected) " · ATUAL" else ""}",
                        { if (!state.busy) viewModel.open(chapter, read) }, modifier.fillMaxWidth(), primary = selected)
                    LaunchedEffect(locate) {
                        if (selected && locate) { locate = false; current.requestFocus() }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val preview = previewFile ?: fallbackCover
            if (preview != null) {
                AsyncImage(preview, "Página atual da leitura", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text(context?.title?.title ?: "Sua leitura", Modifier.padding(32.dp), color = TvColors.Muted, fontSize = 24.sp)
            }
        }
    }
}

package com.pelican.comictv.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*
import kotlinx.coroutines.launch

@Composable fun OnlineEditionsScreen(viewModel: OnlineEditionsViewModel, onRead: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    val current = remember { FocusRequester() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var locate by remember { mutableStateOf(false) }
    val context = state.context
    val sequence = remember(context, state.reversed) {
        context?.let { chapterSequence(it.chapters, it.current.key, state.reversed) }
    }
    val exit = { viewModel.leave(); onBack() }
    val read: (String) -> Unit = { destination -> viewModel.leave(); onRead(destination) }
    BackHandler { exit() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle(context?.title?.title ?: "Edições online", "Continue a saga ou série sem buscar o título novamente.")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton("Voltar", exit, Modifier.focusRequester(first))
            if (!state.busy) TvButton("Atualizar lista", viewModel::retry)
            if (context != null && !state.busy) TvButton(if (state.reversed) "Ordem invertida · alternar" else "Ordem da fonte · alternar", viewModel::toggleOrder)
        }
        if (state.busy) Text("Consultando a fonte…", color = TvColors.Amber, fontSize = 18.sp)
        state.message?.let { Text(it, fontSize = 17.sp, color = TvColors.Amber, maxLines = 2) }
        if (context != null && sequence != null) {
            Text("Atual: ${context.current.title}", fontSize = 20.sp, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!state.busy) {
                    sequence.previous?.let { chapter -> TvButton("Edição anterior", { viewModel.open(chapter, read) }) }
                    sequence.next?.let { chapter -> TvButton("Próxima edição", { viewModel.open(chapter, read) }, primary = true) }
                    if (sequence.index >= 0) TvButton("Localizar atual", {
                        locate = true
                        scope.launch { list.scrollToItem(sequence.index) }
                    })
                }
            }
            Text(if (sequence.index < 0) "A edição atual não aparece na lista atualizada. Escolha outra abaixo."
                else "${sequence.index + 1} de ${sequence.chapters.size} · A sequência segue a ordem selecionada, sem inferir cronologia.",
                color = TvColors.Muted, fontSize = 15.sp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(sequence.chapters, key = { _, chapter -> chapter.key }) { index, chapter ->
                    val selected = chapter.key == context.current.key
                    val modifier = if (selected) Modifier.focusRequester(current).onGloballyPositioned {
                        if (locate) { locate = false; current.requestFocus() }
                    } else Modifier
                    TvButton("${index + 1}. ${chapter.title}${if (selected) " · ATUAL" else ""}",
                        { if (!state.busy) viewModel.open(chapter, read) }, modifier.fillMaxWidth(), primary = selected)
                }
            }
        }
    }
}

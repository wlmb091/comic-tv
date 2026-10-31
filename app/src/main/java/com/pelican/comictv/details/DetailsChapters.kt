package com.pelican.comictv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.library.*
import com.pelican.comictv.sources.*
import com.pelican.comictv.ui.*
import kotlinx.coroutines.*

/** Owned by the visible Details lifecycle, not a retained ViewModel that can outlive the reader. */
internal class DetailsChapterController {
    var context by mutableStateOf<OnlineEditions?>(null)
    var order by mutableStateOf(ChapterOrder.Ascending)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    private var task: Job? = null
    var scope: CoroutineScope? = null
    fun run(block: suspend () -> Unit) {
        val owner = scope ?: return
        val previous = task
        previous?.cancel()
        task = owner.launch {
            previous?.join()
            busy = true; message = null
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Não foi possível consultar os capítulos. Tente novamente." }
            finally { busy = false }
        }
    }
    suspend fun stop() { task?.cancelAndJoin(); task = null }
}

@Composable internal fun DetailsChapterPanel(
    id: String, rows: List<DetailChapter>, state: DetailsChapterController, online: Boolean,
    interacted: Boolean, restoreId: String?, onRestored: () -> Unit,
    onRetry: () -> Unit, onOrder: (ChapterOrder) -> Unit, onRead: (DetailChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val target = restoreId?.takeIf { restored -> rows.any { it.id == restored } } ?: chapterResumeId(rows, id)
    val focus = remember(id) { FocusRequester() }
    var initialAttempted by rememberSaveable(id) { mutableStateOf(false) }
    var pending by remember(id) { mutableStateOf<String?>(null) }
    // Details open on Continue reading; the list only scrolls to the resume row without taking focus.
    LaunchedEffect(rows.map { it.id }, target, state.busy, restoreId) {
        if (!state.busy && target != null && (restoreId != null || !initialAttempted)) {
            initialAttempted = true
            if (restoreId != null) pending = target
            list.scrollToItem(rows.indexOfFirst { it.id == target } + 1)
        }
    }
    LazyColumn(modifier.background(TvColors.Panel), state = list, contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "chapter-controls") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Capítulos / edições", fontSize = 21.sp)
                Text(if (online && state.context != null) "${rows.size} capítulos na fonte" else "${rows.size} ${if (rows.size == 1) "edição" else "edições"} na sua saga · somente arquivos conhecidos",
                    fontSize = 14.sp, color = TvColors.Muted)
                if (state.busy) Text("Consultando a fonte…", color = TvColors.Amber, fontSize = 15.sp)
                state.message?.let { Text(it, color = TvColors.Amber, fontSize = 15.sp) }
                if (online && !state.busy) {
                    if (state.context != null) ChapterOrderControls(state.order, onOrder)
                    TvButton(if (state.message != null) "Tentar novamente" else "Atualizar capítulos", onRetry)
                }
                if (online && state.context != null && rows.none { it.id == id })
                    Text("A edição atual não aparece na lista da fonte. Você ainda pode retomá-la à esquerda.", color = TvColors.Muted, fontSize = 14.sp)
                if (rows.isEmpty() && !state.busy) Text("Nenhum capítulo disponível nesta lista.", color = TvColors.Muted, fontSize = 15.sp)
            }
        }
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            val isTarget = row.id == target
            TvButton("${index + 1}. ${row.title} · ${chapterReadStatus(row.progress)}${if (row.id == id) " · ATUAL" else ""}",
                { if (!state.busy) onRead(row) }, Modifier.fillMaxWidth()
                    .then(if (isTarget) Modifier.focusRequester(focus) else Modifier)
                    .onGloballyPositioned {
                        if (pending == row.id) {
                            pending = null
                            focus.requestFocus()
                            onRestored()
                        }
                    }, primary = row.id == id)
            LaunchedEffect(pending, interacted) {
                if (isTarget && pending == row.id) {
                    pending = null
                    withFrameNanos { }; focus.requestFocus()
                    onRestored()
                }
            }
        }
    }
}

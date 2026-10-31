package com.pelican.comictv.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvColors
import java.util.Locale
import kotlinx.coroutines.flow.first

internal fun browserDownloadProgress(item: BrowserDownloadItem): String {
    fun mib(bytes: Long) = String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0)
    return if (item.total > 0) "${(item.bytes * 100 / item.total).coerceIn(0, 100)}% · ${mib(item.bytes)} / ${mib(item.total)}"
        else "${mib(item.bytes)} · tamanho total não informado"
}
internal data class BrowserDownloadAction(val key: String, val label: String, val command: String?)

/** Primary stays in the same keyed slot, including while importing; never disable a focused action. */
internal fun browserDownloadActions(row: BrowserDownloadItem, exportBusy: Boolean): List<BrowserDownloadAction> {
    val primary = when (row.status) {
        BrowserDownloadStatus.Queued, BrowserDownloadStatus.Running -> "Pausar" to "pause"
        BrowserDownloadStatus.Paused, BrowserDownloadStatus.Failed -> "Retomar / tentar novamente" to "resume"
        BrowserDownloadStatus.Importing -> "Importando…" to null
        BrowserDownloadStatus.Canceled -> "Baixar novamente" to "restart"
        BrowserDownloadStatus.Complete, BrowserDownloadStatus.Imported -> when {
            row.importedId != null -> "Ler agora" to "read"
            BrowserDownloadNames.importable(row.filename) -> "Importar para biblioteca" to "import"
            else -> (if (exportBusy) "Salvando…" else "Salvar em…") to if (exportBusy) null else "save"
        }
    }
    return buildList {
        add(BrowserDownloadAction("primary", primary.first, primary.second))
        when (row.status) {
            BrowserDownloadStatus.Queued, BrowserDownloadStatus.Running -> add(BrowserDownloadAction("cancel", "Cancelar", "cancel"))
            BrowserDownloadStatus.Paused, BrowserDownloadStatus.Failed -> {
                add(BrowserDownloadAction("restart", "Recomeçar do zero", "restart"))
                add(BrowserDownloadAction("cancel", "Cancelar", "cancel"))
            }
            BrowserDownloadStatus.Complete, BrowserDownloadStatus.Imported -> {
                if (row.importedId != null) add(BrowserDownloadAction("details", "Ver detalhes", "details"))
                if (row.importedId != null || BrowserDownloadNames.importable(row.filename))
                    add(BrowserDownloadAction("save", if (exportBusy) "Salvando…" else "Salvar em…", if (exportBusy) null else "save"))
            }
            else -> Unit
        }
        if (row.status != BrowserDownloadStatus.Importing) add(BrowserDownloadAction("remove", "Excluir download", "remove"))
    }
}

@Composable internal fun BrowserDownloadsPanel(rows: List<BrowserDownloadItem>, exportBusy: Boolean,
    onBack: () -> Unit, message: String = "", interactive: Boolean = true, action: (String, String) -> Unit) {
    val backFocus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastFocus by rememberSaveable { mutableStateOf("back") }
    var entered by remember { mutableStateOf(false) }
    val ordered = rows.asReversed()
    val available = ordered.flatMap { row -> browserDownloadActions(row, exportBusy).map { "${row.id}/${it.key}" } }.toSet()
    suspend fun restore() {
        val rowId = lastFocus.substringBefore('/')
        val index = ordered.indexOfFirst { it.id == rowId }
        if (lastFocus == "back" || index < 0) { backFocus.requestFocus(); return }
        val target = lastFocus.takeIf { it in available } ?: "$rowId/primary"
        if (list.layoutInfo.visibleItemsInfo.none { it.key == rowId }) list.scrollToItem(index)
        snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == rowId } }.first { it }
        withFrameNanos { }
        requesters[target]?.requestFocus() ?: backFocus.requestFocus()
    }
    LaunchedEffect(interactive) { if (interactive) { withFrameNanos { }; restore(); entered = true } }
    LaunchedEffect(available, interactive) {
        if (entered && interactive && lastFocus != "back" && lastFocus !in available) restore()
        requesters.keys.retainAll(available)
    }
    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 32.dp, vertical = 24.dp)
        .focusProperties { onExit = {
            if (interactive && requestedFocusDirection in listOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right)) cancelFocusChange()
        } }.focusGroup()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Downloads", fontSize = 28.sp)
            TvButton("Voltar ao navegador", onBack, Modifier.focusRequester(backFocus).onFocusChanged { if (it.isFocused) lastFocus = "back" })
        }
        Text(if (exportBusy) "Salvando cópia do arquivo…" else "Arquivos concluídos são mantidos. Downloads pausam fora do primeiro plano.", color = TvColors.Muted, fontSize = 14.sp)
        if (message.isNotBlank()) Text(message, color = TvColors.Muted, fontSize = 14.sp, maxLines = 2)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (rows.isEmpty()) item { Text("Nenhum download ainda. Abra um site e escolha o arquivo que deseja baixar.", fontSize = 19.sp) }
            items(ordered, key = { it.id }) { row ->
                // Lighter than the background so theme-colored buttons stay distinct on the card.
                Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .10f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.filename, fontSize = 20.sp, maxLines = 2)
                    Text("${row.status.label} · ${browserDownloadProgress(row)}", fontSize = 15.sp)
                    if (row.total > 0) Box(Modifier.fillMaxWidth().height(5.dp).background(TvColors.Track)) {
                        Box(Modifier.fillMaxWidth((row.bytes.toFloat() / row.total).coerceIn(0f, 1f)).fillMaxHeight().background(TvColors.FocusAccent))
                    }
                    row.error?.let { Text(it, fontSize = 14.sp, color = TvColors.Muted) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        browserDownloadActions(row, exportBusy).forEach { button -> key(button.key) {
                            val id = "${row.id}/${button.key}"
                            val requester = remember(id) { requesters.getOrPut(id) { FocusRequester() } }
                            TvButton(button.label, { button.command?.let { action(it, row.id) } },
                                Modifier.focusRequester(requester).testTag("download-action-$id").onFocusChanged { if (it.isFocused) lastFocus = id })
                        } }
                    }
                }
            }
        }
    }
}

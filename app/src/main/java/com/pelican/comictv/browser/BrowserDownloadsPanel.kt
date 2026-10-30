package com.pelican.comictv.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvColors
import java.util.Locale

internal fun browserDownloadProgress(item: BrowserDownloadItem): String {
    fun mib(bytes: Long) = String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0)
    return if (item.total > 0) "${(item.bytes * 100 / item.total).coerceIn(0, 100)}% · ${mib(item.bytes)} / ${mib(item.total)}"
        else "${mib(item.bytes)} · tamanho total não informado"
}
@Composable internal fun BrowserDownloadsPanel(rows: List<BrowserDownloadItem>, exportBusy: Boolean,
    onBack: () -> Unit, message: String = "", action: (String, String) -> Unit) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }
    Column(Modifier.fillMaxSize().background(Color(0xFF10151D)).padding(horizontal = 32.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Downloads", fontSize = 28.sp)
            TvButton("Voltar ao navegador", onBack, Modifier.focusRequester(backFocus))
        }
        Text(if (exportBusy) "Salvando cópia do arquivo…" else "Arquivos concluídos são mantidos. Downloads pausam fora do primeiro plano.", color = TvColors.Muted, fontSize = 14.sp)
        if (message.isNotBlank()) Text(message, color = TvColors.Muted, fontSize = 14.sp, maxLines = 2)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (rows.isEmpty()) item { Text("Nenhum download ainda. Abra um site e escolha o arquivo que deseja baixar.", fontSize = 19.sp) }
            items(rows.asReversed(), key = { it.id }) { row ->
                Column(Modifier.fillMaxWidth().background(Color(0xFF202A37)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.filename, fontSize = 20.sp, maxLines = 2)
                    Text("${row.status.label} · ${browserDownloadProgress(row)}", fontSize = 15.sp)
                    if (row.total > 0) Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF445064))) {
                        Box(Modifier.fillMaxWidth((row.bytes.toFloat() / row.total).coerceIn(0f, 1f)).fillMaxHeight().background(TvColors.FocusAccent))
                    }
                    row.error?.let { Text(it, fontSize = 14.sp, color = TvColors.Muted) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (row.status) {
                            BrowserDownloadStatus.Queued, BrowserDownloadStatus.Running -> {
                                TvButton("Pausar", { action("pause", row.id) }); TvButton("Cancelar", { action("cancel", row.id) })
                            }
                            BrowserDownloadStatus.Paused, BrowserDownloadStatus.Failed -> {
                                TvButton("Retomar / tentar novamente", { action("resume", row.id) }); TvButton("Recomeçar do zero", { action("restart", row.id) })
                                TvButton("Cancelar", { action("cancel", row.id) })
                            }
                            BrowserDownloadStatus.Complete, BrowserDownloadStatus.Imported -> {
                                if (BrowserDownloadNames.importable(row.filename)) TvButton("Importar para biblioteca", { action("import", row.id) })
                                row.importedId?.let { TvButton("Ler agora", { action("read", row.id) }); TvButton("Ver detalhes", { action("details", row.id) }) }
                                TvButton("Salvar em…", { action("save", row.id) }, enabled = !exportBusy)
                            }
                            BrowserDownloadStatus.Importing -> Text("Importando e conferindo o arquivo…")
                            BrowserDownloadStatus.Canceled -> TvButton("Baixar novamente", { action("restart", row.id) })
                        }
                        if (row.status != BrowserDownloadStatus.Importing) TvButton("Excluir download", { action("remove", row.id) })
                    }
                }
            }
        }
    }
}

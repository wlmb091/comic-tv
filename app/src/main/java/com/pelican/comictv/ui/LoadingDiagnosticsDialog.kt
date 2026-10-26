package com.pelican.comictv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.pelican.comictv.performance.*

/** The report owns focus; actions stay outside scrolling text so neither end becomes unreachable. */
@Composable internal fun LoadingDiagnosticsDialog(onDismiss: () -> Unit, metrics: LoadMetrics = AppTimings,
    heapSnapshot: () -> Pair<Long, Long> = { Runtime.getRuntime().let {
        (it.totalMemory() - it.freeMemory()) / (1024 * 1024) to it.maxMemory() / (1024 * 1024)
    } }) {
    var revision by remember { mutableIntStateOf(0) }
    val rows = remember(revision) { loadDiagnosticRows(metrics.snapshot()) }
    val heap = remember(revision) { heapSnapshot() }
    val list = rememberLazyListState()
    val reportFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    var reportFocused by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(.92f).heightIn(max = 520.dp)
            .background(TvColors.Background, RoundedCornerShape(12.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Diagnóstico de carregamento", fontSize = 24.sp)
            Text("↑ ↓ Rolar informações · → Ir aos botões", color = TvColors.Amber, fontSize = 16.sp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth()
                .border(if (reportFocused) 2.dp else 1.dp, if (reportFocused) TvColors.Amber else TvColors.Muted)
                .focusRequester(reportFocus).onFocusChanged { reportFocused = it.hasFocus }
                .focusProperties { up = FocusRequester.Cancel; left = FocusRequester.Cancel; right = refreshFocus; down = refreshFocus }
                .semantics { contentDescription = "Informações do diagnóstico" }.textPanelScroll(list),
                state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(rows, key = { it.stage }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.stage.label, fontSize = 18.sp)
                        Text(row.detail, fontSize = 16.sp, color = TvColors.Muted)
                    }
                }
                item(key = "resources") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Heap Java: ${heap.first} MiB usados · limite ${heap.second} MiB. Não inclui toda a memória de imagens/GPU.", color = TvColors.Muted, fontSize = 16.sp)
                        Text("Cache de páginas: até 256 MiB · miniaturas: até 32 MiB. Consulte o uso em Armazenamento.", color = TvColors.Muted, fontSize = 16.sp)
                        Text("Medições desta execução. Etapas podem se sobrepor; não some as médias. Exibição mede até o retorno do leitor, não FPS. Nenhum título ou endereço é registrado.", color = TvColors.Muted, fontSize = 16.sp)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton("Atualizar medições", { revision++ }, Modifier.focusRequester(refreshFocus).focusProperties { up = reportFocus })
                TvButton("Zerar medições", { metrics.clear(); revision++ }, Modifier.focusProperties { up = reportFocus })
                TvButton("Fechar", onDismiss, Modifier.focusProperties { up = reportFocus })
            }
        }
        LaunchedEffect(Unit) { reportFocus.requestFocus() }
    }
}

package com.pelican.comictv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*

@Composable internal fun FullSummaryDialog(summary: String, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    val scroll = rememberLazyListState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(.88f).fillMaxHeight(.88f).background(TvColors.Background).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Resumo completo", fontSize = 26.sp)
            TvButton("Voltar aos detalhes", onDismiss, Modifier.focusRequester(close))
            LazyColumn(Modifier.fillMaxWidth().weight(1f).textPanelScroll(scroll), state = scroll) {
                items(summary.split("\n\n")) { paragraph ->
                    Text(paragraph, Modifier.padding(bottom = 12.dp), fontSize = 20.sp, lineHeight = 31.sp, color = TvColors.Muted)
                }
            }
        }
    }
    LaunchedEffect(Unit) { close.requestFocus() }
}

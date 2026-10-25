package com.pelican.comictv.sources

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*

/** Collapsing editing never clears the active filter; focus alone never opens editing or IME. */
@Composable internal fun SourceSearchControls(query: String, onQuery: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    val button = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var buttonFocused by remember { mutableStateOf(false) }
    fun close() { keyboard?.hide(); button.requestFocus(); expanded = false }
    BackHandler(expanded) { close() }
    LaunchedEffect(expanded) {
        if (expanded) { withFrameNanos { }; if (buttonFocused) field.requestFocus() }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton(if (expanded) "Fechar busca" else "Buscar", {
                if (expanded) close() else { button.requestFocus(); expanded = true }
            }, Modifier.focusRequester(button).onFocusChanged { buttonFocused = it.isFocused })
            if (query.isNotBlank()) TvButton("Limpar busca", { button.requestFocus(); onQuery("") })
            extra()
        }
        if (query.isNotBlank() && !expanded) Text("Busca ativa: ${query.take(24)}", color = TvColors.Amber,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (expanded) SourceInput(query, onQuery, label, Modifier.fillMaxWidth().focusRequester(field))
    }
}

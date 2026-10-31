package com.pelican.comictv.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.pelican.comictv.ui.*

/** Lifts controls off the theme surface in every preset, including high contrast. */
private val BrowserControlSurface = Color.White.copy(alpha = .12f)

@Composable internal fun BrowserChrome(state: BrowserSessionState, address: String, onAddress: (String) -> Unit,
    addressFocus: FocusRequester, canBack: Boolean, canForward: Boolean, downloads: Int, progress: Int,
    message: String, action: (String) -> Unit, homeFocus: FocusRequester? = null) {
    var caption by remember { mutableStateOf("MENU abre ou fecha os controles · Segure OK sobre um link para opções") }
    val surface = if (TvColors.options.highContrast) Color.Black else TvColors.ButtonSurface.copy(alpha = .95f)
    var addressFocused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.fillMaxWidth().background(surface).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("COMIC TV", fontSize = 15.sp, color = TvColors.FocusAccent)
                TvTextInput(address, onAddress, Modifier.weight(1f).focusRequester(addressFocus)
                    .onFocusChanged { addressFocused = it.isFocused; if (it.isFocused) caption = "Endereço · OK abre o teclado" }
                    .border(3.dp, if (addressFocused) TvColors.FocusAccent else Color.Transparent)
                    .background(BrowserControlSurface).padding(10.dp), textStyle = TextStyle(color = Color.White, fontSize = 19.sp),
                    maxLength = 4096, onSubmit = { action("go") })
                BrowserIcon("⌕", "Acessar endereço ou pesquisar", { action("go") }, onFocus = { caption = it })
                BrowserIcon("mic", "Pesquisa por voz", { action("voice") }, onFocus = { caption = it })
                BrowserIcon(if (state.bookmarks.any { it.url == state.tabs.firstOrNull { tab -> tab.id == state.selectedId }?.url }) "★" else "☆",
                    "Adicionar ou remover favorito", { action("bookmark") }, onFocus = { caption = it })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp)
                .focusProperties { if (homeFocus != null) down = homeFocus }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tabs.forEachIndexed { index, tab ->
                    Button(onClick = { action("tab:${tab.id}") }, modifier = Modifier.width(155.dp).heightIn(min = 42.dp)
                        .semantics { contentDescription = "Aba ${index + 1}: ${tab.title}" }.onFocusChanged { if (it.isFocused) caption = "Aba ${index + 1}: ${tab.title}" },
                        colors = ButtonDefaults.colors(containerColor = if (tab.id == state.selectedId) TvColors.Amber.copy(alpha = .28f) else BrowserControlSurface,
                            contentColor = if (tab.id == state.selectedId) TvColors.FocusAccent else Color.White,
                            focusedContainerColor = TvColors.FocusAccent, focusedContentColor = TvColors.OnFocusAccent),
                        scale = ButtonDefaults.scale(focusedScale = 1.02f)) {
                        Text("${index + 1} · ${tab.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                    }
                }
                BrowserIcon("+", "Nova aba", { action("new") }, onFocus = { caption = it })
            }
            if (progress in 1..99) Box(Modifier.fillMaxWidth().height(3.dp).background(TvColors.Track)) {
                Box(Modifier.fillMaxWidth(progress / 100f).fillMaxHeight().background(TvColors.FocusAccent))
            }
        }
        Column(Modifier.fillMaxWidth().background(surface).padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (message.isNotBlank()) Text(message, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(caption, color = TvColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(6.dp)
                .focusProperties { if (homeFocus != null) up = homeFocus }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrowserIcon("×", "Fechar aba atual", { action("close-tab") }, onFocus = { caption = it })
                BrowserIcon("←", "Voltar página", { action("back") }, canBack, { caption = it })
                BrowserIcon("→", "Avançar página", { action("forward") }, canForward, { caption = it })
                BrowserIcon(if (progress in 1..99) "■" else "↻", "Recarregar ou parar página", { action("reload") }, onFocus = { caption = it })
                BrowserIcon("⌂", "Página inicial", { action("home") }, onFocus = { caption = it })
                BrowserIcon("↓", "Downloads${if (downloads > 0) ": $downloads em andamento" else ""}", { action("downloads") }, onFocus = { caption = it })
                BrowserIcon("⋮", "Mais opções do navegador", { action("more") }, onFocus = { caption = it })
                BrowserIcon("▶", "Voltar à página", { action("page") }, enabled = true, onFocus = { caption = it })
                BrowserIcon("exit", "Sair do navegador", { action("exit") }, onFocus = { caption = it })
            }
        }
    }
}

@Composable internal fun BrowserIcon(symbol: String, label: String, click: () -> Unit, enabled: Boolean = true, onFocus: (String) -> Unit = {}) {
    // Unavailable actions stay reachable and visibly focused; a press there does nothing.
    Button(onClick = { if (enabled) click() }, modifier = Modifier.size(48.dp).semantics { contentDescription = label }
        .onFocusChanged { if (it.isFocused) onFocus(label) }, contentPadding = PaddingValues(4.dp),
        colors = ButtonDefaults.colors(containerColor = BrowserControlSurface, contentColor = if (enabled) Color.White else Color.White.copy(alpha = .4f),
            focusedContainerColor = TvColors.FocusAccent, focusedContentColor = TvColors.OnFocusAccent),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusAccent))),
        scale = ButtonDefaults.scale(focusedScale = 1.04f)) {
        if (symbol == "mic") {
            val ink = LocalContentColor.current
            androidx.compose.foundation.Canvas(Modifier.size(25.dp)) {
                val unit = size.width / 24f
                drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(8 * unit, 2 * unit),
                    size = androidx.compose.ui.geometry.Size(8 * unit, 13 * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4 * unit),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2 * unit))
                drawArc(ink, 0f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(4 * unit, 6 * unit),
                    size = androidx.compose.ui.geometry.Size(16 * unit, 14 * unit), style = androidx.compose.ui.graphics.drawscope.Stroke(2 * unit))
                drawLine(ink, androidx.compose.ui.geometry.Offset(12 * unit, 20 * unit), androidx.compose.ui.geometry.Offset(12 * unit, 23 * unit), 2 * unit)
                drawLine(ink, androidx.compose.ui.geometry.Offset(8 * unit, 23 * unit), androidx.compose.ui.geometry.Offset(16 * unit, 23 * unit), 2 * unit)
            }
        } else if (symbol == "exit") {
            // Drawn because TV system fonts may not include the power glyph.
            val ink = LocalContentColor.current
            androidx.compose.foundation.Canvas(Modifier.size(25.dp)) {
                val unit = size.width / 24f
                drawArc(ink, -60f, 300f, false, topLeft = androidx.compose.ui.geometry.Offset(4 * unit, 4 * unit),
                    size = androidx.compose.ui.geometry.Size(16 * unit, 16 * unit),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2 * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawLine(ink, androidx.compose.ui.geometry.Offset(12 * unit, 2 * unit), androidx.compose.ui.geometry.Offset(12 * unit, 12 * unit),
                    2 * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        } else Text(symbol, fontSize = 25.sp)
    }
}

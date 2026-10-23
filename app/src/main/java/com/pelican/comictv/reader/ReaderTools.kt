package com.pelican.comictv.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*

@Composable fun ReaderTools(state: ReaderState, hasError: Boolean, onAction: (ReaderAction) -> Unit,
    onRetry: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(modifier.fillMaxWidth().padding(24.dp).background(TvColors.Panel, RoundedCornerShape(14.dp))
        .padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ferramentas de leitura", fontSize = 23.sp)
            Text("Página ${state.page + 1} · ${(state.zoom * 100).toInt()}%", color = TvColors.Muted, fontSize = 16.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton("Continuar", { onAction(ReaderAction.Back) }, Modifier.focusRequester(first), primary = true)
            TvButton("Mover página", { onAction(ReaderAction.StartPan) })
            TvButton("Ajuste: " + when (state.fit) { FitMode.Page -> "Página"; FitMode.Width -> "Largura"; FitMode.Height -> "Altura" }, { onAction(ReaderAction.ToggleFit) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(if (state.rtl) "Direção: direita → esquerda" else "Direção: esquerda → direita", { onAction(ReaderAction.ToggleRtl) })
            if (hasError) TvButton("Tentar novamente", { onRetry(); onAction(ReaderAction.Back) })
            TvButton("Sair da leitura", onExit)
        }
        Text("Use ↑ ↓ durante a leitura para ajustar o zoom. Em Mover página, as quatro setas deslocam a imagem.", fontSize = 15.sp, color = TvColors.Muted)
    }
}

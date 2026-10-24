package com.pelican.comictv.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*

@Composable fun ReaderTools(state: ReaderState, hasError: Boolean, onAction: (ReaderAction) -> Unit,
    onRetry: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier,
    automatic: AutomaticReading = AutomaticReading(), onStartAutomatic: () -> Unit = {}, onAutomaticInterval: () -> Unit = {},
    onEditions: (() -> Unit)? = null, onPreviousChapter: (() -> Unit)? = null, onNextChapter: (() -> Unit)? = null,
    gallery: Boolean = false, onToggleGallery: () -> Unit = {}) {
    val first = remember { FocusRequester() }
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(settings) { if (!settings) first.requestFocus() }
    BackHandler(settings) { settings = false }
    Box(modifier.fillMaxHeight().widthIn(max = 480.dp)) {
        if (settings) {
            val settingsFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { settingsFocus.requestFocus() }
            Column(Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight()
                .background(TvColors.Panel).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Mais opções", fontSize = 25.sp)
                TvButton("Voltar ao menu", { settings = false }, Modifier.focusRequester(settingsFocus))
                Text("Página ${state.page + 1} · ${(state.zoom * 100).toInt()}%", color = TvColors.Muted, fontSize = 16.sp)
                TvButton(if (state.rtl) "Direção: direita → esquerda" else "Direção: esquerda → direita", { onAction(ReaderAction.ToggleRtl) })
                Text("Leitura automática", fontSize = 20.sp)
                TvButton("Iniciar leitura automática", onStartAutomatic)
                TvButton("Intervalo: ${automatic.secondsPerScreen}s", onAutomaticInterval)
                Text("Setas e OK pausam a leitura automática. Não continua em segundo plano.", color = TvColors.Muted, fontSize = 16.sp)
                Text("↑ ↓ ajustam o zoom. Em Mover página, as quatro setas deslocam a imagem. OK alterna Mover e Zoom; segure OK por 1s para abrir o menu.", color = TvColors.Muted, fontSize = 16.sp)
            }
        } else {
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp, horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End) {
                RailButton("Continuar", RailIcon.Play, { onAction(ReaderAction.Back) }, Modifier.focusRequester(first))
                if (state.pageCount > 0) RailButton("Escolher página", RailIcon.Pages, { onAction(ReaderAction.StartTimeline) })
                onPreviousChapter?.let { RailButton("Capítulo anterior", RailIcon.Previous, it) }
                onNextChapter?.let { RailButton("Próximo capítulo", RailIcon.Next, it) }
                onEditions?.let { RailButton("Capítulos / edições", RailIcon.Chapters, it) }
                RailButton(if (gallery) "Galeria: ligada" else "Galeria: desligada", RailIcon.Gallery, onToggleGallery)
                RailButton("Ajuste: " + when (state.fit) { FitMode.Page -> "Página"; FitMode.Width -> "Largura"; FitMode.Height -> "Altura" }, RailIcon.Fit, { onAction(ReaderAction.ToggleFit) })
                RailButton("Mais opções", RailIcon.More, { settings = true })
                if (hasError) RailButton("Tentar novamente", RailIcon.Retry, { onRetry(); onAction(ReaderAction.Back) })
                RailButton("Sair da leitura", RailIcon.Exit, onExit)
            }
        }
    }
}

private enum class RailIcon { Play, Pages, Gallery, Previous, Next, Chapters, Pan, Fit, More, Retry, Exit }

@Composable private fun RailButton(label: String, icon: RailIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (focused) Text(label, Modifier.background(TvColors.Panel, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 17.sp)
        Button(onClick, modifier.size(48.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
            contentPadding = PaddingValues(12.dp),
            colors = ButtonDefaults.colors(containerColor = TvColors.Panel, contentColor = Color.White,
                focusedContainerColor = TvColors.Amber, focusedContentColor = TvColors.Ink),
            scale = ButtonDefaults.scale(focusedScale = 1.08f)) {
            RailGlyph(icon, if (focused) TvColors.Ink else Color.White)
        }
    }
}

@Composable private fun RailGlyph(icon: RailIcon, tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(size.width*x/24, size.height*y/24), Offset(size.width*x2/24, size.height*y2/24), 2.dp.toPx())
        fun path(vararg points: Float) {
            val p = Path().apply { moveTo(points[0]*size.width/24, points[1]*size.height/24); for (i in 2 until points.size step 2) lineTo(points[i]*size.width/24, points[i+1]*size.height/24) }
            drawPath(p, tint, style = Stroke(2.dp.toPx()))
        }
        when (icon) {
            RailIcon.Play -> path(8f,4f,19f,12f,8f,20f,8f,4f)
            RailIcon.Gallery -> { path(8f,2f,16f,2f,16f,22f,8f,22f,8f,2f); path(1f,7f,5f,7f,5f,17f,1f,17f,1f,7f); path(19f,7f,23f,7f,23f,17f,19f,17f,19f,7f) }
            RailIcon.Pages -> { path(7f,3f,20f,3f,20f,18f,7f,18f,7f,3f); path(3f,7f,3f,22f,16f,22f) }
            RailIcon.Previous -> { line(4f,4f,4f,20f); path(18f,5f,9f,12f,18f,19f) }
            RailIcon.Next -> { line(20f,4f,20f,20f); path(6f,5f,15f,12f,6f,19f) }
            RailIcon.Chapters -> { for (y in listOf(5f,12f,19f)) { line(3f,y,5f,y); line(9f,y,21f,y) } }
            RailIcon.Pan -> { line(12f,3f,12f,21f); line(3f,12f,21f,12f); path(8f,7f,12f,3f,16f,7f); path(8f,17f,12f,21f,16f,17f); path(7f,8f,3f,12f,7f,16f); path(17f,8f,21f,12f,17f,16f) }
            RailIcon.Fit -> { path(3f,9f,3f,3f,9f,3f); path(15f,3f,21f,3f,21f,9f); path(21f,15f,21f,21f,15f,21f); path(9f,21f,3f,21f,3f,15f) }
            RailIcon.More -> for (y in listOf(5f,12f,19f)) drawCircle(tint, 2.dp.toPx(), Offset(size.width/2, size.height*y/24))
            RailIcon.Retry -> { drawArc(tint, 45f, 280f, false, topLeft = Offset(size.width/6,size.height/6), size = androidx.compose.ui.geometry.Size(size.width*2/3,size.height*2/3), style = Stroke(2.dp.toPx())); path(15f,3f,20f,4f,19f,9f) }
            RailIcon.Exit -> { path(10f,3f,3f,3f,3f,21f,10f,21f); line(9f,12f,21f,12f); path(16f,7f,21f,12f,16f,17f) }
        }
    }
}

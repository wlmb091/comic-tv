package com.pelican.comictv.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.tv.material3.Border
import androidx.tv.material3.LocalContentColor
import com.pelican.comictv.ui.*

@Composable fun ReaderTools(state: ReaderState, hasError: Boolean, onAction: (ReaderAction) -> Unit,
    onRetry: () -> Unit, onExit: () -> Unit, modifier: Modifier = Modifier,
    automatic: AutomaticReading = AutomaticReading(), onStartAutomatic: () -> Unit = {}, onAutomaticInterval: () -> Unit = {},
    onEditions: (() -> Unit)? = null, onPreviousChapter: (() -> Unit)? = null, onNextChapter: (() -> Unit)? = null,
    layout: ReaderLayout = ReaderLayout.Normal, onCycleLayout: () -> Unit = {},
    imageOptions: ReaderImageOptions = ReaderImageOptions(), compareOriginal: Boolean = false,
    onCycleImagePreset: () -> Unit = {}, onCycleImageIntensity: () -> Unit = {}, onCompareOriginal: () -> Unit = {}, onNarrate: (() -> Unit)? = null) {
    val first = remember { FocusRequester() }
    val more = remember { FocusRequester() }
    var settings by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    fun closeSettings() { returning = true; settings = false }
    LaunchedEffect(settings) { if (!settings) { if (returning) more.requestFocus() else first.requestFocus(); returning = false } }
    BackHandler(settings) { closeSettings() }
    Box(modifier.fillMaxHeight().widthIn(max = 480.dp)) {
        if (settings) {
            val settingsFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { settingsFocus.requestFocus() }
            Column(Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight()
                .background(TvColors.Panel).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mais opções", fontSize = 25.sp)
                TvButton("Voltar ao menu", ::closeSettings, Modifier.focusRequester(settingsFocus))
                onNarrate?.let { TvButton("Ouvir livro e traduzir", it) }
                Text("Página ${state.page + 1} · ${(state.zoom * 100).toInt()}%", color = TvColors.Muted, fontSize = 16.sp)
                TvButton(if (state.rtl) "Direção: direita → esquerda" else "Direção: esquerda → direita", { onAction(ReaderAction.ToggleRtl) })
                Text("Direção salva nesta edição. Mangás podem usar direita → esquerda; sua escolha manual é preservada.", color = TvColors.Muted, fontSize = 16.sp)
                Text("Imagem", fontSize = 20.sp)
                TvButton("Filtro: " + imageOptions.preset.label, onCycleImagePreset)
                if (imageOptions.preset != ImagePreset.Original) {
                    TvButton("Intensidade: " + listOf("Suave", "Média", "Forte")[imageOptions.intensity.coerceIn(0, 2)], onCycleImageIntensity)
                    TvButton(if (compareOriginal) "Voltar ao filtro" else "Comparar com original", onCompareOriginal)
                }
                Text("Filtro salvo nesta edição. O arquivo original é preservado.", color = TvColors.Muted, fontSize = 16.sp)
                Text("Leitura automática", fontSize = 20.sp)
                TvButton("Iniciar leitura automática", onStartAutomatic)
                TvButton("Intervalo: ${automatic.secondsPerScreen}s", onAutomaticInterval)
                Text("Setas e OK pausam a leitura automática. Não continua em segundo plano.", color = TvColors.Muted, fontSize = 16.sp)
                Text("↑ ↓ ajustam o zoom. Em Mover página, as quatro setas deslocam a imagem. OK alterna Mover e Zoom; segure OK por 1s para abrir o menu.", color = TvColors.Muted, fontSize = 16.sp)
            }
        } else {
            val actions = buildList {
                add(Tool(ToolId.Continue, "Continuar", RailIcon.Play) { onAction(ReaderAction.Back) })
                if (state.pageCount > 0) add(Tool(ToolId.Pages, "Escolher página", RailIcon.Pages) { onAction(ReaderAction.StartTimeline) })
                onPreviousChapter?.let { add(Tool(ToolId.Previous, "Capítulo anterior", RailIcon.Previous, it)) }
                onNextChapter?.let { add(Tool(ToolId.Next, "Próximo capítulo", RailIcon.Next, it)) }
                onEditions?.let { add(Tool(ToolId.Chapters, "Capítulos / edições", RailIcon.Chapters, it)) }
                add(Tool(ToolId.Layout, "Visualização: " + layout.label, when (layout) {
                    ReaderLayout.Normal -> RailIcon.Normal
                    ReaderLayout.Gallery -> RailIcon.Gallery
                    ReaderLayout.Book -> RailIcon.Book
                }, onCycleLayout))
                add(Tool(ToolId.Fit, "Ajuste: " + when (state.fit) { FitMode.Page -> "Página"; FitMode.Width -> "Largura"; FitMode.Height -> "Altura" }, RailIcon.Fit) { onAction(ReaderAction.ToggleFit) })
                add(Tool(ToolId.More, "Imagem e mais opções", RailIcon.More) { settings = true })
                onNarrate?.let { add(Tool(ToolId.Narration, "Ouvir livro e traduzir", RailIcon.Book, it)) }
                if (hasError) add(Tool(ToolId.Retry, "Tentar novamente", RailIcon.Retry) { onRetry(); onAction(ReaderAction.Back) })
                add(Tool(ToolId.Exit, "Sair da leitura", RailIcon.Exit, onExit))
            }
            val ids = actions.map { it.id }
            val requesters = remember(ids) { ids.associateWith { id -> when (id) { ToolId.Continue -> first; ToolId.More -> more; else -> FocusRequester() } } }
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp, horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End) {
                actions.forEachIndexed { index, tool ->
                    fun neighbor(direction: ToolDirection) = readerToolNeighbor(index, actions.size, direction)
                        ?.let { requesters.getValue(actions[it].id) } ?: FocusRequester.Cancel
                    RailButton(tool.label, tool.icon, tool.action, Modifier.focusRequester(requesters.getValue(tool.id))
                        .focusProperties { left = neighbor(ToolDirection.Left); right = neighbor(ToolDirection.Right)
                            up = neighbor(ToolDirection.Up); down = neighbor(ToolDirection.Down) })
                }
            }
        }
    }
}

private enum class RailIcon { Play, Pages, Normal, Gallery, Book, Previous, Next, Chapters, Pan, Fit, More, Retry, Exit }
private enum class ToolId { Continue, Pages, Previous, Next, Chapters, Layout, Fit, More, Narration, Retry, Exit }
private data class Tool(val id: ToolId, val label: String, val icon: RailIcon, val action: () -> Unit)

@Composable private fun RailButton(label: String, icon: RailIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (focused) Text(label, Modifier.widthIn(max = 340.dp)
            .background(TvColors.Panel, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 17.sp, lineHeight = 22.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Button(onClick, modifier.size(48.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label },
            contentPadding = PaddingValues(12.dp),
            colors = ButtonDefaults.colors(containerColor = TvColors.Panel, contentColor = Color.White,
                focusedContainerColor = TvColors.Action, focusedContentColor = TvColors.OnAction),
            scale = ButtonDefaults.scale(focusedScale = 1.08f),
            border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(if (TvColors.options.highContrast) 3.dp else 2.dp, TvColors.Amber)))) {
            RailGlyph(icon, LocalContentColor.current)
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
            RailIcon.Normal -> path(5f,2f,19f,2f,19f,22f,5f,22f,5f,2f)
            RailIcon.Book -> { path(12f,5f,3f,3f,3f,20f,12f,22f,21f,20f,21f,3f,12f,5f,12f,22f) }
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

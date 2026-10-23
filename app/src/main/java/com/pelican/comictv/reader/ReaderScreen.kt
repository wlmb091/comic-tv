package com.pelican.comictv.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.pelican.comictv.ui.*
import kotlin.math.min

@Composable fun ReaderScreen(viewModel: ReaderViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val image by viewModel.image.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var renderError by remember(image?.file) { mutableStateOf(false) }
    val displayedError = error ?: if (renderError) "Não foi possível decodificar esta página." else null
    val focus = remember { FocusRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val statusHeight = with(LocalDensity.current) { 40.dp.roundToPx() }
    val pageAreaHeight = (size.height - statusHeight).coerceAtLeast(1)
    val exit = { viewModel.close(onExit) }
    BackHandler {
        if (state.mode != ReaderMode.Reading) viewModel.action(ReaderAction.Back) else exit()
    }
    LaunchedEffect(state.mode) { if (state.mode != ReaderMode.Tools) focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds().onSizeChanged { size = it }
        .onPreviewKeyEvent { event ->
            if (state.mode == ReaderMode.Tools) false else {
                val action = when (event.key) {
                    Key.DirectionLeft -> ReaderAction.Left
                    Key.DirectionRight -> ReaderAction.Right
                    Key.DirectionUp -> ReaderAction.Up
                    Key.DirectionDown -> ReaderAction.Down
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> ReaderAction.Select
                    else -> null
                }
                if (action == null) false else {
                    if (event.type == (if (action == ReaderAction.Select) KeyEventType.KeyUp else KeyEventType.KeyDown)) viewModel.action(action)
                    true
                }
            }
        }.focusRequester(focus).focusable()) {
        image?.let { page ->
            val fittedScale = if (size.width > 0) min(size.width.toFloat() / page.width, pageAreaHeight.toFloat() / page.height) else 1f
            val fittedWidth = page.width * fittedScale
            val fittedHeight = page.height * fittedScale
            val adjustment = when (state.fit) {
                FitMode.Page -> 1f
                FitMode.Width -> if (fittedWidth > 0) size.width / fittedWidth else 1f
                FitMode.Height -> if (fittedHeight > 0) pageAreaHeight / fittedHeight else 1f
            }
            val scale = state.zoom * adjustment
            val maxX = ((fittedWidth * scale - size.width) / 2).coerceAtLeast(0f)
            val maxY = ((fittedHeight * scale - pageAreaHeight) / 2).coerceAtLeast(0f)
            val context = LocalContext.current
            val request = remember(page.file, size) {
                ImageRequest.Builder(context).data(page.file).size(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1)).build()
            }
            AsyncImage(request, "Página ${state.page + 1}", Modifier.fillMaxSize().padding(bottom = 40.dp).graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = -state.panX * maxX; translationY = -state.panY * maxY
            }, contentScale = ContentScale.Fit, onError = { renderError = true })
        }
        if (loading) Text("Carregando página…", Modifier.align(Alignment.Center), fontSize = 20.sp)
        displayedError?.let { text ->
            Column(Modifier.align(Alignment.Center).widthIn(max = 540.dp).background(TvColors.Panel, RoundedCornerShape(12.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(text, fontSize = 20.sp)
                Text("Pressione OK para abrir as ferramentas e tentar novamente.", color = TvColors.Muted, fontSize = 16.sp)
            }
        }
        if (state.mode != ReaderMode.Tools) {
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp).background(Color.Black).padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.pageCount > 0) "Página ${state.page + 1} de ${state.pageCount}" else "Leitor", fontSize = 14.sp)
                Text(if (state.mode == ReaderMode.Pan) "↑ ↓ ← →  Mover  ·  Voltar  Ferramentas" else "← →  Páginas  ·  ↑ ↓  Zoom  ·  OK  Ferramentas", fontSize = 14.sp, color = TvColors.Muted)
                Text("${(state.zoom * 100).toInt()}%", fontSize = 14.sp, color = TvColors.Amber)
            }
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
            ReaderTools(state, displayedError != null, viewModel::action, { renderError = false; viewModel.retry() }, exit,
                Modifier.align(Alignment.BottomCenter))
        }
    }
}

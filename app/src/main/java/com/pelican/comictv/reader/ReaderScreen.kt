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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.pelican.comictv.ui.*
import kotlin.math.min
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun ReaderScreen(viewModel: ReaderViewModel, onExit: () -> Unit, onEditions: (() -> Unit)? = null, onChapterStep: ((Int) -> Unit)? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val image by viewModel.image.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val automatic by viewModel.automatic.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) viewModel.pauseAutomaticReading()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.pauseAutomaticReading() }
    }
    image?.takeIf { it.text != null }?.let { page ->
        EpubTextScreen(viewModel, state, page, error, onExit)
        return
    }
    var renderError by remember(image?.file) { mutableStateOf(false) }
    val displayedError = error ?: if (renderError) "Não foi possível decodificar esta página." else null
    val focus = remember { FocusRequester() }
    val centerPress = remember { CenterPressController() }
    val gestureScope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    val cancelCenter = { holdJob?.cancel(); holdJob = null; centerPress.cancel() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) cancelCenter()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); cancelCenter() }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val timelineHeight = if (state.mode == ReaderMode.Timeline) 220.dp else 0.dp
    val timelinePixels = with(LocalDensity.current) { timelineHeight.roundToPx() }
    val pageAreaHeight = (size.height - timelinePixels).coerceAtLeast(1)
    var showPosition by remember { mutableStateOf(true) }
    LaunchedEffect(image?.file, state.page, state.zoom, state.mode) {
        showPosition = true
        delay(1800)
        showPosition = false
    }
    val exit = { viewModel.close(onExit) }
    BackHandler {
        if (state.mode != ReaderMode.Reading) viewModel.action(ReaderAction.Back) else exit()
    }
    LaunchedEffect(state.mode) { if (state.mode != ReaderMode.Tools) focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds().onSizeChanged { size = it }
        .onPreviewKeyEvent { event ->
            val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
            if (isCenter && (state.mode != ReaderMode.Tools || centerPress.isPressed)) {
                if (event.type == KeyEventType.KeyDown) {
                    if (event.nativeKeyEvent.repeatCount == 0 && centerPress.down(SystemClock.uptimeMillis())) {
                        holdJob = gestureScope.launch {
                            delay(2000)
                            centerPress.hold(SystemClock.uptimeMillis())?.let(viewModel::action)
                        }
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    holdJob?.cancel()
                    holdJob = null
                    if (event.nativeKeyEvent.isCanceled) centerPress.cancel()
                    else centerPress.up(SystemClock.uptimeMillis(), state.mode)?.let(viewModel::action)
                }
                true
            } else if (state.mode == ReaderMode.Tools) false else {
                val action = when (event.key) {
                    Key.DirectionLeft -> ReaderAction.Left
                    Key.DirectionRight -> ReaderAction.Right
                    Key.DirectionUp -> ReaderAction.Up
                    Key.DirectionDown -> ReaderAction.Down
                    else -> null
                }
                if (action == null) false else {
                    if (event.type == KeyEventType.KeyDown) viewModel.action(action)
                    true
                }
            }
        }.onFocusChanged { if (!it.hasFocus) cancelCenter() }.focusRequester(focus).focusable()) {
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
            AsyncImage(request, "Página ${state.page + 1}", Modifier.fillMaxSize().padding(bottom = timelineHeight).graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = -state.panX * maxX; translationY = -state.panY * maxY
            }, contentScale = ContentScale.Fit,
                onSuccess = { viewModel.imageDisplayed(page.file) },
                onError = { renderError = true; viewModel.imageDisplayFailed(page.file) })
        }
        if (showPosition && state.pageCount > 0 && (state.mode == ReaderMode.Reading || state.mode == ReaderMode.Pan)) {
            Text("${state.page + 1} / ${state.pageCount} · ${(state.zoom * 100).toInt()}%",
                Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = .65f)).padding(8.dp),
                fontSize = 14.sp, color = Color.White)
        }
        if (loading) Text("Carregando página…", Modifier.align(Alignment.Center), fontSize = 20.sp)
        displayedError?.let { text ->
            Column(Modifier.align(Alignment.Center).widthIn(max = 540.dp).background(TvColors.Panel, RoundedCornerShape(12.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(text, fontSize = 20.sp)
                Text("Segure OK por 2s para abrir as ferramentas e tentar novamente.", color = TvColors.Muted, fontSize = 16.sp)
            }
        }
        if (state.mode == ReaderMode.Timeline) {
            ReaderTimeline(state, Modifier.align(Alignment.BottomCenter), previews = previews)
        } else if (state.mode == ReaderMode.Tools) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .15f)))
            ReaderTools(state, displayedError != null, viewModel::action, { renderError = false; viewModel.retry() }, exit,
                Modifier.align(Alignment.CenterEnd), automatic, viewModel::startAutomaticReading, viewModel::cycleAutomaticInterval,
                onEditions = onEditions?.let { action -> { viewModel.close(action) } },
                onPreviousChapter = onChapterStep?.let { action -> { viewModel.close { action(-1) } } },
                onNextChapter = onChapterStep?.let { action -> { viewModel.close { action(1) } } })
        }
    }
}

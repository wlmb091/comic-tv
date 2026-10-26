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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun ReaderScreen(viewModel: ReaderViewModel, onExit: () -> Unit, onEditions: (() -> Unit)? = null, onChapterStep: ((Int) -> Unit)? = null, narrationOriginal: java.io.File? = null) {
    var narrationMode by remember(viewModel) { mutableStateOf(false) }
    val narrate: (() -> Unit)? = if (ReaderFeatures.narrationAndTranslation) ({ viewModel.beginNarration(); narrationMode = true }) else null
    val state by viewModel.state.collectAsStateWithLifecycle()
    val image by viewModel.image.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val bookPartner by viewModel.bookPartner.collectAsStateWithLifecycle()
    val automatic by viewModel.automatic.collectAsStateWithLifecycle()
    val imageOptions by viewModel.imageOptions.collectAsStateWithLifecycle()
    val compareOriginal by viewModel.compareOriginal.collectAsStateWithLifecycle()
    val anticipated by viewModel.anticipated.collectAsStateWithLifecycle()
    val effectiveImageOptions = if (compareOriginal) ReaderImageOptions() else imageOptions
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, narrationMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) { viewModel.pauseAutomaticReading(); viewModel.pauseAnticipation() }
            if (event == Lifecycle.Event.ON_RESUME && !narrationMode) viewModel.resumeAnticipation()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.pauseAutomaticReading() }
    }
    if (ReaderFeatures.narrationAndTranslation && narrationMode) {
        com.pelican.comictv.reader.narration.NarratedReaderScreen(viewModel, narrationOriginal) {
            viewModel.finishNarration(); narrationMode = false
        }
        return
    }
    image?.takeIf { it.text != null }?.let { page ->
        EpubTextScreen(viewModel, state, page, error, onExit, narrate)
        return
    }
    val context = LocalContext.current
    val tileCache = remember(viewModel) {
        val memoryClass = (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass
        ReaderTileCache((memoryClass / 8).coerceIn(8, 24) * 1024L * 1024)
    }
    DisposableEffect(tileCache) { onDispose { tileCache.close() } }
    var renderError by remember(image?.file) { mutableStateOf(false) }
    val displayedError = error ?: if (renderError) "Não foi possível decodificar esta página." else null
    val focus = remember { FocusRequester() }
    val endShortcutFocus = remember { FocusRequester() }
    var endShortcutFocused by remember { mutableStateOf(false) }
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
    LaunchedEffect(anticipated, size, effectiveImageOptions, layout) {
        if (size.width == 0 || anticipated.isEmpty()) return@LaunchedEffect
        delay(100)
        val width = if (layout == ReaderLayout.Gallery) ((size.width - 80) * .48f).toInt().coerceAtLeast(1) else size.width
        val height = (size.height - if (layout == ReaderLayout.Gallery) 32 else if (layout == ReaderLayout.Book) 24 else 0).coerceAtLeast(1)
        val forecasts = if (layout == ReaderLayout.Book) zoomPlacements(anticipated.map { ImageExtent(it.width, it.height) }, width - 24, height, 1f, FitMode.Page, 0f, 0f, 4f)
            else anticipated.flatMap { zoomPlacements(listOf(ImageExtent(it.width, it.height)), width, height, 1f, FitMode.Page, 0f, 0f) }
        anticipated.forEachIndexed { index, page ->
            val regions = listOf(overviewRegion(page)) + forecasts.getOrNull(index)?.let { visibleTiles(it, width, height) }.orEmpty()
            for (region in regions.distinct()) {
                try { tileCache.load(TileKey(page.file, tileIdentity(page), region, effectiveImageOptions)) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
        }
    }
    val timelineHeight = if (state.mode == ReaderMode.Timeline) 320.dp + pagePickerTextExtra() * 2 else 0.dp
    val timelinePixels = with(LocalDensity.current) { timelineHeight.roundToPx() }
    val pageAreaHeight = (size.height - timelinePixels).coerceAtLeast(1)
    var showPosition by remember { mutableStateOf(true) }
    LaunchedEffect(image?.file, state.page, state.zoom, state.mode) {
        showPosition = true
        delay(1800)
        showPosition = false
    }
    val exit = { viewModel.close(onExit) }
    val lastVisiblePage = state.page + if (layout == ReaderLayout.Book && viewModel.hasBookPartner(state.page) && bookPartner?.displayed == true) 1 else 0
    val showEndShortcut = chapterEndShortcutVisible(state, onChapterStep != null,
        !loading && displayedError == null && image?.displayed == true, lastVisiblePage)
    LaunchedEffect(showEndShortcut) {
        if (!showEndShortcut && endShortcutFocused) { endShortcutFocused = false; focus.requestFocus() }
    }
    BackHandler {
        if (showEndShortcut && endShortcutFocused) focus.requestFocus()
        else if (state.mode != ReaderMode.Reading) viewModel.action(ReaderAction.Back) else exit()
    }
    LaunchedEffect(state.mode) { if (state.mode != ReaderMode.Tools) focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds().onSizeChanged { size = it }
        .onPreviewKeyEvent { event ->
            if (showEndShortcut && endShortcutFocused) return@onPreviewKeyEvent false
            val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
            if (isCenter && (state.mode != ReaderMode.Tools || centerPress.isPressed)) {
                if (event.type == KeyEventType.KeyDown) {
                    if (event.nativeKeyEvent.repeatCount == 0 && centerPress.down(SystemClock.uptimeMillis())) {
                        holdJob = gestureScope.launch {
                            delay(1000)
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
                    if (event.type == KeyEventType.KeyDown) {
                        if (viewModel.state.value == state && focusChapterEndShortcut(state, action, showEndShortcut)) endShortcutFocus.requestFocus()
                        else viewModel.action(action)
                    }
                    true
                }
            }
        }.onFocusChanged { if (!it.hasFocus) cancelCenter() }.focusRequester(focus).focusable()) {
        if (layout == ReaderLayout.Gallery) ReaderGallery(state, image, previews, tileCache, effectiveImageOptions, Modifier.fillMaxSize().padding(bottom = timelineHeight),
            onDisplayed = viewModel::imageDisplayed, onFailed = viewModel::imageDisplayFailed)
        if (layout == ReaderLayout.Book) ReaderBook(state, image, bookPartner, viewModel.hasBookPartner(state.page), tileCache, effectiveImageOptions, Modifier.fillMaxSize().padding(bottom = timelineHeight),
            viewModel::imageDisplayed, viewModel::imageDisplayFailed, viewModel::bookPageDisplayed, viewModel::bookPageFailed)
        if (layout == ReaderLayout.Normal) image?.let { page ->
            HighResolutionPages(state, listOf(page), tileCache, effectiveImageOptions,
                Modifier.fillMaxSize().padding(bottom = timelineHeight),
                onDisplayed = viewModel::imageDisplayed, onFailed = viewModel::imageDisplayFailed)
        }
        if (showPosition && state.pageCount > 0 && (state.mode == ReaderMode.Reading || state.mode == ReaderMode.Pan)) {
            Text((if (layout == ReaderLayout.Book && viewModel.hasBookPartner(state.page))
                (state.page + 1).toString() + "–" + (state.page + 2) else (state.page + 1).toString()) +
                " / " + state.pageCount + " · " + (state.zoom * 100).toInt() + "%",
                Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha = .65f)).padding(8.dp),
                fontSize = 14.sp, color = Color.White)
        }
        if (loading) Text("Carregando página…", Modifier.align(Alignment.Center), fontSize = 20.sp)
        ReaderModeNotice(state.mode, Modifier.align(Alignment.BottomCenter).padding(24.dp))
        if (showEndShortcut) ChapterEndShortcut(endShortcutFocus, { focused ->
            endShortcutFocused = focused
            if (focused) { cancelCenter(); viewModel.pauseAutomaticReading() }
        }, {
            if (viewModel.state.value == state) viewModel.close { onChapterStep?.invoke(1) }
        }, { focus.requestFocus() }, Modifier.align(Alignment.BottomEnd).padding(24.dp))
        displayedError?.let { text ->
            Column(Modifier.align(Alignment.Center).widthIn(max = 540.dp).background(TvColors.Panel, RoundedCornerShape(12.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(text, fontSize = 20.sp)
                Text("Segure OK por 1s para abrir as ferramentas e tentar novamente.", color = TvColors.Muted, fontSize = 16.sp)
            }
        }
        if (state.mode == ReaderMode.Timeline) {
            ReaderTimeline(state, Modifier.align(Alignment.BottomCenter), previews = previews)
        } else if (state.mode == ReaderMode.Tools) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .15f)))
            ReaderTools(state, displayedError != null, viewModel::action, { tileCache.clear(); renderError = false; viewModel.retry() }, exit,
                Modifier.align(Alignment.CenterEnd), automatic, viewModel::startAutomaticReading, viewModel::cycleAutomaticInterval,
                onEditions = onEditions?.let { action -> { viewModel.close(action) } },
                onPreviousChapter = onChapterStep?.let { action -> { viewModel.close { action(-1) } } },
                onNextChapter = onChapterStep?.let { action -> { viewModel.close { action(1) } } },
                layout = layout, onCycleLayout = viewModel::cycleLayout,
                imageOptions = imageOptions, compareOriginal = compareOriginal, onCycleImagePreset = viewModel::cycleImagePreset,
                onCycleImageIntensity = viewModel::cycleImageIntensity, onCompareOriginal = viewModel::toggleOriginalComparison,
                onNarrate = if (viewModel.canNarrateBook()) narrate else null)
        }
    }
}

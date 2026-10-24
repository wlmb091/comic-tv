package com.pelican.comictv.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(FlowPreview::class)
@Composable internal fun EpubTextScreen(viewModel: ReaderViewModel, state: ReaderState, page: PageImage, error: String?, onExit: () -> Unit, onNarrate: () -> Unit = {}) {
    val automatic by viewModel.automatic.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val preferences = LocalContext.current.getSharedPreferences("epub-reading", 0)
    var fontSize by remember { mutableIntStateOf(preferences.getInt("font-size", 24).coerceIn(18, 40)) }
    val positionKey = "position-${page.file.name}"
    val scroll = remember(positionKey) { ScrollState(0) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var restored by remember(positionKey) { mutableStateOf(false) }
    fun savePosition() {
        if (restored) preferences.edit().putFloat(positionKey,
            if (scroll.maxValue > 0 && scroll.maxValue != Int.MAX_VALUE) scroll.value.toFloat() / scroll.maxValue else 0f).apply()
    }
    LaunchedEffect(positionKey) {
        val maximum = snapshotFlow { scroll.maxValue }.first { it != Int.MAX_VALUE }
        val ratio = preferences.getFloat(positionKey, 0f).coerceIn(0f, 1f)
        scroll.scrollTo((maximum * ratio).roundToInt())
        restored = true
    }
    DisposableEffect(positionKey, restored) { onDispose { savePosition() } }
    LaunchedEffect(positionKey, restored) {
        if (restored) snapshotFlow { scroll.value }.sample(500).collect { savePosition() }
    }
    LaunchedEffect(positionKey, restored, automatic, state.mode) {
        if (!restored || !automatic.enabled || state.mode != ReaderMode.Reading) return@LaunchedEffect
        val expectedPage = state.page
        val remaining = (scroll.maxValue - scroll.value).coerceAtLeast(0)
        if (remaining == 0) delay(automatic.secondsPerScreen * 1_000L)
        else {
            val duration = (remaining.toDouble() / scroll.viewportSize.coerceAtLeast(1) * automatic.secondsPerScreen * 1_000).toInt().coerceAtLeast(250)
            scroll.animateScrollTo(scroll.maxValue, tween(duration, easing = LinearEasing))
            delay(800)
        }
        viewModel.automaticSectionFinished(expectedPage)
    }
    val tools = state.mode == ReaderMode.Tools
    val exit = { savePosition(); viewModel.close(onExit) }
    BackHandler { if (state.mode != ReaderMode.Reading) viewModel.action(ReaderAction.Back) else exit() }
    LaunchedEffect(tools) { if (!tools) focus.requestFocus() }
    Box(Modifier.fillMaxSize().background(TvColors.Background)
        .onPreviewKeyEvent { event ->
            if (tools) false else {
                val action = when (event.key) {
                    Key.DirectionLeft -> ReaderAction.Left
                    Key.DirectionRight -> ReaderAction.Right
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> ReaderAction.Select
                    else -> null
                }
                when {
                    state.mode == ReaderMode.Timeline && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) -> {
                        if (event.type == KeyEventType.KeyDown) viewModel.action(if (event.key == Key.DirectionUp) ReaderAction.Up else ReaderAction.Down)
                        true
                    }
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
                        viewModel.pauseAutomaticReading()
                        if (event.type == KeyEventType.KeyDown) scope.launch {
                            val delta = maxOf(scroll.viewportSize * .65f, 100f) * if (event.key == Key.DirectionDown) 1 else -1
                            scroll.scrollTo((scroll.value + delta).roundToInt().coerceIn(0, scroll.maxValue))
                        }
                        true
                    }
                    action != null -> {
                        if (event.type == (if (action == ReaderAction.Select) KeyEventType.KeyUp else KeyEventType.KeyDown)) {
                            savePosition(); viewModel.action(action)
                        }
                        true
                    }
                    else -> false
                }
            }
        }.focusRequester(focus).focusable()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp).padding(top = 28.dp, bottom = 58.dp)
            .verticalScroll(scroll)) {
            Text(page.text.orEmpty(), fontSize = fontSize.sp, lineHeight = (fontSize * 1.55).sp,
                modifier = Modifier.fillMaxWidth().widthIn(max = 1200.dp), color = Color(0xFFF5F3EF))
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(TvColors.Panel).padding(horizontal = 30.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Seção ${state.page + 1} de ${state.pageCount}", fontSize = 14.sp)
            Text("↑ ↓  Rolar  ·  ← →  Seções  ·  OK  Fonte e opções", color = TvColors.Muted, fontSize = 14.sp)
            if (automatic.enabled) Text("Automático · ${automatic.secondsPerScreen}s/tela", color = TvColors.Amber, fontSize = 14.sp)
        }
        if (error != null) {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(TvColors.Panel).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error, color = TvColors.Amber, fontSize = 20.sp)
                Text("Se estava saindo, pressione Voltar para tentar salvar novamente.", color = TvColors.Muted, fontSize = 16.sp)
            }
        }
        if (tools) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { first.requestFocus() }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(TvColors.Panel).padding(30.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Leitura de texto · Fonte $fontSize", fontSize = 23.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton("Continuar", { viewModel.action(ReaderAction.Back) }, Modifier.focusRequester(first), primary = true)
                    TvButton("Fonte −", { fontSize = (fontSize - 2).coerceAtLeast(18); preferences.edit().putInt("font-size", fontSize).apply() })
                    TvButton("Fonte +", { fontSize = (fontSize + 2).coerceAtMost(40); preferences.edit().putInt("font-size", fontSize).apply() })
                    TvButton("Sair da leitura", exit)
                }
                if (state.pageCount > 0) TvButton("Escolher seção", { viewModel.action(ReaderAction.StartTimeline) })
                TvButton("Ouvir livro e traduzir", { savePosition(); onNarrate() })
                AutomaticReadingControls(automatic, viewModel::startAutomaticReading, viewModel::cycleAutomaticInterval)
                Text("O texto se ajusta à tela. Use ↑ ↓ para rolar e ← → para trocar de seção.", color = TvColors.Muted, fontSize = 16.sp)
            }
        }
        if (state.mode == ReaderMode.Timeline) ReaderTimeline(state, Modifier.align(Alignment.BottomCenter), section = true, previews = previews)
    }
}

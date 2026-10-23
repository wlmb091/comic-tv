package com.pelican.comictv.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import com.pelican.comictv.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable internal fun EpubTextScreen(viewModel: ReaderViewModel, state: ReaderState, page: PageImage, error: String?, onExit: () -> Unit) {
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
        if (restored) snapshotFlow { scroll.value }.collect { savePosition() }
    }
    val tools = state.mode == ReaderMode.Tools
    val exit = { savePosition(); viewModel.close(onExit) }
    BackHandler { if (tools) viewModel.action(ReaderAction.Back) else exit() }
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
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
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
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(TvColors.Panel).padding(30.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Leitura de texto · Fonte $fontSize", fontSize = 23.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvButton("Continuar", { viewModel.action(ReaderAction.Back) }, Modifier.focusRequester(first), primary = true)
                    TvButton("Fonte −", { fontSize = (fontSize - 2).coerceAtLeast(18); preferences.edit().putInt("font-size", fontSize).apply() })
                    TvButton("Fonte +", { fontSize = (fontSize + 2).coerceAtMost(40); preferences.edit().putInt("font-size", fontSize).apply() })
                    TvButton("Sair da leitura", exit)
                }
                Text("O texto se ajusta à tela. Use ↑ ↓ para rolar e ← → para trocar de seção.", color = TvColors.Muted, fontSize = 16.sp)
            }
        }
    }
}

package com.pelican.comictv.reader.narration

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.reader.ReaderViewModel
import com.pelican.comictv.reader.narrationDigest
import com.pelican.comictv.ui.*
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun NarratedReaderScreen(reader: ReaderViewModel, pdf: File?, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    var settings by remember { mutableStateOf(LocalTranslationSettings.read(app)) }
    val source = remember(reader) {
        TranslatedNarrationSource(if (reader.narrationIsPdf() && pdf != null)
            PdfNarrationSource(app, pdf, reader.state.value.pageCount)
        else EpubNarrationSource(reader.state.value.pageCount, reader::narrationText)).also { value ->
            if (settings.address.isNotBlank()) runCatching {
                value.configure(settings.client(), narrationDigest(settings.address + "|" + settings.language))
            }
        }
    }
    val controller = remember(reader) {
        NarrationController(reader.narrationBookId, source, AndroidNarrationSpeech(app),
            FileNarrationStore(File(app.filesDir, "narration")), reader.state.value.page, reader::narrationSelectPage)
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) controller.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.release() }
    }
    val scope = rememberCoroutineScope()
    var controls by remember { mutableStateOf(true) }
    var configure by remember { mutableStateOf(false) }
    var translated by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val preferences = remember { app.getSharedPreferences("epub-reading", 0) }
    var fontSize by remember { mutableIntStateOf(preferences.getInt("font-size", 24).coerceIn(18, 40)) }
    val scroll = rememberScrollState()
    val textFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val textLayout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val leave: () -> Unit = {
        if (!leaving) scope.launch {
            leaving = true
            if (controller.prepareExit()) onBack() else leaving = false
        }
    }
    BackHandler(!configure, leave)
    LaunchedEffect(controls, configure) { if (!configure) { if (controls) playFocus.requestFocus() else textFocus.requestFocus() } }
    LaunchedEffect(state.text) { scroll.scrollTo(0) }
    LaunchedEffect(state.start, state.speaking, fontSize, controls) {
        if (state.speaking && state.text.isNotEmpty()) {
            withFrameNanos { }
            textLayout[0]?.let { layout ->
                val line = layout.getLineForOffset(state.start.coerceIn(0, state.text.length - 1))
                val target = (layout.getLineTop(line) - scroll.viewportSize / 3).toInt().coerceIn(0, scroll.maxValue)
                scroll.scrollTo(target)
            }
        }
    }
    val highlighted = remember(state.text, state.start, state.end) {
        buildAnnotatedString {
            append(state.text)
            if (state.end > state.start && state.start >= 0 && state.end <= length)
                addStyle(SpanStyle(background = TvColors.Amber.copy(alpha = .24f)), state.start, state.end)
        }
    }
    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 36.dp, vertical = 20.dp)
        .onPreviewKeyEvent { event ->
            if (controls || configure) false else when (event.key) {
                Key.DirectionUp, Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown) {
                        controller.pause()
                        scope.launch { scroll.scrollTo((scroll.value + (scroll.viewportSize * .65f).toInt() *
                            if (event.key == Key.DirectionDown) 1 else -1).coerceIn(0, scroll.maxValue)) }
                    }; true
                }
                Key.DirectionLeft, Key.DirectionRight -> {
                    if (event.type == KeyEventType.KeyDown) controller.pageStep(if (event.key == Key.DirectionRight) 1 else -1)
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyUp) { controller.pause(); controls = true }; true
                }
                else -> false
            }
        }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${source.label} ${state.page + 1} de ${source.count} · " +
            if (translated) "Tradução automática para português" else "Texto original", fontSize = 20.sp)
        state.message?.let { Text(it, color = TvColors.Amber, fontSize = 17.sp) }
        hint?.let { Text(it, color = TvColors.Muted, fontSize = 16.sp) }
        Text(highlighted, Modifier.weight(1f).fillMaxWidth().focusRequester(textFocus).focusable().verticalScroll(scroll),
            fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp, onTextLayout = { textLayout[0] = it })
        if (controls) FlowRow(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(if (state.speaking) "Pausar" else "Ouvir / continuar", {
                if (state.speaking) controller.pause() else { controller.play(); controls = false }
            }, Modifier.focusRequester(playFocus))
            TvButton("Parar", controller::stop)
            TvButton("Trecho anterior", { controller.segmentStep(-1) })
            TvButton("Próximo trecho", { controller.segmentStep(1) })
            TvButton("${source.label} anterior", { controller.pageStep(-1) })
            TvButton("Próxima ${source.label.lowercase()}", { controller.pageStep(1) })
            TvButton("Velocidade: ${state.rate}×", controller::cycleRate)
            TvButton(state.voices.firstOrNull { it.id == state.voiceId }?.label ?: "Voz indisponível", controller::cycleVoice)
            TvButton("Fonte −", { controller.pause(); fontSize = (fontSize - 2).coerceAtLeast(18); preferences.edit().putInt("font-size", fontSize).apply() })
            TvButton("Fonte +", { controller.pause(); fontSize = (fontSize + 2).coerceAtMost(40); preferences.edit().putInt("font-size", fontSize).apply() })
            TvButton(if (translated) "Usar texto original" else "Traduzir para português", {
                controller.pause()
                if (!source.configured) configure = true else {
                    translated = !translated; source.useTranslation(translated); controller.refreshText()
                }
            })
            TvButton("Configurar computador", { controller.pause(); configure = true })
            TvButton("Atualizar vozes", controller::retryVoices)
            TvButton("Vozes nas configurações do Android", {
                controller.pause()
                try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                catch (_: Exception) { hint = "Abra as configurações da Box e procure Saída de texto para voz. A disponibilidade depende do Android instalado." }
            })
            TvButton("Ver texto", { controller.pause(); controls = false })
            TvButton(if (leaving) "Salvando…" else "Voltar à página", leave)
        }
        Text(if (controls) "A voz começa somente ao escolher Ouvir. ↑ ↓ ← → navegam pelas opções." else
            "OK  Pausar e opções · ↑ ↓  Rolar · ← →  Trocar página · Voltar  Imagem", fontSize = 14.sp, color = TvColors.Muted)
    }
    if (configure) TranslationSettingsDialog(settings, { value ->
        controller.pause()
        source.configure(value.client(), narrationDigest(value.address + "|" + value.language))
        settings = value; translated = false; controller.refreshText()
    }, { configure = false })
}

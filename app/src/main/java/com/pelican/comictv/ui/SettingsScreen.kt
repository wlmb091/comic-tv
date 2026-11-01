package com.pelican.comictv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.pelican.comictv.BuildConfig
import com.pelican.comictv.reader.ImagePreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable internal fun SettingsScreen(preferences: AppearancePreferences, storage: com.pelican.comictv.storage.StorageViewModel,
    backup: com.pelican.comictv.backup.BackupViewModel, onDownloads: (() -> Unit)? = null,
    updater: com.pelican.comictv.updates.AppUpdateViewModel? = null, installUpdate: (java.io.File) -> Unit = {},
    orientation: AppOrientationPreferences? = null, onBack: () -> Unit) {
    val options by preferences.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var accessibility by rememberSaveable { mutableStateOf(true) }
    var storageOpen by rememberSaveable { mutableStateOf(false) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var updatesOpen by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf(false) }
    var themeHelp by remember { mutableStateOf(false) }
    if (themeHelp) com.pelican.comictv.library.LibraryDialog("Sobre os temas", { themeHelp = false }) {
        Text("O tema muda fundos, barras, cartões, botões, foco e a cor da marca. Os arquivos originais das capas e páginas são preservados. O filtro do carrossel tem seu próprio ajuste nesta tela.", color = TvColors.Muted)
        TvButton("Fechar", { themeHelp = false })
    }
    if (diagnostics) LoadingDiagnosticsDialog({ diagnostics = false })
    val changes = remember { Mutex() }
    fun save(change: (AppearanceOptions) -> AppearanceOptions) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { changes.withLock {
            saving = true
            error = null
            try { preferences.update(change(preferences.state.value)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Não foi possível salvar." }
            finally { saving = false }
        } }
    }
    val themeCellWidth = 208.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val sections = androidx.compose.foundation.lazy.rememberLazyListState()
    val firstSection = preferredContentEntryModifier(prepare = { sections.scrollToItem(0) })
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Configurações", "Comic TV · versão " + BuildConfig.VERSION_NAME)
        LazyRow(state = sections, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TvChoice("Acessibilidade e aparência", accessibility && !storageOpen && !backupOpen && !updatesOpen, { accessibility = true; storageOpen = false; backupOpen = false; updatesOpen = false }, firstSection) }
            item { TvChoice("Controle remoto", !accessibility && !storageOpen && !backupOpen && !updatesOpen, { accessibility = false; storageOpen = false; backupOpen = false; updatesOpen = false }) }
            item { TvChoice("Armazenamento", storageOpen && !backupOpen && !updatesOpen, { storageOpen = true; backupOpen = false; updatesOpen = false; storage.refresh() }) }
            item { TvChoice("Backup e restauração", backupOpen && !updatesOpen, { backupOpen = true; storageOpen = false; updatesOpen = false }) }
            if (updater != null) item { TvChoice("Atualizações", updatesOpen, { updatesOpen = true; backupOpen = false; storageOpen = false }) }
        }
        if (updatesOpen && updater != null) {
            com.pelican.comictv.updates.AppUpdateSettings(updater, installUpdate, Modifier.weight(1f))
            return@Column
        } else if (backupOpen) {
            com.pelican.comictv.backup.BackupSettings(backup, Modifier.weight(1f))
            return@Column
        }
        if (storageOpen) {
            com.pelican.comictv.storage.StorageSettings(storage, onBack, Modifier.weight(1f), onDownloads)
            return@Column
        }
        LazyVerticalGrid(GridCells.Adaptive(themeCellWidth), Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { message -> settingsRow("error") { Text(message, color = TvColors.Amber) } }
            if (accessibility) {
                if (orientation != null) {
                    settingsRow("orientation-title") { Text("Orientação da tela", fontSize = 23.sp) }
                    settingsRow("orientation-choices") {
                        val current = LocalAppOrientation.current
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppOrientation.entries.forEach { choice ->
                                TvChoice(choice.label, current == choice, { orientation.update(choice) })
                            }
                        }
                    }
                    settingsRow("orientation-description") {
                        Text("Vale para o app inteiro e fica salva ao reabrir. Escolha o sentido em que o projetor ou a TV está virado.", color = TvColors.Muted)
                    }
                }
                settingsRow("themes-title") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.heightIn(min = 48.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                            Text("Temas da interface", fontSize = 23.sp)
                        }
                        TvButton("Sobre os temas", { themeHelp = true })
                    }
                }
                ColorPreset.entries.filter { it.selectable }.forEach { preset -> item(key = "theme-${preset.name}") {
                    ThemePresetCard(preset, options.preset == preset, { save { it.copy(preset = preset) } })
                } }
                settingsRow("cinema-title") { Text("Arte cinematográfica", fontSize = 23.sp) }
                settingsRow("cinema-description") { Text("Flix, Prime, Max e Cinema mostram automaticamente o fundo da edição em foco. A arte horizontal escolhida em Detalhes → Mais opções tem prioridade; quando não houver, será usada a própria capa, ampliada com proporção preservada e recorte para preencher a área. Alto contraste mantém o fundo preto. O Comic TV original mantém sua estante.", color = TvColors.Muted) }
                settingsRow("carousel-motion-title") { Text("Carrossel", fontSize = 23.sp) }
                settingsRow("carousel-autoplay") {
                    TvChoice("Troca automática: " + if (options.carouselAutoplay) "ligada" else "desligada",
                        options.carouselAutoplay, { save { it.copy(carouselAutoplay = !it.carouselAutoplay) } })
                }
                settingsRow("carousel-motion-description") {
                    Text("Em Flix, Max e Cinema, a troca automática alterna as edições a cada 8 segundos, com transição suave e imagem parada. Ao desligá-la, o destaque fica parado. O filtro de imagem permanece como foi escolhido.", color = TvColors.Muted)
                }
                settingsRow("carousel-filter-title") { Text("Filtro das imagens do carrossel", fontSize = 23.sp) }
                settingsRow("carousel-filter-presets") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ImagePreset.entries.forEach { preset ->
                            TvChoice(if (preset == ImagePreset.Original) "Desligado · Original" else preset.label,
                                options.carouselImagePreset == preset, { save { it.copy(carouselImagePreset = preset) } })
                        }
                    }
                }
                settingsRow("carousel-filter-intensity") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Intensidade", fontSize = 18.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("Suave", "Média", "Forte").forEachIndexed { intensity, label ->
                                TvChoice(label, options.carouselImageIntensity == intensity,
                                    { save { it.copy(carouselImageIntensity = intensity) } })
                            }
                        }
                    }
                }
                settingsRow("carousel-filter-description") {
                    Text("Ajusta as imagens grandes dos destaques de Flix, Max e Cinema, sem alterar os arquivos originais. Original desliga o filtro; a intensidade fica guardada para quando você ativar novamente. Este ajuste não muda Prime, a estante Comic TV nem os filtros do leitor.", color = TvColors.Muted)
                }
                settingsRow("background-title") { Text("Fundo dos detalhes", fontSize = 23.sp) }
                settingsRow("background-toggle") { TvButton(if (options.coverBackground) "Fundo: capa desfocada" else "Fundo: identidade Comic TV",
                    { save { it.copy(coverBackground = !it.coverBackground) } }) }
                settingsRow("background-description") { Text("Nos detalhes: capa desfocada quando ativado. Esta opção é independente da arte horizontal da biblioteca. Em alto contraste, fundo preto.", color = TvColors.Muted) }
                settingsRow("text-title") { Text("Tamanho dos textos", fontSize = 23.sp) }
                settingsRow("text-control") {
                    TextScaleControl(options.textPercentage,
                        onStep = { step -> save { it.withTextPercentage(it.textPercentage + step * 5) } },
                        onSet = { percent -> save { it.withTextPercentage(percent) } })
                }
                settingsRow("text-description") { Text("Ajusta os textos da interface. Use ← → para diminuir ou aumentar. No EPUB de texto, a leitura também tem seu próprio ajuste.", color = TvColors.Muted) }
                settingsRow("contrast-toggle") { TvButton("Alto contraste: " + if (options.highContrast) "ligado" else "desligado",
                    { save { it.copy(highContrast = !it.highContrast) } }) }
                settingsRow("contrast-description") { Text("Alto contraste usa painéis pretos, textos secundários brancos e contorno de foco reforçado nos botões.", color = TvColors.Muted) }
                settingsRow("reset") { TvButton("Restaurar aparência padrão", { save { AppearanceOptions() } }) }
                settingsRow("saving") { if (saving) Text("Salvando…", color = TvColors.Amber) }
            } else {
                settingsRow("remote-help") { Text("""← →  Página anterior / próxima
↑ ↓  Aumentar / reduzir zoom
OK rápido  Alternar zoom/mover
Segurar OK 1s  Abrir ferramentas
Mover página  Deslocar com as quatro setas
Voltar  Sair do modo ativo e retornar""",
                    fontSize = 20.sp, lineHeight = 32.sp) }
                settingsRow("remote-library") { Text("Na biblioteca: segure OK por 1 segundo sobre uma capa ou leitura recente para abrir as opções.", color = TvColors.Muted) }
                settingsRow("diagnostics") { TvButton("Diagnóstico de carregamento", { diagnostics = true }) }
            }
            settingsRow("back") { TvButton("Voltar à biblioteca", onBack) }
        }
    }
}

@Composable internal fun ThemePresetCard(preset: ColorPreset, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 84.dp).semantics { this.selected = selected },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(12.dp), scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(containerColor = TvColors.ButtonSurface,
            contentColor = if (selected) TvColors.Amber else Color.White,
            focusedContainerColor = TvColors.Action, focusedContentColor = TvColors.OnAction),
        border = ButtonDefaults.border(border = if (selected) Border(BorderStroke(1.dp, TvColors.Amber)) else Border.None,
            focusedBorder = Border(BorderStroke(if (TvColors.options.highContrast) 3.dp else 2.dp, TvColors.Amber)))) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeSwatches(preset)
            Text((if (selected) "✓ " else "") + preset.label, fontSize = 17.sp)
        }
    }
}

@Composable private fun ThemeSwatches(preset: ColorPreset) {
    Row(Modifier.fillMaxWidth().height(28.dp)
        .background(if (preset.hasNeutralBackground) TvColors.NeutralBackground else Color(preset.background), RoundedCornerShape(6.dp))
        .border(1.dp, Color(preset.accent).copy(alpha = .6f), RoundedCornerShape(6.dp))
        .padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(18.dp).fillMaxHeight().background(Color(preset.panel)))
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(preset.panel)))
        Box(Modifier.width(40.dp).fillMaxHeight().background(Color(preset.action), RoundedCornerShape(3.dp)))
    }
}

private fun LazyGridScope.settingsRow(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

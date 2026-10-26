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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable fun SettingsScreen(preferences: AppearancePreferences, storage: com.pelican.comictv.storage.StorageViewModel,
    backup: com.pelican.comictv.backup.BackupViewModel, onDownloads: (() -> Unit)? = null, onBack: () -> Unit) {
    val options by preferences.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var accessibility by rememberSaveable { mutableStateOf(true) }
    var storageOpen by rememberSaveable { mutableStateOf(false) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf(false) }
    var themeHelp by remember { mutableStateOf(false) }
    if (themeHelp) com.pelican.comictv.library.LibraryDialog("Sobre os temas", { themeHelp = false }) {
        Text("O tema muda fundos, barras, cartões, botões, foco e a cor da marca. Capas e páginas mantêm suas cores originais.", color = TvColors.Muted)
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Configurações", "Comic TV · versão " + BuildConfig.VERSION_NAME)
        LazyRow(state = sections, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TvChoice("Acessibilidade e aparência", accessibility && !storageOpen && !backupOpen, { accessibility = true; storageOpen = false; backupOpen = false }, firstSection) }
            item { TvChoice("Controle remoto", !accessibility && !storageOpen && !backupOpen, { accessibility = false; storageOpen = false; backupOpen = false }) }
            item { TvChoice("Armazenamento", storageOpen && !backupOpen, { storageOpen = true; backupOpen = false; storage.refresh() }) }
            item { TvChoice("Backup e restauração", backupOpen, { backupOpen = true; storageOpen = false }) }
        }
        if (backupOpen) {
            com.pelican.comictv.backup.BackupSettings(backup, Modifier.weight(1f))
            return@Column
        }
        if (storageOpen) {
            com.pelican.comictv.storage.StorageSettings(storage, onBack, Modifier.weight(1f), onDownloads)
            return@Column
        }
        LazyVerticalGrid(GridCells.Adaptive(themeCellWidth), Modifier.weight(1f), contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { message -> settingsRow("error") { Text(message, color = TvColors.Amber) } }
            if (accessibility) {
                settingsRow("themes-title") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.heightIn(min = 48.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                            Text("Temas da interface", fontSize = 23.sp)
                        }
                        TvButton("Sobre os temas", { themeHelp = true })
                    }
                }
                ColorPreset.entries.forEach { preset -> item(key = "theme-${preset.name}") {
                    ThemePresetCard(preset, options.preset == preset, { save { it.copy(preset = preset) } })
                } }
                settingsRow("background-title") { Text("Fundo dos detalhes", fontSize = 23.sp) }
                settingsRow("background-toggle") { TvButton(if (options.coverBackground) "Fundo: capa desfocada" else "Fundo: identidade Comic TV",
                    { save { it.copy(coverBackground = !it.coverBackground) } }) }
                settingsRow("background-description") { Text("Nos detalhes: capa desfocada quando ativado. Na tela principal: identidade Comic TV desfocada e escura. Em alto contraste, fundo preto.", color = TvColors.Muted) }
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
        contentPadding = PaddingValues(12.dp), scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(containerColor = TvColors.Panel,
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
        .background(Color(preset.background), RoundedCornerShape(6.dp))
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

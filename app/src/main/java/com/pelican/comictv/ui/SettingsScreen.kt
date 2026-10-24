package com.pelican.comictv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun SettingsScreen(preferences: AppearancePreferences, storage: com.pelican.comictv.storage.StorageViewModel, onBack: () -> Unit) {
    val options by preferences.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var accessibility by rememberSaveable { mutableStateOf(true) }
    var storageOpen by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(value: AppearanceOptions) {
        if (saving) return
        scope.launch {
            saving = true
            error = null
            try { preferences.update(value) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Não foi possível salvar." }
            finally { saving = false }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Configurações", "Comic TV · versão " + BuildConfig.VERSION_NAME)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Acessibilidade e aparência", { accessibility = true; storageOpen = false }, primary = accessibility && !storageOpen)
            TvButton("Controle remoto", { accessibility = false; storageOpen = false }, primary = !accessibility && !storageOpen)
            TvButton("Armazenamento", { storageOpen = true; storage.refresh() }, primary = storageOpen)
        }
        if (storageOpen) {
            com.pelican.comictv.storage.StorageSettings(storage, onBack, Modifier.weight(1f))
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { message -> item { Text(message, color = TvColors.Amber) } }
            if (accessibility) {
                item { Text("Temas da interface", fontSize = 23.sp) }
                item { Text("O tema muda fundos, barras, cartões, botões e foco. A logo e as páginas mantêm suas cores.", color = TvColors.Muted) }
                ColorPreset.entries.forEach { preset -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeSwatches(preset)
                        TvButton((if (options.preset == preset) "✓ " else "") + preset.label,
                            { save(options.copy(preset = preset)) }, primary = options.preset == preset)
                    }
                } }
                item { Text("Fundo dos detalhes", fontSize = 23.sp) }
                item { TvButton(if (options.coverBackground) "Fundo: capa desfocada" else "Fundo: identidade Comic TV",
                    { save(options.copy(coverBackground = !options.coverBackground)) }) }
                item { Text("Nos detalhes: capa desfocada quando ativado. Na tela principal: identidade Comic TV desfocada e escura. Em alto contraste, fundo preto.", color = TvColors.Muted) }
                item { Text("Tamanho dos textos", fontSize = 23.sp) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Padrão", "Maior · 115%", "Extra · 130%").forEachIndexed { index, label ->
                            TvButton((if (options.textSize == index) "✓ " else "") + label,
                                { save(options.copy(textSize = index)) }, primary = options.textSize == index)
                        }
                    }
                }
                item { Text("Ajusta os textos da interface. No EPUB de texto, o tamanho da leitura também tem seu próprio ajuste.", color = TvColors.Muted) }
                item { TvButton("Alto contraste: " + if (options.highContrast) "ligado" else "desligado",
                    { save(options.copy(highContrast = !options.highContrast)) }) }
                item { Text("Alto contraste usa painéis pretos, textos secundários brancos e contorno de foco reforçado nos botões.", color = TvColors.Muted) }
                item { TvButton("Restaurar aparência padrão", { save(AppearanceOptions()) }) }
                item { if (saving) Text("Salvando…", color = TvColors.Amber) }
            } else {
                item { Text("""← →  Página anterior / próxima
↑ ↓  Aumentar / reduzir zoom
OK rápido  Alternar zoom/mover
Segurar OK 1s  Abrir ferramentas
Mover página  Deslocar com as quatro setas
Voltar  Sair do modo ativo e retornar""",
                    fontSize = 20.sp, lineHeight = 32.sp) }
                item { Text("Na biblioteca: segure OK por 1 segundo sobre uma capa ou leitura recente para abrir as opções.", color = TvColors.Muted) }
            }
            item { TvButton("Voltar à biblioteca", onBack) }
        }
    }
}

@Composable private fun ThemeSwatches(preset: ColorPreset) {
    Row(Modifier.width(208.dp).height(28.dp)
        .background(Color(preset.background), RoundedCornerShape(6.dp))
        .border(1.dp, Color(preset.accent).copy(alpha = .6f), RoundedCornerShape(6.dp))
        .padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(18.dp).fillMaxHeight().background(Color(preset.panel)))
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(preset.panel)))
        Box(Modifier.width(40.dp).fillMaxHeight().background(Color(preset.action), RoundedCornerShape(3.dp)))
    }
}

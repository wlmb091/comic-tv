package com.pelican.comictv.ui

import androidx.compose.foundation.layout.*
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

@Composable fun SettingsScreen(preferences: AppearancePreferences, onBack: () -> Unit) {
    val options by preferences.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var accessibility by rememberSaveable { mutableStateOf(true) }
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
            TvButton("Acessibilidade e aparência", { accessibility = true }, primary = accessibility)
            TvButton("Controle remoto", { accessibility = false }, primary = !accessibility)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            error?.let { message -> item { Text(message, color = TvColors.Amber) } }
            if (accessibility) {
                item { Text("Cores da interface", fontSize = 23.sp) }
                item { Text("O preset muda os destaques, botões e foco. As páginas dos quadrinhos mantêm suas cores.", color = TvColors.Muted) }
                ColorPreset.entries.forEach { preset -> item {
                    TvButton((if (options.preset == preset) "✓ " else "") + preset.label,
                        { save(options.copy(preset = preset)) }, primary = options.preset == preset)
                } }
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

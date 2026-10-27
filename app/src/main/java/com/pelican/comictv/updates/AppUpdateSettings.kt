package com.pelican.comictv.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.BuildConfig
import com.pelican.comictv.ui.*
import java.util.Locale

@Composable internal fun AppUpdateSettings(model: AppUpdateViewModel, install: (java.io.File) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsStateWithLifecycle()
    AppUpdateContent(state, onPrimary = {
        when (state.phase) {
            AppUpdatePhase.Checking, AppUpdatePhase.Downloading, AppUpdatePhase.Verifying -> model.cancel()
            AppUpdatePhase.Ready -> model.install(install)
            AppUpdatePhase.Available -> model.download()
            AppUpdatePhase.Failed -> if (state.update != null) model.download() else model.check()
            AppUpdatePhase.Installing -> Unit
            else -> model.check()
        }
    }, onCheck = model::check, modifier = modifier)
}

@Composable internal fun AppUpdateContent(state: AppUpdateState, onPrimary: () -> Unit, onCheck: () -> Unit,
    modifier: Modifier = Modifier) {
    val notes = rememberLazyListState()
    val primaryFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }
    var notesFocused by remember { mutableStateOf(false) }
    var checkFocused by remember { mutableStateOf(false) }
    val canRecheck = state.phase in setOf(AppUpdatePhase.Available, AppUpdatePhase.Ready) ||
        (state.phase == AppUpdatePhase.Failed && state.update != null)
    var showCheck by remember { mutableStateOf(canRecheck) }
    // Keep the secondary composed until its focus has moved to the stable primary.
    LaunchedEffect(canRecheck) {
        if (canRecheck || !checkFocused || primaryFocus.requestFocus()) showCheck = canRecheck
    }
    val status = when (state.phase) {
        AppUpdatePhase.Idle -> "Verifique se há uma nova versão"
        AppUpdatePhase.Checking -> "Consultando atualizações…"
        AppUpdatePhase.Current -> "Você está na versão mais recente"
        AppUpdatePhase.Available -> "Nova versão disponível"
        AppUpdatePhase.Downloading -> "Baixando atualização…"
        AppUpdatePhase.Verifying -> "Conferindo atualização…"
        AppUpdatePhase.Ready -> "Pronta para instalar"
        AppUpdatePhase.Installing -> "Aguardando confirmação do Android"
        AppUpdatePhase.Failed -> "Não foi possível concluir"
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().background(TvColors.Panel, RoundedCornerShape(8.dp))
            .border(1.dp, TvColors.Track, RoundedCornerShape(8.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Atualizações do Comic TV", fontSize = 22.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Instalada  ${BuildConfig.VERSION_NAME}", color = TvColors.Muted, fontSize = 18.sp)
                state.update?.let { Text("Disponível  ${it.versionName}", color = TvColors.Amber, fontSize = 18.sp) }
            }
            Text(status, fontSize = 20.sp)
        }
        val label = when (state.phase) {
            AppUpdatePhase.Checking, AppUpdatePhase.Downloading, AppUpdatePhase.Verifying -> "Cancelar"
            AppUpdatePhase.Ready -> "Instalar atualização"
            AppUpdatePhase.Installing -> "Aguardando o Android…"
            AppUpdatePhase.Available -> "Baixar atualização"
            AppUpdatePhase.Failed -> if (state.update != null) "Baixar novamente" else "Verificar atualizações"
            else -> "Verificar atualizações"
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // One stable focus target survives checking/downloading/ready/cancellation.
            TvButton(label, onPrimary, Modifier.testTag("update-primary").focusRequester(primaryFocus)
                .focusProperties { down = notesFocus })
            if (showCheck) TvButton("Verificar novamente", {
                if (canRecheck) { primaryFocus.requestFocus(); onCheck() }
            }, Modifier.testTag("update-check").focusProperties { down = notesFocus }
                .onFocusChanged { checkFocused = it.hasFocus })
        }
        if (state.phase == AppUpdatePhase.Downloading) {
            val total = (state.update?.sizeBytes ?: 1L).coerceAtLeast(1L)
            val received = state.received.coerceIn(0L, total)
            val fraction = received.toFloat() / total
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${(received.toDouble() / total * 100).toInt()}%", fontSize = 22.sp)
                    Text(String.format(Locale.forLanguageTag("pt-BR"), "%.1f / %.1f MiB",
                        received / 1048576.0, total / 1048576.0), fontSize = 18.sp, color = TvColors.Muted)
                }
                Box(Modifier.testTag("update-progress").progressSemantics(fraction).fillMaxWidth()
                    .height(6.dp).background(TvColors.Track, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(TvColors.Amber, RoundedCornerShape(3.dp)))
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("update-notes")
            .border(if (notesFocused) 2.dp else 1.dp, if (notesFocused) TvColors.Amber else TvColors.Track, RoundedCornerShape(8.dp))
            .focusRequester(notesFocus).onFocusChanged { notesFocused = it.hasFocus }
            .focusProperties { up = primaryFocus }.textPanelScroll(notes), state = notes,
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.message?.let { message -> item(key = "message") { Text(message, color = TvColors.Amber, fontSize = 16.sp) } }
            item {
                Text("↑ ↓ Rolar novidades · ↑ no início volta aos botões", fontSize = 16.sp, color = TvColors.Muted)
                Text("O download começa quando você escolhe Baixar. A instalação precisa da confirmação do Android.", fontSize = 16.sp, color = TvColors.Muted)
            }
            state.update?.notes?.takeIf { it.isNotBlank() }?.let { text -> item {
                Text("Novidades", fontSize = 20.sp)
                Text(text, fontSize = 17.sp, lineHeight = 25.sp)
            } }
        }
    }
}

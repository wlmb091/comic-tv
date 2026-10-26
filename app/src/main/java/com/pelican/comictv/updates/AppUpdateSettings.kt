package com.pelican.comictv.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.BuildConfig
import com.pelican.comictv.ui.*

@Composable internal fun AppUpdateSettings(model: AppUpdateViewModel, install: (java.io.File) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsStateWithLifecycle()
    val notes = rememberLazyListState()
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Atualizações do Comic TV", fontSize = 24.sp)
        Text("Versão instalada: ${BuildConfig.VERSION_NAME}", color = TvColors.Muted)
        state.update?.let { Text("Versão disponível: ${it.versionName}", color = TvColors.Amber) }
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
            TvButton(label, {
                when (state.phase) {
                    AppUpdatePhase.Checking, AppUpdatePhase.Downloading, AppUpdatePhase.Verifying -> model.cancel()
                    AppUpdatePhase.Ready -> model.install(install)
                    AppUpdatePhase.Available -> model.download()
                    AppUpdatePhase.Failed -> if (state.update != null) model.download() else model.check()
                    AppUpdatePhase.Installing -> Unit
                    else -> model.check()
                }
            }, Modifier.testTag("update-primary"))
            TvButton("Verificar novamente", model::check)
        }
        if (state.phase == AppUpdatePhase.Downloading) {
            val total = state.update?.sizeBytes ?: 1L
            val fraction = (state.received.toFloat() / total).coerceIn(0f, 1f)
            Text("Baixando: ${(fraction * 100).toInt()}% · ${state.received / 1024} / ${total / 1024} KiB")
            Box(Modifier.fillMaxWidth().height(6.dp).background(TvColors.Track)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(TvColors.Amber))
            }
        }
        if (state.phase == AppUpdatePhase.Checking) Text("Consultando o canal de atualizações…", color = TvColors.Muted)
        if (state.phase == AppUpdatePhase.Verifying) Text("Conferindo o APK antes de instalar…", color = TvColors.Muted)
        state.message?.let { Text(it, color = TvColors.Amber, fontSize = 16.sp) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().textPanelScroll(notes), state = notes, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("O app consulta atualizações uma vez ao abrir. O download começa somente quando você escolhe Baixar. O Android pede sua confirmação para instalar.", fontSize = 16.sp, color = TvColors.Muted) }
            state.update?.notes?.takeIf { it.isNotBlank() }?.let { text -> item {
                Text("Novidades", fontSize = 20.sp)
                Text(text, fontSize = 17.sp, lineHeight = 25.sp)
            } }
        }
    }
}

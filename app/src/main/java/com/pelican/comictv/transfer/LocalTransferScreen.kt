package com.pelican.comictv.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*

@Composable fun LocalTransferScreen(viewModel: LocalTransferViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); viewModel.stop() }
    }
    BackHandler { viewModel.stop(); onBack() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(36.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        ScreenTitle("Importar pela rede local", "Envie quadrinhos pelo celular ou computador na mesma rede da TV.")
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (state.status.running) "Sessão ativa" else "Uma conexão direta com sua TV", fontSize = 22.sp, color = TvColors.Amber)
                state.url?.let { Text(it, fontSize = 16.sp, lineHeight = 22.sp) }
                Text(state.message ?: if (state.starting) "Preparando transferência…" else state.status.message,
                    fontSize = 17.sp, lineHeight = 23.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                if (state.status.running) {
                    Text("Dispositivos ativos: ${state.status.clients} · Envios processados: ${state.status.completed}", color = TvColors.Muted, fontSize = 15.sp)
                    if (state.status.totalBytes > 0) ProgressTrack(state.status.receivedBytes.toFloat() / state.status.totalBytes, Modifier.fillMaxWidth())
                }
                Text("Escaneie o QR ou digite o endereço completo. A sessão dura até 15 minutos e termina ao sair desta tela.", color = TvColors.Muted, fontSize = 15.sp, lineHeight = 21.sp)
                if (!state.status.running) Text("Se não conseguir acessar, confira a rede. Redes de convidados podem bloquear a comunicação entre os dispositivos.", color = TvColors.Muted, fontSize = 15.sp, lineHeight = 21.sp)
            }
            state.qr?.let { bitmap ->
                Image(bitmap.asImageBitmap(), "QR code do endereço temporário de envio", Modifier.size(250.dp).background(Color.White).padding(8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TvButton(if (state.status.running) "Parar servidor" else if (state.starting) "Preparando…" else "Iniciar transferência", {
                if (state.status.running) viewModel.stop() else viewModel.start()
            }, Modifier.focusRequester(first), primary = true)
            TvButton("Voltar", { viewModel.stop(); onBack() })
        }
    }
}

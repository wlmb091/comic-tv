package com.pelican.comictv.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

@Composable fun FtpTransferScreen(viewModel: FtpTransferViewModel, onBack: () -> Unit) {
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
    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle("Receber por FTP", "Envie quadrinhos com um cliente FTP na mesma rede da TV.")
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.status.running) "Sessão ativa · modo passivo" else "Uma conexão temporária com sua TV", color = TvColors.Amber, fontSize = 22.sp)
                state.url?.let { Text("ftp://${it.substringAfter('@')}", fontSize = 22.sp) }
                state.username?.let { Text("Usuário: $it", fontSize = 18.sp) }
                state.password?.let { Text("Senha: $it", fontSize = 18.sp) }
                Text(state.message ?: if (state.starting) "Preparando servidor…" else state.status.message,
                    fontSize = 17.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.status.running) {
                    Text("Conexões: ${state.status.clients} · Importados: ${state.status.completed}", color = TvColors.Muted, fontSize = 16.sp)
                    if (state.status.activeUploads > 0) Text("Recebidos: ${state.status.receivedBytes / 1024 / 1024} MiB", fontSize = 16.sp)
                }
                if (!state.status.running) Text("No cliente, escolha FTP e modo passivo. Envie os arquivos diretamente para a raiz.", color = TvColors.Muted, fontSize = 17.sp, lineHeight = 23.sp)
            }
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.qr?.let { Image(it.asImageBitmap(), "QR code com endereço e acesso FTP temporários",
                    Modifier.size(200.dp).background(Color.White).padding(8.dp)) }
                Text("Até 1 GiB por arquivo · 15 minutos. A sessão termina ao sair. O QR inclui o acesso.", color = TvColors.Muted, fontSize = 14.sp, lineHeight = 18.sp)
                Text("FTP sem criptografia. Use somente na sua rede local.", color = TvColors.Muted, fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TvButton(if (state.status.running) "Parar servidor FTP" else if (state.starting) "Preparando…" else "Iniciar servidor FTP", {
                if (state.status.running) viewModel.stop() else viewModel.start()
            }, Modifier.focusRequester(first), primary = true)
            TvButton("Voltar", { viewModel.stop(); onBack() })
        }
    }
}

package com.pelican.comictv.companion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*

@Composable internal fun CompanionScreen(controller:CompanionController,onBack:()->Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val first=remember {FocusRequester()};LaunchedEffect(Unit){withFrameNanos{};first.requestFocus()}
    val qrFocus=remember {FocusRequester()}
    LaunchedEffect(state.url){if(state.url!=null){withFrameNanos{};withFrameNanos{};runCatching{qrFocus.requestFocus()}}}
    Column(Modifier.fillMaxSize().background(TvColors.Background).verticalScroll(rememberScrollState()).padding(48.dp,27.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        ScreenTitle("Comic TV no celular","Funciona no Safari do iPhone e em navegadores Android, sem instalar outro app.")
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            TvButton(if(state.url!=null)"Encerrar sessão" else if(state.busy)"Preparando…" else "Iniciar conexão por QR code",{
                if(state.url!=null)controller.stop() else controller.start()
            },Modifier.focusRequester(first))
            TvButton("Voltar ao aplicativo",onBack)
        }
        Text(state.message,fontSize=18.sp,color=TvColors.Amber)
        state.qr?.let {qr->Image(qr.asImageBitmap(),"QR code da sessão privada do celular",Modifier.focusRequester(qrFocus).focusable().size(230.dp).background(Color.White).padding(8.dp))}
        state.url?.let {Text(it,fontSize=17.sp)}
        Text("Biblioteca e leitura independente · Controle e prévia da TV · Envio e importação · Download de originais locais",fontSize=18.sp)
        Text("Celular e TV precisam estar na mesma rede. Quem receber este QR terá acesso à biblioteca e à tela durante a sessão. A conexão é local HTTP; use uma rede confiável. Não compartilhe o endereço. A sessão dura até 30 minutos e termina ao sair do Comic TV ou ao encerrá-la abaixo.",fontSize=16.sp,color=TvColors.Muted)
        Text("A prévia da TV tem atualização moderada e pode não exibir vídeo ou conteúdo protegido. A leitura no celular não altera o progresso da TV.",fontSize=16.sp,color=TvColors.Muted)
        Text("Voltar ao aplicativo mantém a conexão ativa enquanto o Comic TV estiver aberto.",fontSize=16.sp,color=TvColors.Muted)
    }
}

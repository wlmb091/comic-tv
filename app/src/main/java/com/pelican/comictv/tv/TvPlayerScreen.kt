package com.pelican.comictv.tv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvButton

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun TvPlayerScreen(controller:TvPlaybackController) {
    val state by controller.state.collectAsState()
    var aspect by remember { mutableIntStateOf(0) }
    val aspects=listOf("Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,"Preencher" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,"Esticar" to AspectRatioFrameLayout.RESIZE_MODE_FILL)
    Column(Modifier.fillMaxSize().background(Color.Black),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(state.title,Modifier.padding(horizontal=12.dp),color=Color.White,fontSize=20.sp,maxLines=2)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory={context->PlayerView(context).apply { useController=true }},
                update={it.player=controller.player;it.resizeMode=aspects[aspect].second},onRelease={it.player=null},modifier=Modifier.fillMaxSize())
        }
        if(state.message.isNotBlank()||state.buffering)Text(state.message.ifBlank { "Aguardando dados…" },Modifier.padding(horizontal=12.dp),color=Color.White)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            TvButton("−30 s",{controller.seekTo(state.positionMs-30_000)})
            TvButton(if(state.playing)"Pausar"else "Continuar",{if(state.playing)controller.pause()else controller.resume()})
            TvButton("+30 s",{controller.seekTo(state.positionMs+30_000)})
            TvButton("Imagem: ${aspects[aspect].first}",{aspect=(aspect+1)%aspects.size})
            TvButton("Voltar às fontes",{controller.stop()})
        }
    }
}

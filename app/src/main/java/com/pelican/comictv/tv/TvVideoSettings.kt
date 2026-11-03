package com.pelican.comictv.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvChoice
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Vídeo e legendas: the player used for the TV tab and how subtitles look. The phone shows the same options. */
@Composable internal fun TvVideoSettings(repository:TvRepository,modifier:Modifier=Modifier) {
    val stored by repository.state.collectAsState()
    val prefs=stored.video
    val p=prefs.subtitles
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var message by remember { mutableStateOf("") }
    fun save(change:(TvVideoPrefs)->TvVideoPrefs) {
        scope.launch {
            try { withContext(Dispatchers.IO) { repository.video(change) };message="" }
            catch(_:Exception) { message="Não foi possível salvar. Tente novamente." }
        }
    }
    fun subs(change:(TvSubtitlePrefs)->TvSubtitlePrefs)=save { it.copy(subtitles=change(it.subtitles)) }
    LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=8.dp)) {
        item { Text("Player de vídeo",fontSize=23.sp) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TvVideoPrefs.PLAYERS.forEach { id ->
                    val installed=id=="internal"||id=="ask"||TvExternalPlayers.installed(context,id)!=null
                    TvChoice(TvVideoPrefs.PLAYER_NAMES[id].orEmpty()+if(installed)"" else " · não instalado",prefs.player==id,{save { it.copy(player=id) }})
                }
            }
        }
        item { Text("Como no Stremio: o Comic TV encontra as fontes e o player escolhido reproduz. Em torrents, o Comic TV continua baixando enquanto o player externo estiver aberto. Legendas automáticas são enviadas junto.",color=TvColors.Muted) }
        item { TvChoice("Trackers públicos nos torrents: "+if(prefs.publicTrackers)"ligados" else "desligados",prefs.publicTrackers,{save { it.copy(publicTrackers=!it.publicTrackers) }}) }
        item { Text("Ligados, todo torrent recebe uma lista de trackers públicos além dos do complemento, o que costuma achar conexões mais rápido. Desligados, valem só os do complemento e a rede DHT.",color=TvColors.Muted) }
        item { Text("Legendas",fontSize=23.sp) }
        item { TvChoice("Legendas automáticas: "+if(p.auto)"ligadas" else "desligadas",p.auto,{subs { it.copy(auto=!it.auto) }}) }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Idioma preferido",fontSize=18.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TvChoice("Português (Brasil)",p.preferBrazil,{subs { it.copy(preferBrazil=true) }})
                    TvChoice("Português (Portugal)",!p.preferBrazil,{subs { it.copy(preferBrazil=false) }})
                }
                Text("Se não houver o preferido, vem o outro português e depois o inglês. Legendas do próprio vídeo têm prioridade no mesmo idioma.",color=TvColors.Muted)
            }
        }
        item { TvChoice("Estilo original do ASS (animes): "+if(p.originalAssStyle)"ligado" else "desligado",p.originalAssStyle,{subs { it.copy(originalAssStyle=!it.originalAssStyle) }}) }
        item { Text("Ligado, legendas ASS e SSA aparecem como o autor fez: fontes do MKV, cores, posições e letreiros. Desligado, usam o estilo abaixo. Vale a partir do próximo vídeo.",color=TvColors.Muted) }
        item { Stepper("Tamanho","${p.sizePercent}%",{subs { it.copy(sizePercent=it.sizePercent-10) }},{subs { it.copy(sizePercent=it.sizePercent+10) }}) }
        item { Options("Cor",TvSubtitlePrefs.COLOR_NAMES,p.color) { i->subs { it.copy(color=i) } } }
        item { Options("Fundo",TvSubtitlePrefs.BACKGROUND_NAMES,p.background) { i->subs { it.copy(background=i) } } }
        item { Options("Contorno",TvSubtitlePrefs.EDGE_NAMES,p.edge) { i->subs { it.copy(edge=i) } } }
        item { Stepper("Altura na tela","${p.bottomPercent}%",{subs { it.copy(bottomPercent=it.bottomPercent-2) }},{subs { it.copy(bottomPercent=it.bottomPercent+2) }}) }
        item { Preview(p) }
        item { Text("Tamanho, cor, fundo, contorno e altura valem para SRT e VTT, e também para ASS com o estilo original desligado. A sincronia se ajusta no player, em Legendas.",color=TvColors.Muted) }
        if(message.isNotBlank())item { Text(message,color=TvColors.Amber) }
    }
}

@Composable private fun Stepper(title:String,value:String,minus:()->Unit,plus:()->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,fontSize=18.sp,modifier=Modifier.widthIn(min=150.dp))
        TvButton("−",minus);Text(value,fontSize=18.sp,modifier=Modifier.widthIn(min=60.dp));TvButton("+",plus)
    }
}

@Composable private fun Options(title:String,names:List<String>,selected:Int,choose:(Int)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,fontSize=18.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            names.forEachIndexed { i,name -> TvChoice(name,selected==i,{choose(i)}) }
        }
    }
}

@Composable private fun Preview(p:TvSubtitlePrefs) {
    val color=Color(TvSubtitlePrefs.COLORS[p.color])
    val back=when(p.background) { 0->Color.Transparent;1->Color(0x99000000);else->Color.Black }
    val shadow=when(p.edge) { 0->null;1->Shadow(Color.Black,blurRadius=6f);else->Shadow(Color.Black,androidx.compose.ui.geometry.Offset(3f,3f),4f) }
    Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF3A4A5A),RoundedCornerShape(8.dp)),contentAlignment=Alignment.BottomCenter) {
        Text("Exemplo de legenda, com acentuação",Modifier.padding(bottom=(4+p.bottomPercent).dp).background(back).padding(horizontal=8.dp,vertical=2.dp),
            style=TextStyle(color=color,fontSize=(20*p.sizePercent/100).sp,shadow=shadow))
    }
}

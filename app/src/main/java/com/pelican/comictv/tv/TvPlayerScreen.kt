package com.pelican.comictv.tv

import android.view.KeyEvent as AndroidKey
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvChoice
import com.pelican.comictv.ui.TvColors
import io.github.peerless2012.ass.media.kt.withAssSupport
import kotlinx.coroutines.delay

/** Full-screen player: video, one control bar that hides while playing, and the subtitle and audio panels. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun TvPlayerScreen(controller:TvPlaybackController,repository:TvRepository) {
    val state by controller.state.collectAsState()
    val stored by repository.state.collectAsState()
    val style=stored.video.subtitles
    var aspect by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf("") }
    var interaction by remember { mutableIntStateOf(0) }
    val mainFocus=remember { FocusRequester() }
    val panelFocus=remember { FocusRequester() }
    val nextFocus=remember { FocusRequester() }
    var waitMore by remember(state.suggestNext) { mutableStateOf(false) }
    val offerNext=state.suggestNext&&!waitMore
    val failedWithNext=state.message.isNotBlank()&&state.hasNext&&!state.canPlay
    LaunchedEffect(offerNext,failedWithNext) { if(offerNext||failedWithNext) { delay(80);runCatching { nextFocus.requestFocus() } } }
    val aspects=listOf("Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,"Preencher" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,"Esticar" to AspectRatioFrameLayout.RESIZE_MODE_FILL)
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        controller.externalFinished(TvExternalPlayers.resultPosition(result.data))
    }
    LaunchedEffect(controller) {
        controller.launches.collect { request ->
            try { launcher.launch(request.intent);controller.externalStarted() }
            catch(_:android.content.ActivityNotFoundException) { controller.externalFailed() }
        }
    }
    BackHandler(panel.isNotEmpty()) { panel="";interaction++ }
    // The bar hides after five seconds of playback without a key press; any key brings it back.
    LaunchedEffect(interaction,state.playing,panel,state.canPlay) {
        if(state.playing&&panel.isEmpty()&&state.message.isBlank()) { delay(5000);overlay=false }
    }
    LaunchedEffect(overlay,panel) {
        delay(50)
        runCatching { if(panel.isNotEmpty())panelFocus.requestFocus() else if(overlay)mainFocus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
        if(event.type!=KeyEventType.KeyDown)return@onPreviewKeyEvent false
        interaction++
        if(!overlay&&state.external.isBlank()) {
            when(event.nativeKeyEvent.keyCode) {
                AndroidKey.KEYCODE_BACK->false
                AndroidKey.KEYCODE_DPAD_LEFT->{ if(state.canPlay)controller.seekTo(state.positionMs-10_000);true }
                AndroidKey.KEYCODE_DPAD_RIGHT->{ if(state.canPlay)controller.seekTo(state.positionMs+10_000);true }
                AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE->{ if(state.playing)controller.pause() else controller.resume();true }
                else->{ overlay=true;true }
            }
        } else when(event.nativeKeyEvent.keyCode) {
            AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE->{ if(state.playing)controller.pause() else if(state.canPlay)controller.resume();true }
            else->false
        }
    }) {
        if(state.external.isBlank()) AndroidView(factory={context->PlayerView(context).apply { useController=false;keepScreenOn=true;setShutterBackgroundColor(android.graphics.Color.BLACK) }},
            update={view->
                view.player=controller.player
                view.resizeMode=aspects[aspect].second
                view.subtitleView?.let { applyStyle(it,style,controller.assHandler!=null) }
                val handler=controller.assHandler
                val sub=view.subtitleView
                if(sub!=null&&sub.getTag(com.pelican.comictv.R.id.tv_ass_handler)!==handler) {
                    // A new player brings a new libass handler: drop the old overlay before attaching the new one.
                    (0 until sub.childCount).map { sub.getChildAt(it) }.filter { it.javaClass.name.contains("AssSubtitle") }.forEach(sub::removeView)
                    if(handler!=null)sub.withAssSupport(handler)
                    sub.setTag(com.pelican.comictv.R.id.tv_ass_handler,handler)
                }
            },onRelease={it.player=null},modifier=Modifier.fillMaxSize())
        val status=state.message.ifBlank { state.stage }
        if(state.external.isNotBlank()) Column(Modifier.align(Alignment.Center).padding(32.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(state.title,color=Color.White,fontSize=24.sp,maxLines=2)
            Text(status.ifBlank { "Preparando o vídeo para o ${state.external}…" },color=TvColors.Muted)
            TvButton("Parar e voltar às fontes",{controller.stop()},Modifier.focusRequester(mainFocus))
            if(state.hasNext)TvButton("Tentar próxima fonte",{controller.playNext()},if(offerNext)Modifier.focusRequester(nextFocus) else Modifier)
        } else if(overlay||panel.isNotEmpty()||status.isNotBlank()&&!state.canPlay) {
            Column(Modifier.fillMaxWidth().align(Alignment.TopStart).background(Brush.verticalGradient(listOf(Color(0xCC000000),Color.Transparent))).padding(24.dp)) {
                Text(state.title,color=Color.White,fontSize=22.sp,maxLines=2)
                if(status.isNotBlank())Text(status,color=if(state.message.isNotBlank())TvColors.Amber else Color.White,fontSize=17.sp)
            }
            if(overlay||panel.isNotEmpty())Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xE6000000)))).padding(horizontal=24.dp,vertical=18.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(state.durationMs>0) {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth((state.positionMs.toFloat()/state.durationMs).coerceIn(0f,1f)).fillMaxHeight().background(TvColors.Amber))
                    }
                    Text("${clock(state.positionMs)} / ${clock(state.durationMs)}",color=Color.White,fontSize=15.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    TvButton("−30 s",{controller.seekTo(state.positionMs-30_000);interaction++},enabled=state.canPlay)
                    TvButton(if(state.playing)"Pausar" else "Reproduzir",{if(state.playing)controller.pause() else controller.resume();interaction++},
                        Modifier.focusRequester(mainFocus),primary=true,enabled=state.canPlay)
                    TvButton("+30 s",{controller.seekTo(state.positionMs+30_000);interaction++},enabled=state.canPlay)
                    TvButton("Legendas",{panel=if(panel=="subtitles")"" else "subtitles"},enabled=state.playerReady)
                    if(state.audio.size>1)TvButton("Áudio",{panel=if(panel=="audio")"" else "audio"})
                    TvButton("Imagem: ${aspects[aspect].first}",{aspect=(aspect+1)%aspects.size;interaction++})
                    if(state.hasNext)TvButton("Próxima fonte",{controller.playNext()})
                    TvButton("Voltar às fontes",{controller.stop()})
                }
            }
        }
        if(state.external.isBlank()&&(offerNext||failedWithNext))Column(Modifier.align(Alignment.Center).widthIn(max=560.dp)
            .background(Color(0xF0101010)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(!failedWithNext)"Esta fonte ainda não encontrou conexões. Ela continua tentando enquanto você decide." else state.message,color=Color.White,fontSize=18.sp)
            TvButton("Tentar próxima fonte",{controller.playNext()},Modifier.focusRequester(nextFocus),primary=true)
            if(!failedWithNext)TvButton("Continuar esperando",{waitMore=true;interaction++}) else TvButton("Voltar às fontes",{controller.stop()})
        }
        if(panel.isNotEmpty())Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max=460.dp).background(Color(0xF0101010)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(panel=="subtitles") {
                Text("Legendas",color=Color.White,fontSize=22.sp)
                if(state.subtitleBusy)Text("Abrindo legenda…",color=TvColors.Muted)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    TvButton("−0,5 s",{controller.setSubtitleDelay(state.subtitleDelayMs-500)},Modifier.focusRequester(panelFocus))
                    Text("Sincronia ${delayText(state.subtitleDelayMs)}",color=Color.White)
                    TvButton("+0,5 s",{controller.setSubtitleDelay(state.subtitleDelayMs+500)})
                }
                LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(state.subtitles,key={it.id}) { track -> TvChoice(track.label,track.selected,{controller.selectSubtitle(track.id)},Modifier.fillMaxWidth()) }
                    item { Text("Envie um arquivo SRT, VTT ou ASS pelo painel do celular, na aba TV.",color=TvColors.Muted,fontSize=14.sp) }
                }
            } else {
                Text("Áudio",color=Color.White,fontSize=22.sp)
                LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(state.audio,key={it.id}) { track ->
                        TvChoice(track.label,track.selected,{controller.selectAudio(track.id)},Modifier.fillMaxWidth().then(if(track==state.audio.first())Modifier.focusRequester(panelFocus) else Modifier))
                    }
                }
            }
        }
    }
}

/** Applies the user's subtitle style; ASS keeps its own styles when libass renders it. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun applyStyle(view:SubtitleView,p:TvSubtitlePrefs,assOriginal:Boolean) {
    val background=when(p.background) { 0->android.graphics.Color.TRANSPARENT;1->0x99000000.toInt();else->android.graphics.Color.BLACK }
    val edge=when(p.edge) { 0->CaptionStyleCompat.EDGE_TYPE_NONE;1->CaptionStyleCompat.EDGE_TYPE_OUTLINE;else->CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW }
    view.setStyle(CaptionStyleCompat(TvSubtitlePrefs.COLORS[p.color],background,android.graphics.Color.TRANSPARENT,edge,android.graphics.Color.BLACK,null))
    view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION*p.sizePercent/100f)
    view.setBottomPaddingFraction(p.bottomPercent/100f)
    // Without libass, ASS positions and colors give way to the chosen style.
    view.setApplyEmbeddedStyles(assOriginal)
    view.setApplyEmbeddedFontSizes(assOriginal)
}

internal fun clock(ms:Long):String {
    val s=(ms.coerceAtLeast(0)/1000)
    return if(s>=3600)"%d:%02d:%02d".format(s/3600,s/60%60,s%60) else "%02d:%02d".format(s/60,s%60)
}
private fun delayText(ms:Long)=(if(ms>0)"+" else if(ms<0)"−" else "")+"%.1f s".format(kotlin.math.abs(ms)/1000.0).replace('.',',')

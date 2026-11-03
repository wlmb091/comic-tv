package com.pelican.comictv.tv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvChoice
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.*

@Composable internal fun TvDetailsScreen(preview:TvMeta,repository:TvRepository,playback:TvPlaybackController,
    onBrowser:(String)->Unit,onBack:()->Unit) {
    var meta by remember(preview.type,preview.id) { mutableStateOf(preview) }
    var selectedId by rememberSaveable(preview.type,preview.id) { mutableStateOf<String?>(null) }
    var season by rememberSaveable(preview.type,preview.id) { mutableIntStateOf(-1) }
    var streams by remember { mutableStateOf<List<TvStream>>(emptyList()) }
    var detailBusy by remember { mutableStateOf(true) }
    var sourceBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var resume by remember { mutableStateOf<TvStream?>(null) }
    var sourceFocus by remember { mutableStateOf(selectedId!=null) }
    var lastSource by rememberSaveable { mutableIntStateOf(0) }
    val sourceRequester=remember { FocusRequester() }
    val resumeRequester=remember { FocusRequester() }
    val listState=rememberLazyListState()
    val stored by repository.state.collectAsState()
    val scope=rememberCoroutineScope()
    val videos=meta.videos.filter { it.id.isNotBlank() }.distinctBy { it.id }
    val selected=videos.firstOrNull { it.id==selectedId }?:selectedId?.takeIf { it==meta.id&&videos.isEmpty() }?.let { TvVideo(it,meta.name) }
    val progress=stored.progress.firstOrNull { it.meta.type==meta.type&&it.meta.id==meta.id }
    val filter=SourceFilter.apply(streams,SourceFilter.Choice(stored.video.sourceAddon,stored.video.sourceQuality,stored.video.sourceAudio))
    val shownStreams=filter.shown.map { it.value }
    // The remembered source, or the first one the filters leave.
    val focusIndex=filter.shown.firstOrNull { it.index==lastSource }?.index?:filter.shown.firstOrNull()?.index?:0
    fun choose(change:(TvVideoPrefs)->TvVideoPrefs) {
        scope.launch { try { withContext(Dispatchers.IO) { repository.video(change) } }catch(e:CancellationException){throw e}catch(_:Exception){message="Não foi possível salvar o filtro."} }
    }
    LaunchedEffect(preview.type,preview.id) {
        detailBusy=true
        try {
            meta=repository.details(preview)
            val available=meta.videos.filter { it.id.isNotBlank() }
            if(season<0)season=available.firstOrNull()?.season?:0
            if(available.isEmpty()&&meta.type!="series")selectedId=meta.id
        } finally { detailBusy=false }
    }
    LaunchedEffect(selected?.id,detailBusy,attempt) {
        if(detailBusy)return@LaunchedEffect
        val video=selected?:return@LaunchedEffect
        streams=emptyList();resume=null;sourceBusy=true;message="Procurando fontes…"
        try {
            streams=repository.streams(meta,video)
            message=if(streams.isEmpty())"Nenhuma fonte retornou opções para este vídeo. Confira os complementos instalados."else ""
        } catch(e:CancellationException) { throw e }
        catch(_:Exception) { message="Não foi possível consultar as fontes. Tente novamente." }
        finally { sourceBusy=false }
    }
    LaunchedEffect(streams,sourceFocus) {
        if(sourceFocus&&filter.shown.isNotEmpty()) {
            val index=focusIndex
            lastSource=index
            // Lazy children must be brought into composition before requesting their focus.
            val beforeSources=3+(if(detailBusy)1 else 0)+(if(progress!=null&&videos.isNotEmpty())1 else 0)+
                (if(videos.isNotEmpty())2 else if(!detailBusy&&meta.type=="series")1 else 0)+
                (if(selected!=null)1 else 0)+(if(message.isNotBlank())1 else 0)+
                (if(selected!=null&&!sourceBusy)1 else 0)+(if(resume!=null)1 else 0)+1
            val grouped=filter.shown.groupBy { it.value.addonName }
            var sourceItem=beforeSources
            for((_,group) in grouped) {
                sourceItem++
                val local=group.indexOfFirst { it.index==index }
                if(local>=0) { sourceItem+=local;break }
                sourceItem+=group.size
            }
            listState.scrollToItem(sourceItem)
            withFrameNanos { };runCatching { sourceRequester.requestFocus() };sourceFocus=false
        }
    }
    LaunchedEffect(resume) {
        if(resume!=null) {
            val resumeIndex=3+(if(progress!=null&&videos.isNotEmpty())1 else 0)+
                (if(videos.isNotEmpty())2 else if(!detailBusy&&meta.type=="series")1 else 0)+
                (if(selected!=null)1 else 0)+(if(message.isNotBlank())1 else 0)+
                (if(selected!=null&&!sourceBusy)1 else 0)
            listState.scrollToItem(resumeIndex)
            withFrameNanos { };runCatching { resumeRequester.requestFocus() }
        }
    }
    fun select(video:TvVideo) { selectedId=video.id;resume=null;lastSource=0;sourceFocus=true }
    fun start(stream:TvStream,position:Long=0) { selected?.let { playback.play(meta,it,stream,position,sources=shownStreams.ifEmpty { streams }) } }
    LazyColumn(Modifier.fillMaxSize().background(TvColors.Background).padding(20.dp),state=listState,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item("back") { TvButton("Voltar",onBack) }
        item("summary") {
            BoxWithConstraints {
                val wide=maxWidth>=600.dp
                if(wide)Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    AsyncImage(meta.poster,meta.name,Modifier.width(150.dp).height(215.dp),contentScale=ContentScale.Fit)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) { TvMetaText(meta) }
                } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    AsyncImage(meta.poster,meta.name,Modifier.fillMaxWidth().height(150.dp),contentScale=ContentScale.Fit)
                    TvMetaText(meta)
                }
            }
        }
        item("favorite") {
            TvButton(if(stored.favorites.any { it.id==meta.id&&it.type==meta.type })"Remover da minha lista"else "Adicionar à minha lista",{
                scope.launch { try { withContext(Dispatchers.IO) { repository.favorite(meta) } }catch(e:CancellationException){throw e}catch(_:Exception){message="Não foi possível salvar sua lista."} }
            })
        }
        if(detailBusy)item("details-loading") { Text("Carregando detalhes…",color=TvColors.Muted) }
        if(progress!=null&&videos.isNotEmpty())item("resume-episode") {
            TvButton("Retomar ${progress.video.title}",{season=progress.video.season;select(progress.video)})
        }
        if(videos.isNotEmpty()) {
            item("seasons") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    videos.map { it.season }.distinct().sorted().forEach { number ->
                        TvChoice(if(number>0)"Temporada $number"else "Episódios",season==number,{season=number},compact=true)
                    }
                }
            }
            item("episodes") {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(4.dp)) {
                    items(videos.filter { it.season==season },key={it.id}) { video ->
                        TvChoice("${if(video.episode>0)"${video.episode}. "else ""}${video.title}",selectedId==video.id,{select(video)},Modifier.widthIn(max=300.dp))
                    }
                }
            }
        } else if(!detailBusy&&meta.type=="series")item("missing-episodes") {
            Text("Os complementos não retornaram episódios para esta série.",color=TvColors.Muted)
        }
        if(selected!=null)item("sources-title") { Text("Fontes · ${selected.title}",color=Color.White,fontSize=22.sp) }
        if(message.isNotBlank())item("message") { Text(message,color=TvColors.Muted) }
        if(selected!=null&&!sourceBusy)item("retry-sources") { TvButton("Consultar fontes novamente",{attempt++;sourceFocus=true}) }
        if(resume!=null)item("resume-confirmation") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Há progresso salvo para este vídeo.",color=Color.White)
                TvButton("Continuar",{val stream=resume!!;resume=null;start(stream,progress?.positionMs?:0)},Modifier.focusRequester(resumeRequester))
                TvButton("Começar do início",{val stream=resume!!;resume=null;start(stream)})
                TvButton("Cancelar",{resume=null})
            }
        }
        if(streams.isNotEmpty())item("source-filters") {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterRow("Complemento",filter.addons,filter.applied.addon,showEmpty=true) { v->choose { it.copy(sourceAddon=v) } }
                FilterRow("Qualidade",filter.qualities,filter.applied.quality) { v->choose { it.copy(sourceQuality=v) } }
                FilterRow("Áudio",filter.audios,filter.applied.audio) { v->choose { it.copy(sourceAudio=v) } }
                if(filter.shown.isEmpty()) {
                    Text("Nenhuma fonte com estes filtros.",color=TvColors.Muted)
                    TvButton("Limpar filtros",{choose { it.copy(sourceAddon="",sourceQuality="",sourceAudio="") }})
                } else Text("Qualidade e áudio são lidos do nome da fonte e podem não estar certos.",color=TvColors.Muted,fontSize=14.sp)
            }
        }
        filter.shown.map { it.index to it.value }.groupBy { it.second.addonName }.forEach { (addon,group) ->
            item("addon:$addon") { Text(addon.ifBlank { "Fontes" },color=TvColors.Muted,fontSize=17.sp) }
            items(group,key={it.first}) { (index,stream) ->
                TvButton((if(StreamRank.slow(stream))"Poucos seeders, pode demorar · " else "")+listOf(stream.name,stream.description).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Fonte ${index+1}" }.take(600),{
                    lastSource=index
                    if(stream.externalUrl.isNotBlank()) {
                        if(StremioLinks.http(stream.externalUrl))onBrowser(stream.externalUrl)else message="Este destino externo não é compatível."
                    } else if(progress?.video?.id==selected?.id&&(progress?.positionMs?:0)>0)resume=stream else start(stream)
                },Modifier.fillMaxWidth().then(if(index==focusIndex)Modifier.focusRequester(sourceRequester)else Modifier),enabled=!sourceBusy)
            }
        }
    }
}

/** One row of filter buttons with the number of sources each leaves; empty choices hide, except the add-ons and the one in use. */
@Composable private fun FilterRow(title:String,options:List<SourceFilter.Option>,selected:String,showEmpty:Boolean=false,choose:(String)->Unit) {
    val visible=options.filter { it.value==SourceFilter.ALL||it.value==selected||it.count>0||showEmpty }
    if(visible.size<=2&&selected==SourceFilter.ALL&&!showEmpty)return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        Text(title,color=TvColors.Muted,fontSize=15.sp,modifier=Modifier.widthIn(min=110.dp))
        visible.forEach { option -> TvChoice("${option.label} · ${option.count}",option.value==selected,{choose(option.value)},compact=true) }
    }
}

@Composable private fun TvMetaText(meta:TvMeta) {
    Text(meta.name,color=Color.White,fontSize=30.sp)
    if(meta.genres.isNotEmpty())Text(meta.genres.joinToString(" · "),color=TvColors.Muted)
    if(meta.description.isNotBlank())Text(meta.description,color=Color.White,maxLines=10)
}

package com.pelican.comictv.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.google.gson.Gson
import com.pelican.comictv.ui.*
import kotlinx.coroutines.*
import java.security.MessageDigest

/** URLs remain in private memory; saveable navigation contains only opaque UI keys. */
@Composable internal fun TvScreen(repository:TvRepository,playback:TvPlaybackController,onBrowser:(String)->Unit) {
    val stored by repository.state.collectAsState()
    val playing by playback.state.collectAsState()
    var section by rememberSaveable { mutableStateOf("Início") }
    val metaSaver=remember { Saver<TvMeta?,String>(save={meta->meta?.let { Gson().toJson(it.copy(videos=emptyList(),description=it.description.take(2000))) }},
        restore={raw->runCatching { Gson().fromJson(raw,TvMeta::class.java) }.getOrNull()}) }
    var selected by rememberSaveable(stateSaver=metaSaver) { mutableStateOf<TvMeta?>(null) }
    var restorePoster by rememberSaveable { mutableStateOf<String?>(null) }
    val screens=rememberSaveableStateHolder()
    val memory=remember { TvBrowseMemory() }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,playback) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP)playback.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer);playback.stop() }
    }
    BackHandler(playing.active||selected!=null||section!="Início") {
        when { playing.active->playback.stop();selected!=null->selected=null;else->section="Início" }
    }
    when {
        playing.active -> TvPlayerScreen(playback)
        selected!=null -> {
            val meta=selected!!
            screens.SaveableStateProvider("details:${meta.type}:${meta.id}") {
                TvDetailsScreen(meta,repository,playback,onBrowser) { selected=null }
            }
        }
        else -> screens.SaveableStateProvider("browse:$section") {
            TvBrowseScreen(section,{section=it;restorePoster=null},stored,repository,memory,restorePoster,onBrowser) { meta,key ->
                restorePoster=key;selected=meta
            }
        }
    }
}

private class TvBrowseMemory {
    val pages=mutableStateMapOf<String,List<TvMeta>>()
    val extras=mutableStateMapOf<String,Map<String,String>>()
    val exhausted=mutableStateMapOf<String,Boolean>()
    fun cache(key:String,items:List<TvMeta>) {
        pages[key]=items
        while(pages.size>32) { val oldest=pages.keys.first { it!=key };pages.remove(oldest);exhausted.remove(oldest) }
    }
}
private fun catalogKey(addon:TvAddon,catalog:TvCatalog):String =
    MessageDigest.getInstance("SHA-256").digest((addon.url+"\u0000"+catalog.type+"\u0000"+catalog.id).toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }

@Composable private fun TvBrowseScreen(section:String,onSection:(String)->Unit,stored:TvSnapshot,
    repository:TvRepository,memory:TvBrowseMemory,restorePoster:String?,onBrowser:(String)->Unit,
    onOpen:(TvMeta,String)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val listState=rememberLazyListState()
    val catalogs=remember(section,stored.addons) {
        stored.addons.filter { it.enabled }.flatMap { addon -> addon.manifest.catalogs.filter { catalog ->
            val anime=catalog.type.equals("anime",true)||catalog.name.contains("anime",true)||"${catalog.type}:${catalog.id}" in addon.animeCatalogs
            val matches=when(section) { "Filmes"->catalog.type=="movie"&&!anime;"Séries"->catalog.type=="series"&&!anime;"Animes"->anime;else->true }
            matches&&(section!="Buscar"||catalog.extra.any { it.name=="search" })
        }.map { addon to it } }.distinctBy { catalogKey(it.first,it.second) }.take(40)
    }
    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("TV",color=Color.White,fontSize=28.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Início","Filmes","Séries","Animes","Minha lista","Buscar","Complementos").forEach { name ->
                TvChoice(name,section==name,{onSection(name)},compact=true)
            }
        }
        if(section=="Complementos") { TvAddonsScreen(repository,onBrowser);return@Column }
        if(section=="Buscar") {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                TvTextInput(query,{query=it},Modifier.weight(1f).border(1.dp,TvColors.Muted,RoundedCornerShape(8.dp)).padding(12.dp),
                    maxLength=200,onSubmit={search=query.trim();refresh++})
                TvButton("Buscar",{search=query.trim();refresh++})
            }
        }
        LazyColumn(state=listState,verticalArrangement=Arrangement.spacedBy(18.dp),modifier=Modifier.fillMaxSize()) {
            if(section=="Início"&&stored.progress.isNotEmpty())item("continue") {
                TvRow("Continuar assistindo",stored.progress.asReversed().map { it.meta }.distinctBy { it.type+":"+it.id },"continue",restorePoster,onOpen)
            }
            if(section=="Minha lista")item("favorites") { TvRow("Minha lista",stored.favorites,"favorites",restorePoster,onOpen) }
            if(stored.addons.none { it.enabled })item("empty-addons") {
                Text("Adicione um complemento para carregar catálogos e fontes.",color=Color.White)
                TvButton("Adicionar complemento",{onSection("Complementos")})
            }
            if(section=="Buscar"&&search.isBlank())item("search-help") { Text("Digite um título e escolha Buscar.",color=TvColors.Muted) }
            if(section=="Animes"&&catalogs.isEmpty())item("anime-help") {
                Text("Nenhum catálogo de anime identificado. Em Complementos, marque os catálogos que deseja usar nesta seção.",color=TvColors.Muted)
            }
            if(section=="Buscar"&&search.isNotBlank()&&catalogs.isEmpty())item("search-empty") {
                Text("Os complementos ativos não declaram busca de catálogo.",color=TvColors.Muted)
            }
            if(section!="Minha lista"&&(section!="Buscar"||search.isNotBlank()))items(catalogs,key={catalogKey(it.first,it.second)}) { (addon,catalog) ->
                TvCatalogShelf(addon,catalog,if(section=="Buscar")search else "",refresh,repository,memory,restorePoster,onOpen)
            }
        }
    }
}

@Composable private fun TvCatalogShelf(addon:TvAddon,catalog:TvCatalog,search:String,refresh:Int,
    repository:TvRepository,memory:TvBrowseMemory,restorePoster:String?,onOpen:(TvMeta,String)->Unit) {
    val key=catalogKey(addon,catalog)
    val chosen=memory.extras[key].orEmpty()
    val extras=chosen+(if(search.isNotBlank())mapOf("search" to search)else emptyMap())
    val pageKey=key+":"+extras.entries.sortedBy { it.key }.joinToString { it.key+"="+it.value }
    var metas by remember(pageKey) { mutableStateOf(memory.pages[pageKey].orEmpty()) }
    var loading by remember(pageKey) { mutableStateOf(false) }
    var message by remember(pageKey) { mutableStateOf("") }
    var attempt by remember(pageKey) { mutableIntStateOf(0) }
    var more by remember(pageKey) { mutableStateOf(false) }
    var exhausted by remember(pageKey) { mutableStateOf(memory.exhausted[pageKey]?:false) }
    val scope=rememberCoroutineScope()
    val ready=catalog.canRequest(extras)
    LaunchedEffect(pageKey,attempt,refresh) {
        if(!ready)return@LaunchedEffect
        if(metas.isNotEmpty()&&attempt==0&&refresh==0)return@LaunchedEffect
        loading=true;message=""
        try {
            metas=repository.client.catalog(addon,catalog,extras).distinctBy { it.type+":"+it.id }
            memory.cache(pageKey,metas);exhausted=false;memory.exhausted.remove(pageKey)
        } catch(e:CancellationException) { throw e }
        catch(_:Exception) { message="Este catálogo não respondeu. Você pode tentar novamente." }
        finally { loading=false }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("${catalog.name} · ${addon.manifest.name}",color=Color.White,fontSize=20.sp)
        catalog.extra.filter { it.name !in setOf("search","skip")&&(it.required||it.options.isNotEmpty()) }.forEach { extra ->
            if(extra.options.isNotEmpty()) {
                Text(extra.name,color=TvColors.Muted,fontSize=15.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(!extra.required)TvChoice("Todos",chosen[extra.name].isNullOrBlank(),{memory.extras[key]=chosen-extra.name},compact=true)
                    extra.options.forEach { option -> TvChoice(option,chosen[extra.name]==option,{memory.extras[key]=chosen+(extra.name to option)},compact=true) }
                }
            } else {
                var draft by rememberSaveable(extra.name) { mutableStateOf(chosen[extra.name].orEmpty()) }
                Text("${extra.name} · obrigatório",color=TvColors.Muted)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TvTextInput(draft,{draft=it},Modifier.weight(1f).border(1.dp,TvColors.Muted,RoundedCornerShape(8.dp)).padding(10.dp),maxLength=200,
                        onSubmit={memory.extras[key]=chosen+(extra.name to draft.trim())})
                    TvButton("Aplicar",{memory.extras[key]=chosen+(extra.name to draft.trim())})
                }
            }
        }
        if(!ready)Text("Escolha os filtros obrigatórios para consultar este catálogo.",color=TvColors.Muted)
        if(loading)Text("Carregando…",color=TvColors.Muted)
        if(message.isNotBlank()) { Text(message,color=TvColors.Muted);TvButton("Tentar novamente",{attempt++},enabled=!loading) }
        if(ready&&!loading&&message.isBlank()&&metas.isEmpty())Text("Nenhum título encontrado.",color=TvColors.Muted)
        if(metas.isNotEmpty())TvRow(null,metas,key,restorePoster,onOpen)
        if(metas.isNotEmpty()&&catalog.extra.any { it.name=="skip" }&&!exhausted)TvButton(if(more)"Carregando…"else "Carregar mais",{
            scope.launch {
                more=true;message=""
                try {
                    val next=repository.client.catalog(addon,catalog,extras+("skip" to metas.size.toString()))
                    val combined=(metas+next).distinctBy { it.type+":"+it.id }.take(2000)
                    exhausted=next.isEmpty()||combined.size==metas.size||combined.size>=2000
                    metas=combined;memory.cache(pageKey,metas);memory.exhausted[pageKey]=exhausted
                } catch(e:CancellationException) { throw e }
                catch(_:Exception) { message="Não foi possível carregar mais títulos. Tente novamente." }
                finally { more=false }
            }
        },enabled=!more&&!loading)
    }
}

@Composable private fun TvRow(title:String?,metas:List<TvMeta>,rowKey:String,restorePoster:String?,onOpen:(TvMeta,String)->Unit) {
    val listState=rememberLazyListState()
    val requester=remember { FocusRequester() }
    var restored by remember(restorePoster) { mutableStateOf(false) }
    val distinct=metas.distinctBy { it.type+":"+it.id }
    val restoreIndex=distinct.indexOfFirst { "$rowKey:${it.type}:${it.id}"==restorePoster }
    LaunchedEffect(restorePoster,distinct.size) {
        if(restoreIndex>=0&&!restored) {
            listState.scrollToItem(restoreIndex)
            withFrameNanos { };restored=runCatching { requester.requestFocus() }.getOrDefault(false)
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(title!=null)Text(title,color=Color.White,fontSize=20.sp)
        if(distinct.isEmpty())Text("Nenhum título nesta lista.",color=TvColors.Muted)
        LazyRow(state=listState,horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(4.dp)) {
            itemsIndexed(distinct,key={_,meta->meta.type+":"+meta.id}) { index,meta ->
                Button(onClick={onOpen(meta,"$rowKey:${meta.type}:${meta.id}")},modifier=Modifier.width(150.dp).then(if(index==restoreIndex)Modifier.focusRequester(requester)else Modifier),
                    scale=ButtonDefaults.scale(focusedScale=1f),colors=ButtonDefaults.colors(containerColor=TvColors.Panel,contentColor=Color.White,focusedContainerColor=TvColors.FocusAccent,focusedContentColor=TvColors.OnFocusAccent)) {
                    Column { AsyncImage(meta.poster,meta.name,Modifier.fillMaxWidth().height(170.dp),contentScale=ContentScale.Fit);Text(meta.name,maxLines=3,fontSize=15.sp) }
                }
            }
        }
    }
}


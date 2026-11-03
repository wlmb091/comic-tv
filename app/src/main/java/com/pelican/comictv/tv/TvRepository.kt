package com.pelican.comictv.tv

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TvShelf(val addon:TvAddon,val catalog:TvCatalog,val items:List<TvMeta>,val error:Boolean=false)
data class TvProgress(val meta:TvMeta,val video:TvVideo,val positionMs:Long,val durationMs:Long)
/** [addonList] is the list of add-on links installed from the phone, kept only on this TV to reinstall everything at once. */
data class TvSnapshot(val addons:List<TvAddon> = emptyList(),val favorites:List<TvMeta> = emptyList(),val progress:List<TvProgress> = emptyList(),val cacheGiB:Int=8,
    val video:TvVideoPrefs=TvVideoPrefs(),val addonList:List<String> = emptyList())

/** One line of a list of add-on links, after the TV consulted it. [n] is its position among the links of the list. */
data class TvBatchItem(val n:Int,val link:String,val name:String,val status:String,val installable:Boolean,val adult:Boolean=false,
    val configure:String="",val result:String="",@Transient val addon:TvAddon?=null)

/** Private atomic persistence: configured transport URLs never enter comic backups or routes. */
class TvStore(private val directory:File) {
    private val gson=Gson();private val file=File(directory,"state.json")
    @Synchronized fun load():TvSnapshot=runCatching {
        val root=com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
        val defaults=gson.toJsonTree(TvSnapshot()).asJsonObject
        for((key,value) in defaults.entrySet())if(!root.has(key)||root[key].isJsonNull)root.add(key,value)
        if(root["cacheGiB"].asInt !in setOf(1,2,4,8,16))root.addProperty("cacheGiB",8)
        val snapshot=gson.fromJson(root,TvSnapshot::class.java)
        require(snapshot.addons.size<=100&&snapshot.progress.size<=500&&snapshot.favorites.size<=500&&(snapshot.addonList?:emptyList()).size<=200)
        snapshot.addons.forEach { addon ->
            StremioLinks.manifest(addon.url)
            StremioJson.manifest(gson.toJson(addon.manifest))
            require(addon.animeCatalogs!=null)
        }
        snapshot.copy(video=(snapshot.video?:TvVideoPrefs()).let{it.copy(subtitles=it.subtitles?:TvSubtitlePrefs())}.normalized(),
            addonList=(snapshot.addonList?:emptyList()).mapNotNull{runCatching{StremioLinks.manifest(it)}.getOrNull()}.distinct())
    }.getOrDefault(TvSnapshot())
    @Synchronized fun save(value:TvSnapshot){
        directory.mkdirs();val tmp=File(directory,"state.tmp")
        tmp.outputStream().use{it.write(gson.toJson(value).toByteArray());it.fd.sync()}
        try {java.nio.file.Files.move(tmp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)}
        catch(_:java.nio.file.AtomicMoveNotSupportedException){java.nio.file.Files.move(tmp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)}
    }
}

class StremioClient {
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build()
    private val slots=Semaphore(4)
    private suspend fun get(url:String):String=slots.withPermit {
        require(StremioLinks.http(url)){"Endereço de complemento inválido."}
        suspendCancellableCoroutine { c ->
            val call=client.newCall(Request.Builder().url(url).header("Accept","application/json").build());c.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException){if(c.isActive)c.resumeWithException(IOException("Complemento indisponível."))}
                override fun onResponse(call:Call,response:Response){
                    val result=runCatching{response.use{r->check(r.isSuccessful){"O complemento recusou a consulta."};val body=r.body;require(body.contentLength()<=8L*1024*1024){"Resposta grande demais."};val out=java.io.ByteArrayOutputStream();body.byteStream().use{input->val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=8*1024*1024){"Resposta grande demais."};out.write(b,0,n)}};out.toString("UTF-8")}}
                    if(c.isActive)result.fold(c::resume){c.resumeWithException(IOException("Não foi possível ler a resposta do complemento."))}
                }
            })
        }
    }
    suspend fun manifest(url:String)=StremioJson.manifest(get(StremioLinks.manifest(url)))
    suspend fun catalog(a:TvAddon,c:TvCatalog,extras:Map<String,String> = emptyMap()):List<TvMeta> {require(c.canRequest(extras));return StremioJson.metas(get(StremioLinks.resource(a.url,"catalog",c.type,c.id,extras)))}
    suspend fun meta(a:TvAddon,type:String,id:String)=StremioJson.meta(get(StremioLinks.resource(a.url,"meta",type,id)))
    suspend fun streams(a:TvAddon,type:String,id:String)=StremioJson.streams(get(StremioLinks.resource(a.url,"stream",type,id))).map{it.copy(addonName=a.manifest.name)}
    suspend fun addonCatalog(a:TvAddon,c:TvCatalog)=StremioJson.addonUrls(get(StremioLinks.resource(a.url,"addon_catalog",c.type,c.id)))
    suspend fun subtitles(a:TvAddon,type:String,id:String)=StremioJson.subtitles(get(StremioLinks.resource(a.url,"subtitles",type,id))).map{it.copy(addonName=a.manifest.name)}
    /** Raw bytes of one subtitle file, at most [max] bytes, for charset repair on the TV. */
    suspend fun download(url:String,max:Int):ByteArray=slots.withPermit {
        require(StremioLinks.http(url)){"Endereço de legenda inválido."}
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                check(r.isSuccessful){"A legenda não pôde ser baixada."}
                val body=r.body;require(body.contentLength()<=max){"Legenda grande demais."}
                val out=java.io.ByteArrayOutputStream();body.byteStream().use{input->val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=max){"Legenda grande demais."};out.write(b,0,n)}}
                out.toByteArray()
            }
        }
    }
}

class TvRepository(context:Context) {
    private val store=TvStore(File(context.filesDir,"tv"));val client=StremioClient()
    private val mutable=MutableStateFlow(store.load());val state=mutable.asStateFlow()
    suspend fun preview(input:String):TvAddon=withContext(Dispatchers.IO){val url=StremioLinks.manifest(input);TvAddon(url,client.manifest(url))}
    @Synchronized fun install(a:TvAddon){require(!a.manifest.configurationRequired){"Configure o complemento no navegador e use o link configurado."};require(state.value.addons.size<100||state.value.addons.any{it.url==a.url});update(state.value.copy(addons=state.value.addons.filterNot{it.url==a.url}+a))}
    @Synchronized fun remove(a:TvAddon)=update(state.value.copy(addons=state.value.addons.filterNot{it.url==a.url}))
    @Synchronized fun enabled(a:TvAddon)=update(state.value.copy(addons=state.value.addons.map{if(it.url==a.url)it.copy(enabled=!it.enabled)else it}))
    @Synchronized fun anime(a:TvAddon,c:TvCatalog){val key=c.type+":"+c.id;update(state.value.copy(addons=state.value.addons.map{if(it.url==a.url)it.copy(animeCatalogs=if(key in it.animeCatalogs)it.animeCatalogs-key else it.animeCatalogs+key)else it}))}
    @Synchronized fun favorite(meta:TvMeta){val list=state.value.favorites;update(state.value.copy(favorites=if(list.any{it.id==meta.id&&it.type==meta.type})list.filterNot{it.id==meta.id&&it.type==meta.type}else (list+meta).takeLast(500)))}
    @Synchronized fun progress(meta:TvMeta,video:TvVideo,position:Long,duration:Long){if(position<0)return;val p=TvProgress(meta,video,position,duration.coerceAtLeast(0));update(state.value.copy(progress=(state.value.progress.filterNot{it.meta.id==meta.id&&it.meta.type==meta.type}+p).takeLast(500)))}
    @Synchronized fun cache(gib:Int){require(gib in setOf(1,2,4,8,16));update(state.value.copy(cacheGiB=gib))}
    @Synchronized fun video(change:(TvVideoPrefs)->TvVideoPrefs){update(state.value.copy(video=change(state.value.video).normalized()))}
    private fun update(value:TvSnapshot){store.save(value);mutable.value=value}
    /** Adds links to the saved list, without repeating any. */
    @Synchronized fun remember(links:List<String>){update(state.value.copy(addonList=(state.value.addonList+links).distinct().takeLast(200)))}

    /**
     * Consults every link of a pasted list or .txt file, at most 100, a few at a time. Nothing is installed here:
     * each item says whether it can be installed, or why it is skipped.
     */
    suspend fun checkList(text:String):List<TvBatchItem> = coroutineScope {
        val lines=text.split(Regex("[\\s,;]+")).map{it.trim()}.filter{it.isNotBlank()}.take(100)
        val seen=HashSet<String>()
        val planned=lines.mapIndexed{i,line->
            val link=runCatching{StremioLinks.manifest(line)}.getOrNull()
            when {
                link==null->TvBatchItem(i,line.take(300),line.take(80),"Link inválido",false)
                !seen.add(link)->TvBatchItem(i,link,link.take(80),"Repetido na lista",false)
                else->TvBatchItem(i,link,link.take(80),"",false)
            }
        }
        planned.map{item->async {
            if(item.status.isNotBlank())return@async item
            val manifest=try{withTimeoutOrNull(25_000){client.manifest(item.link)}}catch(e:CancellationException){throw e}catch(_:Exception){null}
                ?:return@async item.copy(status="Não respondeu ou não é um complemento")
            val addon=TvAddon(item.link,manifest)
            val named=item.copy(name=manifest.name.take(120),adult=manifest.adult,addon=addon)
            when {
                state.value.addons.any{it.url==item.link}->named.copy(status="Já instalado")
                manifest.configurationRequired->named.copy(status="Precisa de configuração",configure=runCatching{StremioLinks.configure(item.link)}.getOrDefault(""))
                state.value.addons.size>=100->named.copy(status="Limite de 100 complementos atingido")
                else->named.copy(status=if(manifest.adult)"Pronto para instalar · conteúdo adulto" else "Pronto para instalar",installable=true)
            }
        }}.awaitAll()
    }

    /** Installs the chosen items and saves their links, with the ones already installed, for "Reinstalar tudo". */
    fun installChecked(items:List<TvBatchItem>,chosen:Set<Int>):List<TvBatchItem> {
        val done=items.map{item->
            if(item.n !in chosen||!item.installable||item.addon==null)item
            else runCatching{install(item.addon);item.copy(result="Instalado",installable=false)}.getOrElse{item.copy(result="Não foi possível instalar")}
        }
        remember(done.filter{it.result=="Instalado"||it.status=="Já instalado"}.map{it.link})
        return done
    }
    fun catalogs(section:String,query:String=""):List<Pair<TvAddon,TvCatalog>> =state.value.addons.filter{it.enabled}.flatMap{a->a.manifest.catalogs.filter{c->
        val anime=c.type.equals("anime",true)||c.name.contains("anime",true)||"${c.type}:${c.id}" in a.animeCatalogs
        val type=when(section){"Filmes"->c.type=="movie"&&!anime;"Séries"->c.type=="series"&&!anime;"Animes"->anime;else->true}
        type&&(if(query.isBlank())c.canRequest(emptyMap())else c.extra.any{it.name=="search"}&&c.canRequest(mapOf("search" to query)))
    }.map{a to it}}
    suspend fun details(preview:TvMeta):TvMeta=supervisorScope {
        val candidates=state.value.addons.filter{it.enabled&&it.manifest.supports("meta",preview.type,preview.id)}
        val results=kotlinx.coroutines.channels.Channel<TvMeta?>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val jobs=candidates.map{a->launch{
            val result=try{client.meta(a,preview.type,preview.id).takeIf{it.id==preview.id&&it.type==preview.type}}catch(e:CancellationException){throw e}catch(_:Exception){null}
            results.send(result)
        }}
        var found:TvMeta?=null
        try {withTimeoutOrNull(60_000){for(i in candidates.indices){val value=results.receive();if(value!=null){found=value;break}}}}
        finally {jobs.forEach{it.cancel()};results.cancel()}
        found?:preview
    }
    suspend fun streams(meta:TvMeta,video:TvVideo):List<TvStream> =supervisorScope {
        val candidates=state.value.addons.filter{it.enabled&&it.manifest.supports("stream",meta.type,video.id)}
        val results=kotlinx.coroutines.channels.Channel<List<TvStream>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val jobs=candidates.map{a->launch{
            val value=try{client.streams(a,meta.type,video.id)}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
            results.send(value)
        }}
        val collected=mutableListOf<TvStream>()
        try {withTimeoutOrNull(60_000){repeat(candidates.size){collected.addAll(results.receive())}}}
        finally {jobs.forEach{it.cancel()};results.cancel()}
        StreamRank.sorted(collected)
    }
    /** Subtitles from every enabled add-on that declares them, collected for at most 20 seconds. */
    suspend fun subtitles(meta:TvMeta,video:TvVideo):List<TvSubtitle> =supervisorScope {
        val candidates=state.value.addons.filter{it.enabled&&it.manifest.supports("subtitles",meta.type,video.id)}
        val results=kotlinx.coroutines.channels.Channel<List<TvSubtitle>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val jobs=candidates.map{a->launch{
            val value=try{client.subtitles(a,meta.type,video.id)}catch(e:CancellationException){throw e}catch(_:Exception){emptyList()}
            results.send(value)
        }}
        val collected=mutableListOf<TvSubtitle>()
        try {withTimeoutOrNull(20_000){repeat(candidates.size){collected.addAll(results.receive())}}}
        finally {jobs.forEach{it.cancel()};results.cancel()}
        collected.distinctBy{it.url}.take(300)
    }
}

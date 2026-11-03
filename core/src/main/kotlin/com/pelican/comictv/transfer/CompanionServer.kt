package com.pelican.comictv.transfer

import com.google.gson.Gson
import com.pelican.comictv.archive.*
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.*
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

data class CompanionCommand(val action:String="",val text:String="",val id:String="",val x:Double=0.0,val y:Double=0.0,val delta:Int=0,val dx:Double=0.0,val dy:Double=0.0,val page:Int=0,val zoom:Float=1f,val readerSensitivity:Float=1f,val pointerSensitivity:Float=1f,val zoomSensitivity:Float=1f,val scrollSensitivity:Float=1f,val cursorSize:Int=100,val cursorStyle:Int=0,val soundsOn:Boolean=true,val soundsVolume:Int=50,val soundsStyle:Int=0)
data class CompanionReaderState(val active:Boolean=false,val page:Int=0,val pageCount:Int=0,val zoom:Float=1f,val minZoom:Float=1f,val maxZoom:Float=3f,val browser:Boolean=false,val comicId:String="",val browserScroll:Float=0f,val browserScrollable:Boolean=false,val readerScroll:Float=0f,val readerScrollable:Boolean=false,val browserCanBack:Boolean=false,val browserCanForward:Boolean=false,val readerText:Boolean=false,val readerMenu:CompanionReaderMenu=CompanionReaderMenu(),val readerSensitivity:Float=1f,val pointerSensitivity:Float=1f,val zoomSensitivity:Float=1f,val scrollSensitivity:Float=1f,val cursorAvailable:Boolean=false,val cursorSize:Int=100,val cursorStyle:Int=0,val theme:String="",val browserDark:Boolean=false,val soundsOn:Boolean=true,val soundsVolume:Int=50,val soundsStyle:Int=0)
/** What the TV reader's own tools show now, so the phone panel can offer the same options. */
data class CompanionReaderMenu(val layout:String="",val fit:String="",val rtl:Boolean=false,val filter:String="",val filterOn:Boolean=false,val intensity:Int=1,val compare:Boolean=false,val automatic:Boolean=false,val interval:Int=10,val rotatable:Boolean=false,val rotated:Boolean=false,val chapters:Boolean=false,val editions:Boolean=false,val cover:Boolean=false,val coverBusy:Boolean=false,val coverMessage:String="",val retry:Boolean=false,val fontSize:Int=0,val drawingsHidden:Boolean=false)
val readerOptions=setOf("layout","fit","direction","rotate","cover","filter","intensity","compare","automatic","interval","chapter-prev","chapter-next","editions","retry","exit","font-down","font-up","drawings")
/** The five selectable TV themes, by their persisted preset names. */
val themeNames=setOf("Amber","Flix","Prime","Max","Cinema")
private val PNG_SIGNATURE=byteArrayOf(0x89.toByte(),0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A)
data class CompanionResource(val mime:String,val bytes:ByteArray?=null,val file:File?=null,val filename:String?=null,val headers:Map<String,String> =emptyMap())
/** A stroke while the finger is down: points are page fractions and [size] a fraction of the page width. The TV draws it as a preview. */
data class CompanionStroke(val id:String="",val page:Int=0,val kind:String="",val eraser:Boolean=false,val color:String="#000000",val size:Float=0f,val opacity:Float=1f,val points:List<Float> =emptyList())
/** A change to the drawing of one page. A patch replaces one rectangle of the layer; save and delete reach the stored file. */
data class CompanionDrawing(val kind:String,val id:String,val page:Int,val x:Int=0,val y:Int=0,val width:Int=0,val height:Int=0,val layerWidth:Int=0,val layerHeight:Int=0,val pageWidth:Int=0,val pageHeight:Int=0,val live:Boolean=false)

/** An explicitly opened LAN session that ends when idle or at its absolute limit. No arbitrary URL/path/OS commands. */
class CompanionServer(private val host:String,private val temporaryDirectory:File,private val html:String,
    private val get:(String)->CompanionResource,private val command:(CompanionCommand)->Unit,
    private val received:(File,String)->Unit={_,_->},private val clock:()->Long=System::currentTimeMillis,
    private val lifetimeMillis:Long=30*60*1000L,private val status:(String)->Unit={},private val maxLifetimeMillis:Long=8*60*60*1000L,
    private val stroke:(CompanionStroke)->Unit={},private val drawing:(CompanionDrawing,ByteArray)->Unit={_,_->},private val drawingLayers:(String,Int,InputStream)->Unit={_,_,_->},private val excerpt:(String,Int,Int,Int,ByteArray)->Int={_,_,_,_,_->0}) : Closeable {
    private val token=ByteArray(24).also {SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it.toInt() and 255)}
    private val root="/s/$token"
    private var openedAt=0L
    @Volatile private var usedAt=0L
    @Volatile private var active=false
    private var closed=false
    private val transfers=Semaphore(1);private val resources=Semaphore(2)
    // Covers are small and local: their own slots keep them from queuing behind pages, files and slow source covers.
    private val covers=Semaphore(4)
    private val drawings=Semaphore(1)
    private var bucket=0L;private var commands=0
    private var strokeBucket=0L;private var strokes=0
    val url get()="http://$host:${server.listeningPort}$root"
    private val server=object:NanoHTTPD(host,0){override fun serve(session:IHTTPSession):Response=handle(session)}.apply {
        val delegate=DefaultAsyncRunner();val count=AtomicInteger()
        setAsyncRunner(object:AsyncRunner {
            override fun exec(handler:ClientHandler){if(count.incrementAndGet()>16){count.decrementAndGet();handler.close()}else delegate.exec(handler)}
            override fun closed(handler:ClientHandler){count.decrementAndGet();delegate.closed(handler)}
            override fun closeAll(){delegate.closeAll()}
        })
    }
    @Synchronized fun open(){check(!closed&&!active);temporaryDirectory.mkdirs();server.start(15_000,true);openedAt=clock();usedAt=openedAt;active=true}
    fun expired()=clock().let{now->now-usedAt>=lifetimeMillis||now-openedAt>=maxLifetimeMillis}
    @Synchronized override fun close(){closed=true;active=false;server.stop()}
    private fun response(code:Response.Status,text:String,mime:String="text/plain; charset=utf-8")=secure(newFixedLengthResponse(code,mime,text))
    private fun secure(r:Response)=r.apply {
        addHeader("Connection","close")
        addHeader("Cache-Control","no-store");addHeader("Referrer-Policy","no-referrer");addHeader("X-Content-Type-Options","nosniff");addHeader("X-Frame-Options","DENY")
        addHeader("Content-Security-Policy","default-src 'none'; img-src 'self' blob:; media-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'")
    }
    private fun valid(c:CompanionCommand):Boolean {
        if(c.action !in setOf("up","down","left","right","ok","back","menu","enter","text","tap","scroll","open","pointer","reader-pan","reader-page","reader-turn","reader-zoom","reader-pinch","reader-drag","reader-option","reader-arrow","open-chapter","ok-long","browser-open","home","settings","cursor","rotate","theme","browser-pointer","browser-scroll","browser-scroll-to","browser-history","reader-scroll-to","reader-view","reader-adjust","reader-replay","image-adjust","preset-save","preset-delete","preset-apply","export-cbz","sounds","browser-dark","browser-zoom","browser-pinch","browser-click","browser-context","browser-press","browser-release","browser-cancel","browser-up","browser-down","browser-left","browser-right","browser-ok"))return false
        if(c.text.length>1000||c.id.length>100||c.text.any {it=='\u0000'})return false
        if((c.action=="open"||c.action=="open-chapter")&&!c.id.matches(Regex("[A-Za-z0-9-]{1,100}")))return false
        if(!c.x.isFinite()||!c.y.isFinite()||!c.dx.isFinite()||!c.dy.isFinite()||!c.zoom.isFinite())return false
        return when(c.action){
            "settings"->listOf(c.readerSensitivity,c.pointerSensitivity,c.zoomSensitivity,c.scrollSensitivity).all{it.isFinite()&&it in .25f..3f}
            "cursor"->c.cursorSize in 50..300&&c.cursorStyle in 0..2
            "theme"->c.text in themeNames
            "reader-option"->c.text in readerOptions
            "reader-arrow"->c.text in setOf("up","down","left","right")
            "open-chapter"->c.page in 0..4999
            "browser-open"->c.text.isNotBlank()
            "browser-pointer","browser-scroll"->c.dx in -1.0..1.0&&c.dy in -1.0..1.0
            "browser-scroll-to","reader-scroll-to"->c.dy in 0.0..1.0
            "browser-history"->c.delta==-1||c.delta==1
            "browser-zoom"->c.zoom in .5f..2f
            "browser-pinch"->c.zoom in .5f..2f&&c.x in 0.0..1.0&&c.y in 0.0..1.0&&c.dx in -1.0..1.0&&c.dy in -1.0..1.0
            "reader-pan","reader-drag"->c.x in -1.0..1.0&&c.y in -1.0..1.0
            "pointer"->c.dx in -1.0..1.0&&c.dy in -1.0..1.0
            "reader-page"->c.page in 0..4999
            "reader-turn"->c.delta==-1||c.delta==1
            "reader-zoom"->c.zoom in 1f..3f
            "reader-view"->c.zoom in 1f..3f&&c.x in 0.0..1.0&&c.y in 0.0..1.0
            "reader-adjust"->c.text.isEmpty()||com.pelican.comictv.reader.ImageAdjustments.parse(c.text)!=null
            "image-adjust"->c.id.matches(Regex("[A-Za-z0-9-]{1,100}"))&&com.pelican.comictv.reader.ImageAdjustments.parse(c.text)!=null
            "reader-replay"->c.page in 0..99_999
            "preset-save"->com.pelican.comictv.reader.AdjustmentPreset.parse(c.text)!=null
            "preset-delete"->c.text.matches(com.pelican.comictv.reader.AdjustmentPreset.ID)
            "preset-apply"->c.id.matches(Regex("[A-Za-z0-9-]{1,100}"))&&(c.text=="none"||c.text.matches(com.pelican.comictv.reader.AdjustmentPreset.ID))&&c.delta in 0..1
            "export-cbz"->c.id.matches(Regex("[A-Za-z0-9-]{1,100}"))
            "sounds"->c.soundsVolume in 0..100&&c.soundsStyle in 0..4
            "reader-pinch"->c.zoom in 1f..3f&&c.x in 0.0..1.0&&c.y in 0.0..1.0&&c.dx in -1.0..1.0&&c.dy in -1.0..1.0
            else->c.x in 0.0..1.0&&c.y in 0.0..1.0&&c.delta in -600..600
        }
    }
    private fun validStroke(s:CompanionStroke)=s.id.matches(Regex("[A-Za-z0-9-]{1,100}"))&&s.page in 0..99_999&&s.kind in setOf("begin","move","end","cancel")&&s.color.matches(Regex("#[0-9a-fA-F]{6}"))&&
        s.size.isFinite()&&s.size in 0f..1f&&s.opacity.isFinite()&&s.opacity in 0f..1f&&s.points.size<=1200&&s.points.size%2==0&&s.points.all{it.isFinite()&&it in -.5f..1.5f}
    private fun validDrawing(d:CompanionDrawing):Boolean {
        if(d.layerWidth !in 1..4096||d.layerHeight !in 1..4096||d.layerWidth.toLong()*d.layerHeight>16_000_000L)return false
        if(d.kind=="save")return d.pageWidth in 1..30000&&d.pageHeight in 1..30000
        return d.x>=0&&d.y>=0&&d.width>0&&d.height>0&&d.x+d.width<=d.layerWidth&&d.y+d.height<=d.layerHeight
    }
    private fun body(session:IHTTPSession,max:Long):InputStream {
        require(!session.headers.containsKey("transfer-encoding")){"Envie conteúdo de tamanho conhecido."}
        val length=session.headers["content-length"]?.toLongOrNull()?:error("Tamanho obrigatório.")
        require(length in 1..max){"Tamanho inválido."}
        return object:InputStream(){var remaining=length
            override fun read():Int {val one=ByteArray(1);return if(read(one,0,1)<0)-1 else one[0].toInt() and 255}
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                if(!active||expired()||Thread.currentThread().isInterrupted)throw IOException("Sessão encerrada.")
                if(remaining==0L)return -1
                val n=session.inputStream.read(b,off,minOf(len.toLong(),remaining).toInt())
                if(n<0)throw IOException("Envio incompleto.")
                remaining-=n;return n
            }
        }
    }
    private fun handle(session:IHTTPSession):Response {
        val path=session.uri
        if(path!=root&&path!="$root/"&&!path.startsWith("$root/"))return response(Response.Status.NOT_FOUND,"Sessão não encontrada.")
        if(!active||expired())return response(Response.Status.GONE,"Sessão encerrada. Inicie outra na TV.")
        if(session.headers["host"]!="$host:${server.listeningPort}")return response(Response.Status.FORBIDDEN,"Endereço inválido.")
        val origin=session.headers["origin"]
        if(origin!=null&&origin!="http://$host:${server.listeningPort}")return response(Response.Status.FORBIDDEN,"Origem inválida.")
        usedAt=clock()
        val endpoint=path.removePrefix(root).trimEnd('/')
        try {
            if(session.method==Method.GET){
                if(endpoint.isEmpty())return response(Response.Status.OK,html,"text/html; charset=utf-8")
                if(!endpoint.matches(Regex("/(library|reader-state|home-sources|image-presets|export-state/[A-Za-z0-9-]{1,100}|export-file/[A-Za-z0-9-]{1,100}|home-more/[lc]/[0-9]{1,4}|source-cover/[lc][0-9]{1,4}|chapters/[A-Za-z0-9-]{1,100}|saga-cover/[A-Za-z0-9-]{1,100}|cover/[A-Za-z0-9-]{1,100}|file/[A-Za-z0-9-]{1,100}|page/[A-Za-z0-9-]{1,100}/[0-9]{1,5}|drawing/[A-Za-z0-9-]{1,100}/[0-9]{1,5}|drawing-layers/[A-Za-z0-9-]{1,100}/[0-9]{1,5}|image-adjust/[A-Za-z0-9-]{1,100}|text-excerpts/[A-Za-z0-9-]{1,100}/[0-9]{1,5}|excerpt-base/[A-Za-z0-9-]{1,100}/[0-9]{1,5})")))return response(Response.Status.NOT_FOUND,"Item não encontrado.")
                // The state poll is light and must keep answering while pages and covers load.
                val limited=endpoint!="/reader-state"
                val cover=endpoint.startsWith("/cover/")||endpoint.startsWith("/saga-cover/")
                val slots=if(cover)covers else resources
                // A short wait absorbs the burst of images a page asks for at once instead of refusing the extra ones.
                if(limited&&!slots.tryAcquire(if(cover)2500L else 800L,java.util.concurrent.TimeUnit.MILLISECONDS))return response(Response.Status.CONFLICT,"Aguarde a operação atual.")
                try {
                    val resource=get(endpoint)
                    val file=resource.file
                    val out=if(file!=null)newFixedLengthResponse(Response.Status.OK,resource.mime,file.inputStream(),file.length())
                        else newFixedLengthResponse(Response.Status.OK,resource.mime,resource.bytes!!.inputStream(),resource.bytes.size.toLong())
                    if(resource.filename!=null){val encoded=java.net.URLEncoder.encode(resource.filename.take(180),"UTF-8").replace("+","%20");out.addHeader("Content-Disposition","attachment; filename=\"Comic-TV\"; filename*=UTF-8''$encoded")}
                    secure(out)
                    resource.headers.forEach{(name,value)->out.addHeader(name,value)}
                    // Local covers do not change while a session lasts; the phone may keep them for a few minutes.
                    if(cover)out.addHeader("Cache-Control","private, max-age=300")
                    return out
                } finally {if(limited)slots.release()}
            }
            if(session.method!=Method.POST)return response(Response.Status.METHOD_NOT_ALLOWED,"Método não permitido.")
            if(endpoint=="/command") {
                if(!session.headers["content-type"].orEmpty().startsWith("application/json"))return response(Response.Status.UNSUPPORTED_MEDIA_TYPE,"Envie JSON.")
                synchronized(this){val second=clock()/1000;if(bucket!=second){bucket=second;commands=0};if(++commands>30)return response(Response.Status.TOO_MANY_REQUESTS,"Aguarde um instante.")}
                val c=Gson().fromJson(body(session,8192).reader(Charsets.UTF_8),CompanionCommand::class.java)
                require(c!=null&&valid(c)){"Comando inválido."};command(c);return response(Response.Status.OK,"OK")
            }
            if(endpoint=="/stroke") {
                if(!session.headers["content-type"].orEmpty().startsWith("application/json"))return response(Response.Status.UNSUPPORTED_MEDIA_TYPE,"Envie JSON.")
                synchronized(this){val second=clock()/1000;if(strokeBucket!=second){strokeBucket=second;strokes=0};if(++strokes>60)return response(Response.Status.TOO_MANY_REQUESTS,"Aguarde um instante.")}
                val s=Gson().fromJson(body(session,16384).reader(Charsets.UTF_8),CompanionStroke::class.java)
                require(s!=null&&validStroke(s)){"Traço inválido."};stroke(s);return response(Response.Status.OK,"OK")
            }
            if(endpoint.startsWith("/text-excerpt/")) {
                val parts=endpoint.removePrefix("/text-excerpt/").split('/')
                require(parts.size==2&&parts[0].matches(Regex("[A-Za-z0-9-]{1,100}"))&&parts[1].matches(Regex("[0-9]{1,5}"))){"Trecho inválido."}
                val start=session.parameters["start"]?.singleOrNull()?.toIntOrNull()?:-1
                val end=session.parameters["end"]?.singleOrNull()?.toIntOrNull()?:-1
                require(start>=0&&end>start){"Trecho inválido."}
                if(!drawings.tryAcquire(3000L,java.util.concurrent.TimeUnit.MILLISECONDS))return response(Response.Status.CONFLICT,"Aguarde a operação atual.")
                try {
                    val bytes=body(session,16L*1024*1024).readBytes()
                    require(bytes.size>8&&PNG_SIGNATURE.indices.all{bytes[it]==PNG_SIGNATURE[it]}){"Envie uma imagem PNG."}
                    return response(Response.Status.OK,excerpt(parts[0],parts[1].toInt(),start,end,bytes).toString())
                } finally {drawings.release()}
            }
            if(endpoint.startsWith("/drawing/")) {
                val parts=endpoint.removePrefix("/drawing/").split('/')
                require(parts.size==3&&parts[0] in setOf("save","patch","record","delete","reset","layers")&&parts[1].matches(Regex("[A-Za-z0-9-]{1,100}"))&&parts[2].matches(Regex("[0-9]{1,5}"))){"Desenho inválido."}
                fun number(name:String)=session.parameters[name]?.singleOrNull()?.toIntOrNull()?:0
                val request=CompanionDrawing(parts[0],parts[1],parts[2].toInt(),number("x"),number("y"),number("w"),number("h"),number("lw"),number("lh"),number("pw"),number("ph"),number("live")==1)
                if(request.kind=="layers") {
                    if(!drawings.tryAcquire(3000L,java.util.concurrent.TimeUnit.MILLISECONDS))return response(Response.Status.CONFLICT,"Aguarde a operação atual.")
                    try {
                        val input=java.io.PushbackInputStream(body(session,64L*1024*1024),4)
                        val magic=ByteArray(4);var read=0
                        while(read<4){val n=input.read(magic,read,4-read);if(n<0)break;read+=n}
                        require(read==4&&String(magic,Charsets.US_ASCII)=="CTVL"){"Formato de camadas inválido."}
                        input.unread(magic);drawingLayers(request.id,request.page,input);return response(Response.Status.OK,"OK")
                    } finally {drawings.release()}
                }
                val image=request.kind=="save"||request.kind=="patch"||request.kind=="record"
                if(image)require(validDrawing(request)){"Desenho inválido."}
                if(!drawings.tryAcquire(3000L,java.util.concurrent.TimeUnit.MILLISECONDS))return response(Response.Status.CONFLICT,"Aguarde a operação atual.")
                try {
                    val bytes=if(image)body(session,16L*1024*1024).readBytes() else ByteArray(0)
                    if(image)require(bytes.size>8&&PNG_SIGNATURE.indices.all{bytes[it]==PNG_SIGNATURE[it]}){"Envie uma imagem PNG."}
                    drawing(request,bytes);return response(Response.Status.OK,"OK")
                } finally {drawings.release()}
            }
            if(endpoint!="/upload")return response(Response.Status.NOT_FOUND,"Ação não encontrada.")
            if(!session.headers["content-type"].orEmpty().startsWith("application/octet-stream"))return response(Response.Status.UNSUPPORTED_MEDIA_TYPE,"Envie um arquivo.")
            val name=session.parameters["name"]?.singleOrNull()?.takeIf {it.length in 1..200 && '/' !in it && '\\' !in it && it.none {ch->ch.code<32} && it.substringAfterLast('.').lowercase() in ComicContainers.extensions}
                ?:return response(Response.Status.BAD_REQUEST,"Formato ou nome inválido. APK não é aceito pelo painel.")
            if(!transfers.tryAcquire())return response(Response.Status.CONFLICT,"Outro envio está em andamento.")
            val target=File(temporaryDirectory,"${UUID.randomUUID()}.upload")
            try {
                status("Recebendo $name")
                copyArchiveToolInput(body(session,1024L*1024*1024),target)
                if(ComicContainers.detect(target) in setOf(ComicContainer.Zip,ComicContainer.Rar,ComicContainer.SevenZip))ArchiveTools().list(target)
                check(active&&!expired());status("Importando $name");received(target,name)
                status("Importado · $name");return response(Response.Status.OK,"Importado")
            } finally {target.delete();transfers.release()}
        } catch(error:Exception){status("Operação não concluída.");return response(Response.Status.BAD_REQUEST,error.message?.take(240)?:"Não foi possível concluir.")}
    }
}

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

data class CompanionCommand(val action:String="",val text:String="",val id:String="",val x:Double=0.0,val y:Double=0.0,val delta:Int=0,val dx:Double=0.0,val dy:Double=0.0,val page:Int=0,val zoom:Float=1f,val readerSensitivity:Float=1f,val pointerSensitivity:Float=1f,val zoomSensitivity:Float=1f,val scrollSensitivity:Float=1f,val cursorSize:Int=100,val cursorStyle:Int=0)
data class CompanionReaderState(val active:Boolean=false,val page:Int=0,val pageCount:Int=0,val zoom:Float=1f,val minZoom:Float=1f,val maxZoom:Float=3f,val browser:Boolean=false,val comicId:String="",val browserScroll:Float=0f,val browserScrollable:Boolean=false,val readerScroll:Float=0f,val readerScrollable:Boolean=false,val browserCanBack:Boolean=false,val browserCanForward:Boolean=false,val readerSensitivity:Float=1f,val pointerSensitivity:Float=1f,val zoomSensitivity:Float=1f,val scrollSensitivity:Float=1f,val cursorAvailable:Boolean=false,val cursorSize:Int=100,val cursorStyle:Int=0)
data class CompanionResource(val mime:String,val bytes:ByteArray?=null,val file:File?=null,val filename:String?=null)

/** An explicitly opened LAN session that ends when idle or at its absolute limit. No arbitrary URL/path/OS commands. */
class CompanionServer(private val host:String,private val temporaryDirectory:File,private val html:String,
    private val get:(String)->CompanionResource,private val command:(CompanionCommand)->Unit,
    private val received:(File,String)->Unit={_,_->},private val clock:()->Long=System::currentTimeMillis,
    private val lifetimeMillis:Long=30*60*1000L,private val status:(String)->Unit={},private val maxLifetimeMillis:Long=8*60*60*1000L) : Closeable {
    private val token=ByteArray(24).also {SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it.toInt() and 255)}
    private val root="/s/$token"
    private var openedAt=0L
    @Volatile private var usedAt=0L
    @Volatile private var active=false
    private var closed=false
    private val transfers=Semaphore(1);private val resources=Semaphore(2)
    private var bucket=0L;private var commands=0
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
        if(c.action !in setOf("up","down","left","right","ok","back","menu","enter","text","tap","scroll","open","pointer","reader-pan","reader-page","reader-turn","reader-zoom","reader-pinch","home","settings","cursor","rotate","browser-pointer","browser-scroll","browser-scroll-to","browser-history","reader-scroll-to","ok-long","browser-zoom","browser-pinch","browser-click","browser-context","browser-press","browser-release","browser-cancel","browser-up","browser-down","browser-left","browser-right","browser-ok"))return false
        if(c.text.length>1000||c.id.length>100||c.text.any {it=='\u0000'})return false
        if(c.action=="open"&&!c.id.matches(Regex("[A-Za-z0-9-]{1,100}")))return false
        if(!c.x.isFinite()||!c.y.isFinite()||!c.dx.isFinite()||!c.dy.isFinite()||!c.zoom.isFinite())return false
        return when(c.action){
            "settings"->listOf(c.readerSensitivity,c.pointerSensitivity,c.zoomSensitivity,c.scrollSensitivity).all{it.isFinite()&&it in .25f..3f}
            "cursor"->c.cursorSize in 50..300&&c.cursorStyle in 0..2
            "browser-pointer","browser-scroll"->c.dx in -1.0..1.0&&c.dy in -1.0..1.0
            "browser-scroll-to","reader-scroll-to"->c.dy in 0.0..1.0
            "browser-history"->c.delta==-1||c.delta==1
            "browser-zoom"->c.zoom in .5f..2f
            "browser-pinch"->c.zoom in .5f..2f&&c.x in 0.0..1.0&&c.y in 0.0..1.0&&c.dx in -1.0..1.0&&c.dy in -1.0..1.0
            "reader-pan"->c.x in -1.0..1.0&&c.y in -1.0..1.0
            "pointer"->c.dx in -1.0..1.0&&c.dy in -1.0..1.0
            "reader-page"->c.page in 0..4999
            "reader-turn"->c.delta==-1||c.delta==1
            "reader-zoom"->c.zoom in 1f..3f
            "reader-pinch"->c.zoom in 1f..3f&&c.x in 0.0..1.0&&c.y in 0.0..1.0&&c.dx in -1.0..1.0&&c.dy in -1.0..1.0
            else->c.x in 0.0..1.0&&c.y in 0.0..1.0&&c.delta in -600..600
        }
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
                if(!endpoint.matches(Regex("/(library|reader-state|cover/[A-Za-z0-9-]{1,100}|file/[A-Za-z0-9-]{1,100}|page/[A-Za-z0-9-]{1,100}/[0-9]{1,5})")))return response(Response.Status.NOT_FOUND,"Item não encontrado.")
                // The state poll is light and must keep answering while pages and covers load.
                val limited=endpoint!="/reader-state"
                if(limited&&!resources.tryAcquire())return response(Response.Status.CONFLICT,"Aguarde a operação atual.")
                try {
                    val resource=get(endpoint)
                    val file=resource.file
                    val out=if(file!=null)newFixedLengthResponse(Response.Status.OK,resource.mime,file.inputStream(),file.length())
                        else newFixedLengthResponse(Response.Status.OK,resource.mime,resource.bytes!!.inputStream(),resource.bytes.size.toLong())
                    if(resource.filename!=null){val encoded=java.net.URLEncoder.encode(resource.filename.take(180),"UTF-8").replace("+","%20");out.addHeader("Content-Disposition","attachment; filename=\"Comic-TV\"; filename*=UTF-8''$encoded")}
                    return secure(out)
                } finally {if(limited)resources.release()}
            }
            if(session.method!=Method.POST)return response(Response.Status.METHOD_NOT_ALLOWED,"Método não permitido.")
            if(endpoint=="/command") {
                if(!session.headers["content-type"].orEmpty().startsWith("application/json"))return response(Response.Status.UNSUPPORTED_MEDIA_TYPE,"Envie JSON.")
                synchronized(this){val second=clock()/1000;if(bucket!=second){bucket=second;commands=0};if(++commands>30)return response(Response.Status.TOO_MANY_REQUESTS,"Aguarde um instante.")}
                val c=Gson().fromJson(body(session,8192).reader(Charsets.UTF_8),CompanionCommand::class.java)
                require(c!=null&&valid(c)){"Comando inválido."};command(c);return response(Response.Status.OK,"OK")
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

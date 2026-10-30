package com.pelican.comictv.companion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.pelican.comictv.AppContainer
import com.pelican.comictv.archive.ComicContainer
import com.pelican.comictv.archive.ComicContainers
import com.pelican.comictv.archive.openComicZip
import com.pelican.comictv.transfer.*
import java.io.File
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume

internal data class CompanionState(val url:String?=null,val qr:Bitmap?=null,val busy:Boolean=false,val message:String="Inicie uma sessão para conectar o celular.")

internal class CompanionController(private val activity:ComponentActivity,private val container:AppContainer) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow(CompanionState());val state=mutable.asStateFlow()
    private var server:CompanionServer?=null;private var sessionJob:Job?=null
    private var lastCapture=0L;private var capture:ByteArray?=null
    private val pages=LinkedHashMap<String,List<com.pelican.comictv.model.PageRef>>()
    private val files=File(activity.cacheDir,"companion-pages")
    var onOpen:(String)->Unit={}
    fun start(){
        if(server!=null||state.value.busy)return
        mutable.value=CompanionState(busy=true,message="Preparando painel…")
        val job=SupervisorJob(scope.coroutineContext[Job]);sessionJob=job
        scope.launch(job){var owned:CompanionServer?=null;try {
            val manager=activity.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network=manager.activeNetwork
            val host=withContext(Dispatchers.IO){LocalAddressResolver.currentAddress(activity)}?:error("Conecte TV e celular à mesma rede Wi-Fi ou Ethernet.")
            val html=withContext(Dispatchers.IO){activity.assets.open("companion/index.html").bufferedReader().use{it.readText()}}
            fun <T> call(timeout:Long=30_000,block:suspend ()->T):T=runBlocking {withContext(job+Dispatchers.IO){withTimeout(timeout){block()}}}
            val instance=CompanionServer(host,File(activity.cacheDir,"companion-upload"),html,
                get={path->call {resource(path)}},command={command->call {if(command.action=="open")container.library.getComic(command.id);withContext(Dispatchers.Main.immediate){control(command)}}},
                received={file,name->call(300_000){container.library.importReceived(file,name){text->mutable.update{it.copy(message=text)}};Unit}},
                clock=SystemClock::elapsedRealtime,status={text->if(sessionJob===job)mutable.update {it.copy(message=text)}})
            owned=instance
            val url=withContext(Dispatchers.IO){instance.open();instance.url}
            ensureActive();check(sessionJob===job);server=instance
            val qr=withContext(Dispatchers.Default){
                val matrix=QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,384,384,mapOf(EncodeHintType.MARGIN to 4))
                Bitmap.createBitmap(IntArray(384*384){i->if(matrix[i%384,i/384])android.graphics.Color.BLACK else android.graphics.Color.WHITE},384,384,Bitmap.Config.ARGB_8888)
            }
            mutable.value=CompanionState(url,qr,message="Sessão ativa. Abra o QR no Safari do iPhone ou no navegador do Android.")
            while(isActive){delay(1000);if(instance.expired()||manager.activeNetwork!=network||withContext(Dispatchers.IO){LocalAddressResolver.currentAddress(activity)}!=host){stop("Sessão encerrada por tempo ou mudança de rede.");break}}
        }catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){if(sessionJob===job)stop(error.message?:"Não foi possível iniciar.")}
        finally{owned?.close();if(server===owned)server=null}}
    }
    fun stop(message:String="Sessão encerrada.") {
        server?.close();server=null;sessionJob?.cancel();sessionJob=null
        mutable.value=CompanionState(message=message);capture=null;pages.clear()
    }
    fun close(){stop();scope.cancel()}
    private fun active(){check(server!=null&&activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){"O Comic TV precisa estar aberto na TV."}}
    private suspend fun resource(path:String):CompanionResource {
        withContext(Dispatchers.Main.immediate){active()}
        if(path=="/library") {
            val rows=container.library.observeComics().first().take(5000).map {comic->
                mapOf("id" to comic.id,"title" to comic.title,"pages" to comic.pageCount,"current" to comic.currentPage,
                    "lastRead" to comic.lastRead,"local" to !comic.id.startsWith("online-"),"favorite" to comic.favorite)
            }
            return CompanionResource("application/json; charset=utf-8",Gson().toJson(mapOf("books" to rows,"message" to state.value.message)).toByteArray(Charsets.UTF_8))
        }
        if(path=="/screen")return CompanionResource("image/jpeg",screenshot())
        val parts=path.trim('/').split('/');val id=parts[1]
        val comic=container.library.getComic(id) // Existence and identity always come from the library.
        if(parts[0]=="cover")return CompanionResource("image/jpeg",file=container.library.cover(id).also{check(it.isFile){"Capa indisponível."}})
        if(parts[0]=="file"){
            check(!id.startsWith("online-")){"Baixar original disponível apenas para arquivos importados."}
            val file=container.library.originalFile(id)
            val ext=when(ComicContainers.detect(file)){
                ComicContainer.Pdf->"pdf";ComicContainer.Rar->"cbr";ComicContainer.SevenZip->"cb7"
                ComicContainer.Zip->openComicZip(file).use{if(it.getEntry("META-INF/container.xml")!=null)"epub" else "cbz"}
                else->error("Original indisponível.")
            }
            return CompanionResource("application/octet-stream",file=file,filename=comic.title.replace(Regex("[\\r\\n/\\\\]"),"_")+".$ext")
        }
        check(parts[0]=="page")
        // Only one decoded page at a time; phone navigation never writes TV progress.
        return pageResource(id,parts[2].toInt())
    }
    private val pageMutex=kotlinx.coroutines.sync.Mutex()
    private suspend fun pageResource(id:String,index:Int):CompanionResource {
        pageMutex.lock()
        try {
            val reader=if(id.startsWith("online-"))container.onlineReader else container.library
            val refs=pages[id]?:reader.pages(id).also {pages[id]=it;while(pages.size>32)pages.remove(pages.keys.first())}
            require(index in refs.indices){"Página não encontrada."}
            val page=refs[index]
            val source=reader.prefetch(id,page)
            files.mkdirs()
            val target=File(files,"current-${java.util.UUID.randomUUID()}")
            runInterruptible(Dispatchers.IO){
                check(source.length()<=32L*1024*1024){"Página grande demais para compartilhar."}
                check(files.usableSpace>source.length()+64L*1024*1024){"Espaço insuficiente."}
                // Keep a small rolling cache; a response already opened retains its file descriptor.
                files.listFiles()?.sortedByDescending{it.lastModified()}?.drop(3)?.forEach{it.delete()}
                source.inputStream().use {input->target.outputStream().use{input.copyTo(it)}}
            }
            if(page.key.startsWith("epub:text:"))return CompanionResource("text/plain; charset=utf-8",file=target)
            val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(target.path,options)
            check(options.outWidth>0&&options.outHeight>0){"Imagem não reconhecida."}
            return CompanionResource(options.outMimeType?:"image/jpeg",file=target)
        } finally {pageMutex.unlock()}
    }
    private fun control(c:CompanionCommand){
        active();check(activity.hasWindowFocus()){ "Volte ao Comic TV para controlar." }
        if(c.action=="open") {onOpen(c.id);return}
        if(c.action=="back"){activity.onBackPressedDispatcher.onBackPressed();return}
        if(c.action=="text"){
            val connection=activity.currentFocus?.onCreateInputConnection(EditorInfo())?:error("Selecione primeiro um campo de texto na TV.")
            check(connection.commitText(c.text,1)){"O campo não recebeu o texto."};return
        }
        val view=activity.window.decorView
        if(c.action=="tap"||c.action=="scroll"){
            val now=SystemClock.uptimeMillis();val x=(c.x*view.width).toFloat();val y=(c.y*view.height).toFloat()
            fun touch(action:Int,py:Float,time:Long){val event=MotionEvent.obtain(now,time,action,x,py,0);try{activity.dispatchTouchEvent(event)}finally{event.recycle()}}
            touch(MotionEvent.ACTION_DOWN,y,now)
            if(c.action=="scroll")touch(MotionEvent.ACTION_MOVE,(y-c.delta).coerceIn(1f,(view.height-1).coerceAtLeast(1).toFloat()),now+20)
            touch(MotionEvent.ACTION_UP,if(c.action=="scroll")(y-c.delta).coerceIn(1f,(view.height-1).coerceAtLeast(1).toFloat()) else y,now+40);return
        }
        val code=when(c.action){"up"->KeyEvent.KEYCODE_DPAD_UP;"down"->KeyEvent.KEYCODE_DPAD_DOWN;"left"->KeyEvent.KEYCODE_DPAD_LEFT;"right"->KeyEvent.KEYCODE_DPAD_RIGHT;"ok"->KeyEvent.KEYCODE_DPAD_CENTER;"enter"->KeyEvent.KEYCODE_ENTER;"menu"->KeyEvent.KEYCODE_MENU;else->error("Comando inválido.")}
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code));view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code))
    }
    private suspend fun screenshot():ByteArray=withContext(Dispatchers.Main.immediate){
        active();check(activity.hasWindowFocus()){ "Volte ao Comic TV para visualizar." }
        val now=SystemClock.elapsedRealtime();capture?.takeIf{now-lastCapture<1200}?.let{return@withContext it}
        val view=activity.window.decorView;check(view.width>0&&view.height>0)
        val bitmap=Bitmap.createBitmap(640,(640L*view.height/view.width).toInt().coerceIn(1,640),Bitmap.Config.ARGB_8888)
        var finished=false;var requested=false
        try {
            val result=withTimeout(2500){suspendCancellableCoroutine<Int>{continuation->
                PixelCopy.request(activity.window,bitmap,{code->finished=true;if(continuation.isActive)continuation.resume(code) else bitmap.recycle()},Handler(Looper.getMainLooper()))
                requested=true
            }}
            check(result==PixelCopy.SUCCESS){"Prévia indisponível. Conteúdo protegido pode não aparecer."}
            val bytes=withContext(Dispatchers.Default){ByteArrayOutputStream().use{out->bitmap.compress(Bitmap.CompressFormat.JPEG,65,out);out.toByteArray()}}
            check(bytes.size<=1024*1024);capture=bytes;lastCapture=now;bytes
        } finally {if(finished||!requested)bitmap.recycle()}
    }
}

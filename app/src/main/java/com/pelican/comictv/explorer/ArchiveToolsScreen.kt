package com.pelican.comictv.explorer

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.archive.*
import com.pelican.comictv.ui.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ArchiveToolState(val entries: List<CollectionEntry> = emptyList(), val busy: Boolean = true,
    val message: String = "Lendo conteúdo…", val result: File? = null)
internal class ArchiveToolsViewModel(private val context: Context, val name: String, private val uri: Uri): ViewModel() {
    private val mutable=MutableStateFlow(ArchiveToolState());val state=mutable.asStateFlow()
    private val input=File(context.cacheDir,"archive-tool-${UUID.randomUUID()}")
    private var job: Job?=null
    init { job=viewModelScope.launch { try {
        val entries=runInterruptible(Dispatchers.IO) {
            check(context.cacheDir.usableSpace>64L*1024*1024) { "Espaço insuficiente." }
            context.contentResolver.openInputStream(uri)?.use { stream -> copyArchiveToolInput(stream,input) }
                ?: error("Não foi possível abrir o arquivo.")
            ArchiveTools().list(input)
        }
        mutable.value=ArchiveToolState(entries,false,"${entries.count { !it.directory }} arquivo(s). O original será preservado.")
    } catch(error: Exception) { mutable.value=ArchiveToolState(busy=false,message=error.message?:"Operação interrompida.") } } }
    fun run(convert: Boolean) {
        if(state.value.busy || state.value.entries.isEmpty())return
        mutable.update {it.copy(busy=true,result=null,message=if(convert)"Convertendo para CBZ…" else "Extraindo…")}
        job=viewModelScope.launch { try {
            val result=runInterruptible(Dispatchers.IO) {
                val root=File(context.filesDir,"archive-tools").apply {mkdirs()}
                val base=name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} _-]"),"_").take(60).ifBlank {"Quadrinho"}
                val target=File(root,"$base-${UUID.randomUUID().toString().take(8)}"+(if(convert)".cbz" else ""))
                val report: (Int,Int)->Unit={i,total-> mutable.update { it.copy(message="${if(convert)"Convertendo" else "Extraindo"} · $i de $total") } }
                if(convert)ArchiveTools().convert(input,target,report) else ArchiveTools().extract(input,target,report)
            }
            mutable.update {it.copy(busy=false,result=result,message="Concluído. Resultado em Explorar arquivos → Arquivos processados.")}
        } catch(cancelled: CancellationException) { mutable.update {it.copy(busy=false,message="Operação cancelada. O original foi mantido.")};throw cancelled }
        catch(error: Exception) {mutable.update {it.copy(busy=false,message=error.message?:"Não foi possível concluir.")}}
        }
    }
    fun cancel() {job?.cancel()}
    suspend fun export(destination: Uri) = runInterruptible(Dispatchers.IO) {
        val file=state.value.result?.takeIf {it.isFile}?:error("Nenhum CBZ convertido.")
        context.contentResolver.openOutputStream(destination,"w")?.use {out->file.inputStream().use {it.copyTo(out)}}?:error("Não foi possível salvar.")
    }
    fun message(text:String){mutable.update {it.copy(message=text)}}
    override fun onCleared(){job?.cancel();input.delete();File(input.path+".part").delete()}
}

@Composable internal fun ArchiveToolsScreen(model: ArchiveToolsViewModel,onBack:()->Unit) {
    val state by model.state.collectAsStateWithLifecycle();val scope=rememberCoroutineScope()
    val first=remember {FocusRequester()};LaunchedEffect(Unit){withFrameNanos{};first.requestFocus()}
    var exporting by remember {mutableStateOf(false)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        if(uri!=null)scope.launch {exporting=true;try{model.export(uri);model.message("Cópia salva no destino escolhido.")}
        catch(error:Exception){model.message(error.message?:"Falha ao salvar.")}finally{exporting=false;first.requestFocus()}}
    }
    BackHandler {if(state.busy)model.cancel() else if(!exporting)onBack()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(48.dp,27.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        item { ScreenTitle("Descompactador e conversor",model.name) }
        item { TvButton(if(state.busy)"Cancelar operação" else "Voltar",{if(state.busy)model.cancel() else onBack()},Modifier.focusRequester(first)) }
        item { Text(state.message,fontSize=18.sp,color=TvColors.Amber) }
        if(!state.busy && state.entries.isNotEmpty()) {
            item {TvButton("Extrair para uma nova pasta",{model.run(false)},enabled=!exporting)}
            item {TvButton("Converter páginas para CBZ",{model.run(true)},enabled=!exporting)}
        }
        state.result?.takeIf {it.isFile}?.let {result->item {TvButton(if(exporting)"Salvando…" else "Salvar CBZ em…",{if(!exporting)save.launch(result.name)})}}
        item { Text("Conteúdo · ${state.entries.size} entrada(s)",fontSize=22.sp) }
        items(state.entries,key={it.name}) {entry->Text("${if(entry.directory)"Pasta · " else ""}${entry.name} · ${entry.size/1024} KiB",fontSize=17.sp)}
    }
}

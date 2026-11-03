package com.pelican.comictv.tv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvChoice
import com.pelican.comictv.ui.TvColors
import com.pelican.comictv.ui.TvTextInput
import kotlinx.coroutines.*

@Composable internal fun TvAddonsScreen(repository:TvRepository,onBrowser:(String)->Unit) {
    val stored by repository.state.collectAsState()
    val scope=rememberCoroutineScope()
    val listState=rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<TvAddon?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var discovered by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var previewGeneration by remember { mutableIntStateOf(0) }
    var remove by remember { mutableStateOf<TvAddon?>(null) }
    var restoreInput by remember { mutableIntStateOf(0) }
    val inputFocus=remember { FocusRequester() }
    val previewFocus=remember { FocusRequester() }
    fun inspect(url:String) {
        previewJob?.cancel();val generation=++previewGeneration
        previewJob=scope.launch {
            busy=true;message="Consultando manifesto…";preview=null
            try { val result=repository.preview(url);if(generation==previewGeneration){preview=result;message=""} }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { if(generation==previewGeneration)message="Não foi possível ler o complemento. Use o link de instalação com manifest.json." }
            finally { if(generation==previewGeneration)busy=false }
        }
    }
    fun mutate(success:String,block:()->Unit) {
        scope.launch {
            try { withContext(Dispatchers.IO) { block() };message=success }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { message="Não foi possível salvar a alteração. Tente novamente." }
        }
    }
    LaunchedEffect(preview) {
        if(preview!=null) { listState.scrollToItem(if(stored.addonList.isNotEmpty())4 else 3);withFrameNanos { };runCatching { previewFocus.requestFocus() } }
    }
    LaunchedEffect(restoreInput) {
        if(restoreInput>0) { listState.scrollToItem(0);withFrameNanos { };runCatching { inputFocus.requestFocus() } }
    }
    LazyColumn(Modifier.fillMaxSize(),state=listState,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item("url") {
            Text("Adicionar complemento Stremio por URL",color=Color.White)
            TvTextInput(input,{
                input=it;preview=null;previewGeneration++;previewJob?.cancel();busy=false
            },Modifier.fillMaxWidth().focusRequester(inputFocus).border(1.dp,TvColors.Muted,RoundedCornerShape(8.dp)).padding(12.dp),maxLength=8192,onSubmit={inspect(input)})
        }
        item("actions") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                TvButton("Consultar link",{inspect(input)},enabled=!busy&&input.isNotBlank())
                TvButton("Oferecer catálogo Cinemeta",{inspect("https://v3-cinemeta.strem.io/manifest.json")},enabled=!busy)
            }
        }
        item("message") { if(message.isNotBlank())Text(message,color=Color.White) }
        if(stored.addonList.isNotEmpty())item("saved-list") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Lista salva pelo celular: ${stored.addonList.size} complementos. Fica só nesta TV, fora do backup.",color=TvColors.Muted)
                TvButton("Reinstalar lista salva",{
                    scope.launch {
                        busy=true;message="Consultando ${stored.addonList.size} complementos…"
                        try {
                            val items=repository.checkList(stored.addonList.joinToString("\n"))
                            val done=withContext(Dispatchers.IO) { repository.installChecked(items,items.filter { it.installable }.map { it.n }.toSet()) }
                            val installed=done.count { it.result=="Instalado" }
                            val already=done.count { it.status=="Já instalado" }
                            val skipped=done.size-installed-already
                            message="$installed instalados · $already já estavam · $skipped pulados"+
                                done.filter { it.result!="Instalado"&&it.status!="Já instalado" }.take(5).joinToString("") { "\n${it.name}: ${it.result.ifBlank { it.status }}" }
                        } catch(e:CancellationException) { throw e }
                        catch(_:Exception) { message="Não foi possível reinstalar a lista. Tente novamente." }
                        finally { busy=false }
                    }
                },enabled=!busy)
            }
        }
        preview?.let { addon -> item("preview") {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${addon.manifest.name} · ${addon.manifest.version}",color=Color.White,fontSize=22.sp)
                Text(addon.manifest.description,color=TvColors.Muted)
                Text("Tipos: ${addon.manifest.types.joinToString()} · Recursos: ${addon.manifest.resources.joinToString { it.name }}",color=TvColors.Muted)
                if(addon.manifest.configurable||addon.manifest.configurationRequired)TvButton("Configurar no navegador",{onBrowser(StremioLinks.configure(addon.url))},Modifier.focusRequester(previewFocus))
                if(addon.manifest.configurationRequired)Text("Conclua a configuração e cole o link configurado para instalar.",color=TvColors.Muted)
                TvButton("Instalar complemento",{
                    scope.launch {
                        busy=true
                        try { withContext(Dispatchers.IO) { repository.install(addon) };preview=null;input="";message="Complemento instalado.";restoreInput++ }
                        catch(e:CancellationException) { throw e }
                        catch(_:Exception) { message="Não foi possível instalar. Confira a configuração e o espaço do aparelho." }
                        finally { busy=false }
                    }
                },Modifier.then(if(!addon.manifest.configurable&&!addon.manifest.configurationRequired)Modifier.focusRequester(previewFocus)else Modifier),enabled=!busy&&!addon.manifest.configurationRequired)
            }
        } }
        if(stored.addons.isEmpty())item("empty") { Text("Nenhum complemento instalado. Um catálogo apresenta os títulos; um complemento de fontes oferece opções para assistir.",color=TvColors.Muted) }
        items(stored.addons,key={it.url}) { addon ->
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(addon.manifest.name,color=Color.White,fontSize=21.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TvButton(if(addon.enabled)"Desativar"else "Ativar",{mutate("Complemento atualizado."){repository.enabled(addon)}})
                    TvButton("Remover",{remove=addon})
                    if(addon.manifest.configurable)TvButton("Configurar",{onBrowser(StremioLinks.configure(addon.url))})
                    if(addon.manifest.addonCatalogs.isNotEmpty())TvButton("Explorar complementos",{
                        scope.launch {
                            busy=true
                            try { discovered=addon.manifest.addonCatalogs.flatMap { repository.client.addonCatalog(addon,it) }.distinct();message=if(discovered.isEmpty())"Nenhum complemento retornado."else "Escolha um complemento para consultar antes de instalar." }
                            catch(e:CancellationException) { throw e }
                            catch(_:Exception) { message="Catálogo de complementos indisponível." }
                            finally { busy=false }
                        }
                    },enabled=!busy)
                }
                if(remove?.url==addon.url) {
                    Text("Remover ${addon.manifest.name}? Sua lista e seu progresso serão mantidos.",color=Color.White)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TvButton("Cancelar",{remove=null})
                        TvButton("Confirmar remoção",{remove=null;mutate("Complemento removido."){repository.remove(addon)};restoreInput++})
                    }
                }
                addon.manifest.catalogs.forEach { catalog ->
                    val key=catalog.type+":"+catalog.id
                    TvChoice("${catalog.name} → Animes",key in addon.animeCatalogs,{mutate("Seção Animes atualizada."){repository.anime(addon,catalog)}},compact=true)
                }
            }
        }
        itemsIndexed(discovered) { index,url -> TvButton("Consultar complemento ${index+1}",{inspect(url)},enabled=!busy) }
        item("cache") {
            Text("Limite do cache de torrents",color=Color.White)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(1,2,4,8,16).forEach { gib -> TvChoice("$gib GiB",stored.cacheGiB==gib,{mutate("Limite atualizado."){repository.cache(gib)}},compact=true) }
            }
        }
    }
}

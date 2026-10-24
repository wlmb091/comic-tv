package com.pelican.comictv.offline

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.tv.material3.Text
import com.pelican.comictv.library.*
import com.pelican.comictv.ui.*

@Composable fun DownloadsScreen(viewModel: DownloadsViewModel, comics: List<ComicEntity>, initialId: String?, onBack: () -> Unit, onRead: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val titles = remember(comics) { comics.associate { it.id to it.title } }
    fun title(id: String) = titles[id] ?: "Edição " + id.takeLast(8)
    LaunchedEffect(initialId) { viewModel.enterScreen(initialId); first.requestFocus() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) viewModel.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.pause() }
    }
    BackHandler { viewModel.leave(onBack) }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenTitle("Downloads offline", "Uma edição por vez · pausa ao sair desta tela ou ir ao segundo plano") }
        if (busy) item { Text("Verificando arquivos e fila…",color = TvColors.Amber) }
        item { TvButton("Voltar", { viewModel.leave(onBack) }, Modifier.focusRequester(first)) }
        item {
            if (state.running) TvButton("Pausar downloads", viewModel::pause)
            else TvButton("Iniciar / Retomar fila", viewModel::start, enabled = !busy && state.queue.isNotEmpty())
        }
        state.active?.let { id -> item {
            Text(title(id), fontSize = 23.sp)
            Text("${state.done} / ${state.total} páginas · " + Formatter.formatFileSize(context,state.bytes))
            ProgressTrack(if (state.total == 0) 0f else state.done.toFloat()/state.total,Modifier.fillMaxWidth())
        } }
        state.message?.let { item { Text(it,color = TvColors.Amber) } }
        error?.let { item { Text(it,color = TvColors.Amber) } }
        item { Text("Fila · ${state.queue.size}",fontSize = 23.sp) }
        items(state.queue,key = { "queue:$it" }) { id ->
            Text(title(id),fontSize = 20.sp)
            TvButton("Remover da fila e apagar parciais",{deleting = id},enabled = !busy)
        }
        item { Text("Arquivos no aparelho · ${state.saved.size}",fontSize = 23.sp) }
        items(state.saved.distinct(),key = { "saved:$it" }) { id ->
            Text(title(id),fontSize = 20.sp)
            Text(if (id in state.complete) "✓ Disponível offline" else "Parcial / interrompido",color = TvColors.Muted)
            if (id in state.complete) TvButton("Ler sem internet",{viewModel.leave {onRead(id)}},enabled = !busy)
            else if (id !in state.queue) TvButton("Adicionar à fila para retomar",{viewModel.enter(id)},enabled = !busy && !state.running)
            TvButton("Remover download",{deleting = id},enabled = !busy)
        }
        if (!busy && state.saved.isEmpty() && state.queue.isEmpty()) item { Text("Abra uma edição online na biblioteca e escolha Baixar para ler offline.",color = TvColors.Muted) }
        item { Text("Downloads completos sobrevivem à limpeza do cache. Removê-los mantém a edição e o progresso; para obter as páginas novamente, será necessária a fonte online.",color = TvColors.Muted) }
    }
    deleting?.let { id ->
        val cancel = remember { FocusRequester() }
        LibraryDialog("Remover download?",{deleting = null}) {
            Text(title(id))
            Text("Os arquivos offline ou parciais desta edição serão apagados. A biblioteca, a página salva, favoritos e coleções serão mantidos.")
            TvButton("Cancelar",{deleting = null},Modifier.focusRequester(cancel))
            TvButton("Remover arquivos",{deleting = null;viewModel.remove(id)})
        }
        LaunchedEffect(id) {cancel.requestFocus()}
    }
}

package com.pelican.comictv.library

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pelican.comictv.ui.*
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Imported editions remain separate until the user explicitly assigns each to a named saga. */
@Composable internal fun SagaAssignmentDialog(comic: ComicEntity, repository: LibraryRepository, onDismiss: () -> Unit) {
    val collections by remember(repository) { repository.observeCollections() }.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    fun assign(id: String?) {
        if (busy) return
        scope.launch {
            busy = true
            try { repository.assignSaga(comic.id, id); onDismiss() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Não foi possível organizar a saga." }
            finally { busy = false }
        }
    }
    LibraryDialog("Saga · ${comic.title}", { if (!busy) onDismiss() }) {
        Text("Escolha a saga desta edição. Coleções comuns não agrupam automaticamente; atribua cada edição desejada aqui.", color = TvColors.Muted)
        TvButton("Criar saga", { creating = true }, Modifier.focusRequester(first), enabled = !busy)
        collections.forEach { row -> TvButton("Atribuir a · ${row.name}", { assign(row.id) }, enabled = !busy) }
        TvButton("Usar identificação original", { assign(null) }, enabled = !busy)
        error?.let { Text(it, color = TvColors.Amber) }
        TvButton("Cancelar", onDismiss, enabled = !busy)
    }
    LaunchedEffect(Unit) { first.requestFocus() }
    if (creating) CollectionNameDialog(repository, onDismiss = { creating = false }, onSaved = { creating = false; assign(it) })
}

@Composable internal fun SagaEditionsDialog(group: SagaGroup, repository: LibraryRepository,
    focusedId: String?, onFocus: (String) -> Unit, onOpen: (String) -> Unit,
    onOptions: (ComicEntity) -> Unit, onDismiss: () -> Unit, grid: LazyGridState) {
    var covers by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chooseFocus = remember { FocusRequester() }
    var pendingGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerReturn by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val expected = pendingGroup
        pendingGroup = null
        if (expected != group.key) return@rememberLauncherForActivityResult
        if (uri == null) pickerReturn++
        else scope.launch {
            busy = true; error = null
            try { repository.importSagaCover(group.key, uri) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Não foi possível salvar a imagem da saga." }
            finally { busy = false; pickerReturn++ }
        }
    }
    LaunchedEffect(pickerReturn) {
        if (pickerReturn > 0) { grid.scrollToItem(0); withFrameNanos { }; chooseFocus.requestFocus() }
    }
    fun back() { if (!busy) { if (covers) covers = false else onDismiss() } }
    fun selectCover(id: String?) {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try { repository.chooseSagaCover(group, id); covers = false }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Não foi possível salvar a capa." }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 24.dp, vertical = 18.dp)) {
            SagaEditionsPage(group.key, group.editions, grid, focusedId, chooseFocus, covers,
                coverModel = { repository.cover(it) }, onFocus = onFocus,
                onOpen = { edition -> if (!busy) { onFocus(edition.id); if (covers) selectCover(edition.id) else onOpen(edition.id) } },
                onOptions = { edition -> if (!busy) { onFocus(edition.id); onOptions(edition) } }) { focusEditions ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (covers) "Escolher capa · ${group.title}" else group.title, color = androidx.compose.ui.graphics.Color.White, fontSize = 24.sp)
                    Text(if (covers) "Escolha uma miniatura para usar como capa da saga. As edições permanecem intactas."
                        else "${group.editions.size} edições · OK abre a edição · Segure OK para opções", color = TvColors.Muted, fontSize = 15.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionDown) {
                                if (event.type == KeyEventType.KeyDown) focusEditions()
                                true
                            } else false
                        }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TvButton("Escolher imagem", {
                            if (!busy) try { pendingGroup = group.key; picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }
                            catch (_: ActivityNotFoundException) { pendingGroup = null; error = "Esta TV não oferece seletor de imagens." }
                        }, Modifier.focusRequester(chooseFocus))
                        if (!covers) TvButton("Usar capa de edição", { if (!busy) covers = true })
                        else TvButton("Capa original da saga", { selectCover(null) })
                        TvButton(if (covers) "Voltar às edições" else "Voltar à coleção", ::back)
                    }
                    if (busy) Text("Salvando capa…", color = TvColors.Muted, fontSize = 14.sp)
                    error?.let { Text(it, color = TvColors.Amber, fontSize = 15.sp) }
                }
            }
        }
    }
}

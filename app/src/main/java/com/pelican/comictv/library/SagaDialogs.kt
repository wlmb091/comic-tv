package com.pelican.comictv.library

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    onOptions: (ComicEntity) -> Unit, onDismiss: () -> Unit) {
    val first = remember(group.key) { FocusRequester() }
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
        if (pickerReturn > 0) { withFrameNanos { }; chooseFocus.requestFocus() }
    }
    LibraryDialog(if (covers) "Capa principal · ${group.title}" else group.title, { if (!busy) onDismiss() }) {
        Text("Capa própria da saga: JPEG, PNG ou WebP estático, até 20 MiB. O arquivo é copiado sem alterar as edições.", color = TvColors.Muted)
        TvButton("Escolher imagem da saga", {
            try { pendingGroup = group.key; picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }
            catch (_: ActivityNotFoundException) { pendingGroup = null; error = "Esta TV não oferece seletor de imagens." }
        }, Modifier.focusRequester(chooseFocus), enabled = !busy)

        Text(if (covers) "A capa escolhida permanece fixa ao continuar outras edições." else "${group.editions.size} edições na sua biblioteca", color = TvColors.Muted)
        group.editions.forEach { edition ->
            val focus = if (edition.id == (focusedId?.takeIf { id -> group.editions.any { it.id == id } }
                    ?: group.editions.first().id)) Modifier.focusRequester(first) else Modifier
            TvButton((if (covers) "Usar capa · " else "") + edition.title +
                (if (!covers && edition.lastRead > 0) " · ${readingStatus(edition)}" else ""), {
                if (covers) {
                    if (!busy) scope.launch {
                        busy = true
                        try { repository.chooseSagaCover(group, edition.id); covers = false }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "Não foi possível salvar a capa." }
                        finally { busy = false }
                    }
                } else { onFocus(edition.id); onOpen(edition.id) }
            }, focus.then(if (covers) Modifier else libraryCardKeys({ onFocus(edition.id); onOpen(edition.id) },
                { onFocus(edition.id); onOptions(edition) })), enabled = !busy)
        }
        error?.let { Text(it, color = TvColors.Amber) }
        if (!covers) TvButton("Usar capa de uma edição", { covers = true }, enabled = !busy)
        else TvButton("Usar capa original da saga", {
            if (!busy) scope.launch {
                busy = true
                try { repository.chooseSagaCover(group, null); covers = false }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "Não foi possível salvar a capa." }
                finally { busy = false }
            }
        }, enabled = !busy)
        TvButton(if (covers) "Voltar às edições" else "Voltar à coleção", { if (covers) covers = false else onDismiss() }, enabled = !busy)
    }
    LaunchedEffect(group.key, covers) { first.requestFocus() }
}

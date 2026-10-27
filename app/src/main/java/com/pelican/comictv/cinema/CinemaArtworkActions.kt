package com.pelican.comictv.cinema

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.library.LibraryRepository
import com.pelican.comictv.ui.TvButton
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Mounted in the options dialog: no operation or feedback survives leaving it. */
@Composable internal fun CinemaArtworkActions(id: String, repository: LibraryRepository) {
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val revision by repository.cinemaRevision.collectAsStateWithLifecycle()
    var hasArtwork by remember(id) { mutableStateOf(false) }
    var busy by remember(id) { mutableStateOf(false) }
    var message by remember(id) { mutableStateOf<String?>(null) }
    var operation by remember(id) { mutableStateOf<Job?>(null) }
    var confirmRemove by remember(id) { mutableStateOf(false) }
    var pickerEdition by remember { mutableStateOf<String?>(null) }
    var restoreFocus by remember(id) { mutableIntStateOf(0) }
    var restoreRemoval by remember(id) { mutableStateOf(false) }
    val chooseFocus = remember(id) { FocusRequester() }
    val removeFocus = remember(id) { FocusRequester() }
    fun stop() {
        operation?.cancel()
        operation = null
        busy = false
        message = null
    }
    DisposableEffect(owner, id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); stop() }
    }
    LaunchedEffect(id, revision) {
        try { hasArtwork = repository.cinemaArtwork(id) != null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            coroutineContext.ensureActive()
            message = "Não foi possível consultar a arte desta edição."
        }
    }
    fun execute(success: String, action: suspend () -> Unit) {
        if (busy || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        busy = true
        message = null
        operation = scope.launch {
            try {
                action()
                coroutineContext.ensureActive()
                hasArtwork = repository.cinemaArtwork(id) != null
                coroutineContext.ensureActive()
                message = success
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                coroutineContext.ensureActive()
                message = e.message ?: "Não foi possível alterar a arte. A imagem anterior foi preservada."
            }
            finally { if (coroutineContext[Job]?.isActive == true) busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val expectedEdition = pickerEdition
        pickerEdition = null
        if (expectedEdition != id) return@rememberLauncherForActivityResult
        restoreRemoval = false
        restoreFocus++
        if (uri != null) execute("Arte salva nesta edição. O arquivo original e a capa foram preservados.") {
            repository.importCinemaArtwork(id, uri)
        }
    }
    LaunchedEffect(restoreFocus, confirmRemove) {
        if (restoreFocus > 0 && !confirmRemove) {
            withFrameNanos { }
            if (restoreRemoval && hasArtwork && !busy) removeFocus.requestFocus() else chooseFocus.requestFocus()
        }
    }
    Text("Arte horizontal da biblioteca", color = TvColors.Muted)
    Text("Escolha uma imagem sua para esta edição. Sem busca automática. JPEG, PNG ou WebP estático horizontal, a partir de 1280 × 720, até 20 MiB. Recomendado: 1920 × 1080.", color = TvColors.Muted)
    TvButton(if (busy) "Cancelar alteração da arte" else if (hasArtwork) "Substituir arte horizontal" else "Escolher arte horizontal", {
        if (busy) { stop(); message = "Alteração cancelada." }
        else {
            message = null
            try { pickerEdition = id; picker.launch(arrayOf("image/*")) }
            catch (_: ActivityNotFoundException) {
                pickerEdition = null
                message = "Esta TV não oferece seletor de imagens."
            }
        }
    }, Modifier.focusRequester(chooseFocus))
    // Keep the action mounted while working so completion never removes a focused control.
    TvButton("Remover arte horizontal", { if (!busy && hasArtwork) confirmRemove = true },
        Modifier.focusRequester(removeFocus), enabled = hasArtwork && !busy)
    Text("A arte é copiada para o Comic TV e fica separada da capa. Artes não são incluídas no backup; limite total de 128 MiB.", color = TvColors.Muted)
    if (busy) Text("Preparando arte…", color = TvColors.Amber)
    message?.let { Text(it, color = TvColors.Amber) }
    if (confirmRemove) {
        val cancelFocus = remember { FocusRequester() }
        fun dismiss() { restoreRemoval = true; confirmRemove = false; restoreFocus++ }
        LibraryDialog("Remover arte horizontal?", ::dismiss) {
            Text("Remove somente a cópia da arte desta edição no Comic TV. A capa e o arquivo de origem são preservados.", color = TvColors.Muted)
            TvButton("Cancelar", ::dismiss, Modifier.focusRequester(cancelFocus))
            TvButton("Confirmar remoção", {
                dismiss()
                restoreRemoval = false
                execute("Arte horizontal removida.") { repository.removeCinemaArtwork(id) }
            })
        }
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    }
}

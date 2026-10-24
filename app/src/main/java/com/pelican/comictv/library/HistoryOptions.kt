package com.pelican.comictv.library

import android.os.SystemClock
import com.pelican.comictv.reader.CenterPressController
import com.pelican.comictv.reader.ReaderMode
import com.pelican.comictv.reader.ReaderAction
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Consume both halves of OK so a long press cannot also open the edition. */
@Composable internal fun libraryCardKeys(onOpen: () -> Unit, onOptions: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpen)
    val options by rememberUpdatedState(onOptions)
    var job by remember { mutableStateOf<Job?>(null) }
    val press = remember { CenterPressController() }
    fun cancel() { job?.cancel(); job = null; press.cancel() }
    DisposableEffect(Unit) { onDispose { cancel() } }
    return Modifier.onFocusChanged { if (!it.isFocused) cancel() }.onPreviewKeyEvent { event ->
        val center = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!center) false else {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 && press.down(SystemClock.uptimeMillis())) {
                job = scope.launch {
                    delay(1000)
                    if (press.hold(SystemClock.uptimeMillis()) == ReaderAction.OpenTools) options()
                }
            } else if (event.type == KeyEventType.KeyUp) {
                job?.cancel()
                if (event.nativeKeyEvent.isCanceled) press.cancel()
                else when (press.up(SystemClock.uptimeMillis(), ReaderMode.Reading)) {
                    ReaderAction.TogglePan -> open()
                    ReaderAction.OpenTools -> options()
                    else -> Unit
                }
            }
            true
        }
    }
}

@Composable internal fun HistoryOptions(
    comic: ComicEntity, repository: LibraryRepository, onOpen: () -> Unit, onRead: () -> Unit, onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var blockRelease by remember { mutableStateOf(true) }
    var confirmRemoval by remember { mutableStateOf(false) }
    var hidden by remember(comic.hiddenFromHistory) { mutableStateOf(comic.hiddenFromHistory) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { first.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 520.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState()).background(TvColors.Background).padding(24.dp)
            .onPreviewKeyEvent { event ->
                val center = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (blockRelease && center) {
                    if (event.type == KeyEventType.KeyUp || event.nativeKeyEvent.repeatCount == 0 && event.type == KeyEventType.KeyDown)
                        blockRelease = false
                    event.type == KeyEventType.KeyUp || event.nativeKeyEvent.repeatCount > 0
                } else false
            }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(comic.title, fontSize = 23.sp, maxLines = 3)
            TvButton(if (comic.lastRead > 0) "Continuar leitura" else "Começar leitura", onRead, Modifier.focusRequester(first), primary = true)
            TvButton("Abrir detalhes", onOpen)
            if (comic.lastRead > 0) TvButton(if (hidden) "Restaurar no histórico" else "Remover do histórico", {
                if (!hidden) confirmRemoval = true
                else if (!saving) scope.launch {
                    saving = true
                    try {
                        repository.setHistoryHidden(comic.id, !hidden)
                        hidden = !hidden
                        message = if (hidden) "Removido do histórico. A página salva foi mantida." else "Restaurado no histórico."
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { message = "Não foi possível alterar o histórico." }
                    finally { saving = false }
                }
            })
            message?.let { Text(it, color = TvColors.Amber) }
            TvButton("Fechar", onDismiss)
        }
    }
    if (confirmRemoval) ConfirmHistoryRemoval(comic.title, onDismiss = { confirmRemoval = false }, onConfirm = {
        repository.setHistoryHidden(comic.id, true)
        hidden = true
        message = "Removido do histórico."
    })
}

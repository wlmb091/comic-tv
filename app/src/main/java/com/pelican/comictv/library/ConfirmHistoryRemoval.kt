package com.pelican.comictv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun ConfirmHistoryRemoval(title: String, onDismiss: () -> Unit, onConfirm: suspend () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    com.pelican.comictv.ui.ComicDialog(onDismissRequest = { if (!busy) onDismiss() }) {
        com.pelican.comictv.companion.CompanionDialogWindow { if (!busy) onDismiss() }
        Column(Modifier.widthIn(max = 520.dp).heightIn(max = 460.dp).background(TvColors.Background)
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Remover do histórico?", fontSize = 24.sp)
            Text(title, fontSize = 18.sp, maxLines = 3)
            Text("O quadrinho continuará na biblioteca e a página salva será mantida. Ao retomar a leitura, ele volta ao histórico.",
                color = TvColors.Muted, fontSize = 17.sp)
            error?.let { Text(it, color = TvColors.Amber) }
            TvButton("Cancelar", { if (!busy) onDismiss() }, Modifier.focusRequester(cancelFocus))
            TvButton(if (busy) "Removendo…" else "Remover do histórico", {
                if (!busy) scope.launch {
                    busy = true
                    error = null
                    try { onConfirm(); onDismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Não foi possível remover do histórico. Tente novamente." }
                    finally { busy = false }
                }
            })
        }
    }
}

package com.pelican.comictv.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun ConfirmImportedRemoval(title: String, onDismiss: () -> Unit,
    onConfirm: suspend () -> Boolean, onDeleted: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cleanupPending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cancel = remember { FocusRequester() }
    val done = remember { FocusRequester() }
    LibraryDialog(if (cleanupPending) "Arquivo removido da biblioteca" else "Excluir da biblioteca?", {
        if (!busy) { if (cleanupPending) onDeleted() else onDismiss() }
    }) {
        Text(title)
        Text(if (cleanupPending) "A edição foi removida da biblioteca. Parte da limpeza ficou pendente; reabra o aplicativo e use Configurações → Armazenamento para limpar os caches." else
            "A cópia importada no aplicativo, o progresso, o favorito e os vínculos com coleções serão excluídos. O arquivo original no computador, pendrive ou pasta de origem será preservado.", color = TvColors.Muted)
        if (cleanupPending) {
            TvButton("Entendi", onDeleted, Modifier.focusRequester(done))
        } else {
            error?.let { Text(it, color = TvColors.Amber) }
            TvButton("Cancelar", { if (!busy) onDismiss() }, Modifier.focusRequester(cancel))
            TvButton(if (busy) "Excluindo…" else "Excluir arquivo importado", {
                if (!busy) scope.launch {
                    busy = true; error = null
                    try { if (onConfirm()) onDeleted() else cleanupPending = true }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Não foi possível excluir. O arquivo continua disponível; tente novamente." }
                    finally { busy = false }
                }
            })
        }
        Spacer(Modifier.height(2.dp))
    }
    LaunchedEffect(cleanupPending) { if (cleanupPending) done.requestFocus() else cancel.requestFocus() }
}

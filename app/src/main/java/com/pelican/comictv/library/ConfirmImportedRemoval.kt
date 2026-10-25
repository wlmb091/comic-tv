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
    onConfirm: suspend () -> Boolean, onDeleted: () -> Unit, online: Boolean = false) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cleanupPending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cancel = remember { FocusRequester() }
    val done = remember { FocusRequester() }
    LibraryDialog(if (cleanupPending) "Edição removida da biblioteca" else "Excluir da biblioteca?", {
        if (!busy) { if (cleanupPending) onDeleted() else onDismiss() }
    }) {
        Text(title)
        Text(if (cleanupPending) "A edição saiu da biblioteca. Parte da limpeza ficou pendente; verifique Downloads para arquivos offline restantes e Configurações → Armazenamento para os caches." else if (online)
            "Esta edição, seu progresso, favorito, vínculos com coleções e download offline serão removidos do aplicativo. Ela também sairá da fila de downloads. O título continuará disponível no catálogo da fonte para adicionar novamente." else
            "A cópia importada no aplicativo, o progresso, o favorito e os vínculos com coleções serão excluídos. O arquivo original no computador, pendrive ou pasta de origem será preservado.", color = TvColors.Muted)
        if (cleanupPending) {
            TvButton("Entendi", onDeleted, Modifier.focusRequester(done))
        } else {
            error?.let { Text(it, color = TvColors.Amber) }
            TvButton("Cancelar", { if (!busy) onDismiss() }, Modifier.focusRequester(cancel))
            TvButton(if (busy) "Excluindo…" else if (online) "Excluir edição da biblioteca" else "Excluir arquivo importado", {
                if (!busy) scope.launch {
                    busy = true; error = null
                    try { if (onConfirm()) onDeleted() else cleanupPending = true }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Não foi possível concluir a exclusão. Tente novamente." }
                    finally { busy = false }
                }
            })
        }
        Spacer(Modifier.height(2.dp))
    }
    LaunchedEffect(cleanupPending) { if (cleanupPending) done.requestFocus() else cancel.requestFocus() }
}

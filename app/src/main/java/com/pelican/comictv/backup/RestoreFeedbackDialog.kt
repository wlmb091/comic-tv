package com.pelican.comictv.backup

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.ui.TvButton

/** Result stays above the scrolled settings content until the user acknowledges it. */
@Composable internal fun RestoreFeedbackDialog(result: RestoreResult?, failed: Boolean, onDismiss: () -> Unit) {
    val first = remember {FocusRequester()}
    LibraryDialog(if (failed) "Restauração não concluída" else "Restauração concluída",onDismiss) {
        if (failed) Text("Não foi possível concluir a restauração. Confira o arquivo e o espaço livre. Se houve interrupção, o aplicativo tentará concluir a recuperação na próxima abertura.")
        else result?.let {
            Text("${it.added} adicionadas · ${it.updated} mescladas")
            Text(if (it.pending == 0) "Nenhum original local pendente de reimportação."
                else "${it.pending} quadrinho(s) local(is) aguardam reimportação do mesmo arquivo original.")
        }
        TvButton("Entendi",onDismiss,Modifier.focusRequester(first))
    }
    LaunchedEffect(Unit) {first.requestFocus()}
}

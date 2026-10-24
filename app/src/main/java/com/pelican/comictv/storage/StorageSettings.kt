package com.pelican.comictv.storage

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.ui.*

@Composable fun StorageSettings(viewModel: StorageViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<CacheSelection?>(null) }
    val context = LocalContext.current
    fun bytes(value: Long) = Formatter.formatFileSize(context, value.coerceAtLeast(0))
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
        item { Text("Armazenamento", fontSize = 25.sp) }
        item { Text("Arquivos conhecidos do Comic TV. Estes valores não incluem todos os dados do aplicativo ou do Android.", color = TvColors.Muted) }
        if (state.busy) item { Text("Calculando / atualizando…", color = TvColors.Amber) }
        state.report?.let { report ->
            item { Text("Livre no armazenamento interno · " + bytes(report.freeBytes), fontSize = 21.sp) }
            item { Text("Originais importados · " + bytes(report.originals)) }
            item { Text("Capas salvas · " + bytes(report.covers)) }
            item { Text("Cache de páginas · " + bytes(report.pages)) }
            item { Text("Miniaturas · " + bytes(report.thumbnails)) }
            if (report.skipped > 0) item { Text("${report.skipped} caminho(s) não foram contabilizados por acesso indisponível ou link externo.", color = TvColors.Amber) }
        }
        state.cleanup?.let { result -> item {
            Text("Arquivos removidos · " + bytes(result.freedBytes), color = TvColors.Amber)
            if (result.failures > 0) Text("Limpeza parcial: ${result.failures} arquivo(s) ou caminho(s) não puderam ser removidos. Você pode tentar novamente.")
        } }
        state.error?.let { item { Text(it, color = TvColors.Amber) } }
        item { TvButton("Atualizar valores", viewModel::refresh, enabled = !state.busy) }
        CacheSelection.entries.forEach { selection -> item {
            TvButton(when (selection) { CacheSelection.Pages -> "Limpar cache de páginas"; CacheSelection.Thumbnails -> "Limpar miniaturas"; CacheSelection.Both -> "Limpar ambos" },
                { pending = selection }, enabled = !state.busy && state.report != null)
        } }
        item { Text("A limpeza mantém quadrinhos, capas, progresso, coleções e configurações. Páginas e miniaturas serão preparadas novamente quando necessário; imagens online podem precisar de internet.", color = TvColors.Muted) }
        item { TvButton("Voltar à biblioteca", onBack) }
    }
    pending?.let { selection ->
        val first = remember { FocusRequester() }
        val estimated = state.report?.let { when (selection) { CacheSelection.Pages -> it.pages; CacheSelection.Thumbnails -> it.thumbnails; CacheSelection.Both -> it.pages + it.thumbnails } } ?: 0L
        LibraryDialog("Limpar ${selection.label}?", { pending = null }) {
            Text("Estimativa de arquivos a remover: " + bytes(estimated))
            Text("Seus quadrinhos e a página salva serão mantidos. As imagens temporárias precisarão ser preparadas novamente.")
            TvButton("Cancelar", { pending = null }, Modifier.focusRequester(first))
            TvButton("Confirmar limpeza", { pending = null; viewModel.clearCaches(selection) })
        }
        LaunchedEffect(selection) { first.requestFocus() }
    }
}

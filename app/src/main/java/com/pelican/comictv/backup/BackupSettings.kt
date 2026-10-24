package com.pelican.comictv.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.ui.*

@Composable fun BackupSettings(viewModel: BackupViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.stopSession() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.stopSession() }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(viewModel::save) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::preview) }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(8.dp)) {
        item { Text("Backup e restauração", fontSize = 25.sp) }
        item { Text("Protege progresso, histórico, favoritos, coleções e preferências. Os arquivos dos quadrinhos e downloads ficam separados.", color = TvColors.Muted) }
        item { TvButton(if (state.busy) "Aguarde…" else "Criar backup", viewModel::prepare, enabled = !state.busy) }
        item { TvButton("Salvar backup em arquivo", {
            try { save.launch(state.file?.name ?: "Comic-TV.ctvbackup") }
            catch (_: ActivityNotFoundException) { viewModel.notice("Esta TV não oferece seletor de arquivos. Use Transferir pela rede local.") }
        }, enabled = !state.busy && state.file != null) }
        item { TvButton("Escolher backup para restaurar", {
            try { open.launch(arrayOf("*/*")) }
            catch (_: ActivityNotFoundException) { viewModel.notice("Use a rede local para enviar o backup e confirmar a restauração na TV.") }
        }, enabled = !state.busy) }
        item { Text("${state.pending} quadrinho(s) local(is) aguardam reimportação do mesmo arquivo original. O progresso será reconhecido pela impressão digital do arquivo.", color = TvColors.Muted) }
        state.message?.let { item { Text(it, color = TvColors.Amber) } }
        if (state.url == null) item { TvButton("Transferir pela rede local · QR", viewModel::startSession, enabled = !state.busy) }
        else {
            item { Text(state.url!!, fontSize = 20.sp) }
            item { state.qr?.let { Image(it.asImageBitmap(), "QR da sessão de backup", Modifier.size(210.dp)) } }
            item { Text("No navegador, baixe o backup preparado ou envie um backup para prévia. A sessão termina em até 15 minutos, ao sair ou ir ao segundo plano.", color = TvColors.Muted) }
            item { TvButton("Encerrar sessão", viewModel::stopSession) }
        }
    }
    state.preview?.let { document ->
        val first = remember { FocusRequester() }
        LibraryDialog("Restaurar backup?", viewModel::dismissPreview) {
            Text("${document.comics.size} edições · ${document.collections.size} coleções · ${document.comics.count { it.favorite }} favoritos")
            Text("Data: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(document.createdAt)))
            Text("A biblioteca atual será preservada. O progresso mais recente vence; favoritos e coleções são unidos. Arquivos locais ausentes precisarão ser reimportados.")
            TvButton(if (state.restoreAppearance) "✓ Restaurar aparência" else "Restaurar aparência: não", viewModel::toggleAppearance, enabled = !state.busy)
            TvButton("Cancelar", viewModel::dismissPreview, Modifier.focusRequester(first), enabled = !state.busy)
            TvButton(if (state.busy) "Restaurando…" else "Confirmar restauração", viewModel::restore, enabled = !state.busy)
        }
        LaunchedEffect(document) { first.requestFocus() }
    }
    if (state.restoreFeedback != null || state.restoreFailed)
        RestoreFeedbackDialog(state.restoreFeedback,state.restoreFailed,viewModel::dismissRestoreFeedback)
}

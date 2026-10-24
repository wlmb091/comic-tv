package com.pelican.comictv.details

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.library.*
import com.pelican.comictv.sources.OnlineEditionInfo
import com.pelican.comictv.ui.*
import kotlinx.coroutines.CancellationException

@Composable fun DetailsScreen(
    id: String,
    repository: LibraryRepository,
    onRead: () -> Unit,
    onBack: () -> Unit,
    loadOnlineInfo: suspend (String) -> OnlineEditionInfo? = { null },
) {
    var comic by remember(id) { mutableStateOf<ComicEntity?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var onlineInfo by remember(id) { mutableStateOf<OnlineEditionInfo?>(null) }
    var metadataLoading by remember(id) { mutableStateOf(id.startsWith("online-")) }
    var expandedSummary by remember(id) { mutableStateOf(false) }
    val scrollScope = rememberCoroutineScope()
    var confirmRemoval by remember(id) { mutableStateOf(false) }
    var changingHistory by remember { mutableStateOf(false) }
    val summaryScroll = rememberLazyListState()
    var summaryFocused by remember { mutableStateOf(false) }
    val first = remember(id) { FocusRequester() }
    val isOnline = id.startsWith("online-")
    LaunchedEffect(id) {
        try {
            comic = repository.getComic(id)
            if (comic == null) error = "Esta edição não está mais na biblioteca."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Não foi possível carregar a edição." }
    }
    // Optional stored metadata must never delay loading or focusing the reading action.
    LaunchedEffect(id, loadOnlineInfo) {
        if (isOnline) {
            metadataLoading = true
            try { onlineInfo = loadOnlineInfo(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { onlineInfo = null }
            finally { metadataLoading = false }
        }
    }
    LaunchedEffect(comic != null, error) { if (comic != null || error != null) first.requestFocus() }
    if (confirmRemoval) ConfirmHistoryRemoval(comic?.title.orEmpty(),
        onDismiss = { confirmRemoval = false },
        onConfirm = { repository.setHistoryHidden(id, true); comic = repository.getComic(id) })
    BoxWithConstraints(Modifier.fillMaxSize().background(TvColors.Background).padding(24.dp)) {
        // The cover sets the shared height; both side panels remain independently scrollable.
        val coverWidth = minOf(maxHeight * (2f / 3f), maxWidth * .36f)
        val panelHeight = coverWidth * 1.5f
        Row(Modifier.fillMaxWidth().height(panelHeight).align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (isOnline) "SUA BIBLIOTECA / EDIÇÃO ONLINE" else "SUA BIBLIOTECA / EDIÇÃO LOCAL",
                    fontSize = 13.sp, color = TvColors.Amber)
                Text(onlineInfo?.title?.takeIf { it.isNotBlank() } ?: comic?.title
                    ?: if (error != null) "Edição indisponível" else "Carregando edição…",
                    fontSize = 26.sp, lineHeight = 34.sp)
                onlineInfo?.let { info ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (info.chapter.isNotBlank()) Text(info.chapter, fontSize = 20.sp)
                        Text(listOf(info.sourceName, info.language).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 16.sp, color = TvColors.Muted)
                    }
                }
                comic?.let { item ->
                    val count = item.pageCount.coerceAtLeast(0)
                    val read = if (item.lastRead > 0) (item.currentPage + 1).coerceIn(0, count) else 0
                    val progress = if (count > 0) read.toFloat() / count else 0f
                    val status = when {
                        item.lastRead == 0L -> "Não iniciada"
                        count > 0 && read >= count -> "Concluída"
                        else -> "Em andamento"
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(status + " • " + (progress * 100).toInt() + "% lido", fontSize = 18.sp)
                        Text(if (read > 0) "Página " + read + " de " + count else count.toString() + " páginas",
                            color = TvColors.Muted, fontSize = 16.sp)
                        ProgressTrack(progress, Modifier.fillMaxWidth())
                    }
                }
                error?.let { Text(it, color = TvColors.Amber, fontSize = 16.sp) }
                comic?.let { item ->
                    TvButton(if (item.lastRead > 0) "Continuar leitura" else "Começar leitura",
                        onRead, Modifier.focusRequester(first), primary = true)
                    if (item.lastRead > 0) {
                        TvButton(if (item.hiddenFromHistory) "Restaurar no histórico" else "Remover do histórico", {
                            if (!item.hiddenFromHistory) confirmRemoval = true
                            else if (!changingHistory) scrollScope.launch {
                                changingHistory = true
                                try {
                                    repository.setHistoryHidden(id, !item.hiddenFromHistory)
                                    comic = repository.getComic(id)
                                } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { error = "Não foi possível alterar o histórico." }
                                finally { changingHistory = false }
                            }
                        })
                    }
                }
                TvButton("Voltar à biblioteca", onBack,
                    if (comic == null) Modifier.focusRequester(first) else Modifier)
            }
            AsyncImage(repository.cover(id), "Capa da edição",
                Modifier.width(coverWidth).fillMaxHeight(), contentScale = ContentScale.Fit)
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight().background(TvColors.Panel)
                    .border(if (summaryFocused) 2.dp else 0.dp, TvColors.Amber)
                    .onFocusChanged { summaryFocused = it.hasFocus }
                    .textPanelScroll(summaryScroll),
                state = summaryScroll, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Sobre esta edição", fontSize = 20.sp, lineHeight = 28.sp)
                        val summary = onlineInfo?.description?.trim()?.takeIf { it.isNotEmpty() }
                        if (summary != null) TvButton(
                            if (expandedSummary) "Recolher resumo" else "Mostrar resumo completo",
                            { expandedSummary = !expandedSummary })
                        Text(summary ?: when {
                            metadataLoading -> "Carregando informações salvas…"
                            isOnline -> "Esta edição não tem sinopse salva."
                            else -> "Edição importada de um arquivo local. Nenhuma sinopse está disponível."
                        }, fontSize = 17.sp, lineHeight = 26.sp, color = TvColors.Muted,
                            maxLines = if (expandedSummary) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

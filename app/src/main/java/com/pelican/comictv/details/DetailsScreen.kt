package com.pelican.comictv.details

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
    val metadataScroll = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var changingHistory by remember { mutableStateOf(false) }
    val summaryScroll = rememberLazyListState()
    var summaryFocused by remember { mutableStateOf(false) }
    var metadataFocused by remember { mutableStateOf(false) }
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
    Row(
        Modifier.fillMaxSize().background(TvColors.Background).padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AsyncImage(
            repository.cover(id), "Capa da edição",
            Modifier.width(180.dp).fillMaxHeight(), contentScale = ContentScale.Fit,
        )
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth()
                .border(if (metadataFocused) 2.dp else 0.dp, TvColors.Amber)
                .onFocusChanged { metadataFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val delta = when {
                        !metadataFocused || event.type != KeyEventType.KeyDown -> 0f
                        event.key == Key.DirectionDown && metadataScroll.canScrollForward -> 120f
                        event.key == Key.DirectionUp && metadataScroll.canScrollBackward -> -120f
                        else -> 0f
                    }
                    if (delta == 0f) false else {
                        scrollScope.launch { metadataScroll.scrollBy(delta) }
                        true
                    }
                }.focusable(),
            state = metadataScroll,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    if (isOnline) "SUA BIBLIOTECA  /  EDIÇÃO ONLINE" else "SUA BIBLIOTECA  /  EDIÇÃO LOCAL",
                    fontSize = 13.sp, lineHeight = 18.sp, color = TvColors.Amber,
                )
            }
            item {
                Text(
                    onlineInfo?.title?.takeIf { it.isNotBlank() } ?: comic?.title
                        ?: if (error != null) "Edição indisponível" else "Carregando edição…",
                    fontSize = 26.sp, lineHeight = 34.sp,
                )
            }
            onlineInfo?.let { info ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        info.chapter.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 20.sp, lineHeight = 28.sp)
                        }
                        Text(
                            listOf(info.sourceName, info.language).filter { it.isNotBlank() }.joinToString("  •  "),
                            fontSize = 16.sp, lineHeight = 24.sp, color = TvColors.Muted,
                        )
                    }
                }
            }
            comic?.let { item ->
                item {
                    val pageCount = item.pageCount.coerceAtLeast(0)
                    val readPages = if (item.lastRead > 0) (item.currentPage + 1).coerceIn(0, pageCount) else 0
                    val progress = if (pageCount > 0) readPages.toFloat() / pageCount else 0f
                    val status = when {
                        item.lastRead == 0L -> "Não iniciada"
                        pageCount > 0 && readPages >= pageCount -> "Concluída"
                        else -> "Em andamento"
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("$status  •  ${(progress * 100).toInt()}% lido", fontSize = 18.sp, lineHeight = 26.sp)
                        Text(
                            if (readPages > 0) "Página $readPages de $pageCount" else "$pageCount páginas",
                            fontSize = 16.sp, lineHeight = 24.sp, color = TvColors.Muted,
                        )
                        ProgressTrack(progress, Modifier.fillMaxWidth())
                    }
                }

            }
            error?.let { message -> item { Text(message, fontSize = 18.sp, lineHeight = 26.sp) } }


        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            comic?.let { item ->
                TvButton(if (item.lastRead > 0) "Continuar leitura" else "Começar leitura",
                    onRead, Modifier.focusRequester(first), primary = true)
            }
            comic?.takeIf { it.lastRead > 0 }?.let { item ->
                TvButton(if (item.hiddenFromHistory) "Restaurar no histórico" else "Remover do histórico", {
                    if (!changingHistory) scrollScope.launch {
                        changingHistory = true
                        try {
                            repository.setHistoryHidden(id, !item.hiddenFromHistory)
                            comic = repository.getComic(id)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "Não foi possível alterar o histórico." }
                        finally { changingHistory = false }
                    }
                })
                Text("O quadrinho e a página salva são mantidos.", color = TvColors.Muted, fontSize = 13.sp)
            }
            TvButton("Voltar à biblioteca", onBack,
                if (comic == null) Modifier.focusRequester(first) else Modifier)
        }
        }
        LazyColumn(
            Modifier.weight(.9f).fillMaxHeight().background(TvColors.Panel)
                .border(if (summaryFocused) 2.dp else 0.dp, TvColors.Amber)
                .onFocusChanged { summaryFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    val delta = when {
                        !summaryFocused || event.type != KeyEventType.KeyDown -> 0f
                        event.key == Key.DirectionDown && summaryScroll.canScrollForward -> 120f
                        event.key == Key.DirectionUp && summaryScroll.canScrollBackward -> -120f
                        else -> 0f
                    }
                    if (delta == 0f) false else { scrollScope.launch { summaryScroll.scrollBy(delta) }; true }
                }.focusable(),
            state = summaryScroll, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sobre esta edição", fontSize = 20.sp, lineHeight = 28.sp)
                    val summary = onlineInfo?.description?.trim()?.takeIf { it.isNotEmpty() }
                    if (summary != null) {
                        TvButton(
                            if (expandedSummary) "Recolher resumo" else "Mostrar resumo completo",
                            { expandedSummary = !expandedSummary },
                        )
                    }
                    Text(
                        summary ?: when {
                            metadataLoading -> "Carregando informações salvas…"
                            isOnline -> "Esta edição não tem sinopse salva."
                            else -> "Edição importada de um arquivo local. Nenhuma sinopse está disponível."
                        },
                        fontSize = 17.sp, lineHeight = 26.sp, color = TvColors.Muted,
                        maxLines = if (expandedSummary) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )

                }
            }
        }
    }
}

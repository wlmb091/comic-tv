package com.pelican.comictv.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pelican.comictv.library.*
import com.pelican.comictv.ui.*
import kotlinx.coroutines.CancellationException

@Composable fun DetailsScreen(id: String, repository: LibraryRepository, onRead: () -> Unit, onBack: () -> Unit) {
    var comic by remember(id) { mutableStateOf<ComicEntity?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(id) {
        try { comic = repository.getComic(id) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }
    LaunchedEffect(comic, error) { if (comic != null || error != null) first.requestFocus() }
    Row(Modifier.fillMaxSize().background(TvColors.Background).padding(48.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
        AsyncImage(repository.cover(id), null, Modifier.fillMaxHeight().aspectRatio(2f / 3f), contentScale = ContentScale.Fit)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text("SUA BIBLIOTECA  /  EDIÇÃO LOCAL", fontSize = 13.sp, color = TvColors.Amber)
            Spacer(Modifier.height(20.dp))
            Text(comic?.title ?: "Carregando edição…", fontSize = 34.sp, maxLines = 3)
            Spacer(Modifier.height(18.dp))
            comic?.let { item ->
                Text("${item.pageCount} páginas", fontSize = 18.sp, color = TvColors.Muted)
                Spacer(Modifier.height(28.dp))
                Text(if (item.lastRead > 0) "Página ${item.currentPage + 1} de ${item.pageCount}" else "Uma nova história para descobrir.", fontSize = 18.sp)
                Spacer(Modifier.height(14.dp))
                ProgressTrack(if (item.lastRead > 0) (item.currentPage + 1f) / item.pageCount else 0f, Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))
                TvButton(if (item.lastRead > 0) "Continuar leitura" else "Começar leitura", onRead, Modifier.focusRequester(first), primary = true)
                Spacer(Modifier.height(16.dp))
            }
            error?.let { Text(it, fontSize = 18.sp); Spacer(Modifier.height(20.dp)) }
            if (comic != null || error != null) TvButton("Voltar à biblioteca", onBack,
                if (comic == null) Modifier.focusRequester(first) else Modifier)
        }
    }
}

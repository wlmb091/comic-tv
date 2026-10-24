package com.pelican.comictv.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.sources.*
import com.pelican.comictv.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Opening a recommendation does not add it to the library until an edition is chosen. */
@Composable internal fun RelatedTitleScreen(item: RelatedComic?, online: OnlineReaderRepository,
    onRead: (String) -> Unit, onBack: () -> Unit) {
    var details by remember(item) { mutableStateOf<OnlineDetails?>(null) }
    var loading by remember(item) { mutableStateOf(item != null) }
    var opening by remember { mutableStateOf(false) }
    var error by remember(item) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(item) {
        first.requestFocus()
        if (item != null) {
            try { details = online.relatedDetails(item) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Não foi possível carregar esta obra. Confira a fonte e a conexão." }
            finally { loading = false }
        }
    }
    BackHandler(enabled = opening) { }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(item?.title?.title ?: "Obra indisponível", item?.reasons?.joinToString(" • ").orEmpty())
        TvButton("Voltar aos relacionados", { if (!opening) onBack() }, Modifier.focusRequester(first))
        if (loading) Text("Carregando edições…", color = TvColors.Muted)
        if (opening) Text("Abrindo leitura…", color = TvColors.Amber)
        error?.let { Text(it, color = TvColors.Amber) }
        details?.let { loaded ->
            Text(loaded.title.description?.take(450).orEmpty(), fontSize = 17.sp, maxLines = 4, color = TvColors.Muted)
            Text("Escolha uma edição", fontSize = 23.sp)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(loaded.chapters, key = { index, chapter -> index.toString() + chapter.key }) { _, chapter ->
                    TvButton(chapter.title, {
                        if (!opening && item != null) scope.launch {
                            opening = true
                            error = null
                            try {
                                val id = online.open(item.source, loaded.title, chapter, item.pin)
                                onRead(id)
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "Não foi possível abrir esta edição." }
                            finally { opening = false }
                        }
                    }, Modifier.fillMaxWidth())
                }
                if (loaded.chapters.isEmpty()) item { Text("Nenhuma edição fornecida por esta fonte.", color = TvColors.Muted) }
            }
        }
    }
}

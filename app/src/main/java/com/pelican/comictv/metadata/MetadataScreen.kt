package com.pelican.comictv.metadata

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.pelican.comictv.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable fun MetadataEntry(query: String, below: FocusRequester? = null, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sobre a obra · metadados e notas", fontSize = 22.sp, color = Color.White)
        Text("Consulta separada, somente por sua escolha. Não adiciona títulos à biblioteca.", color = TvColors.Muted)
        TvButton("Consultar livros, quadrinhos ou mangás", { onOpen(query) },
            Modifier.focusProperties { if (below != null) down = below })
    }
}

@Composable fun MetadataScreen(seed: String, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember { MetadataRepository(File(context.cacheDir, "work-metadata")) }
    var query by rememberSaveable { mutableStateOf(seed.take(200)) }
    var batch by remember { mutableStateOf<MetadataBatch?>(null) }
    var selected by remember { mutableStateOf<MetadataWork?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val uri = LocalUriHandler.current
    fun cancel() { generation++; job?.cancel(); job = null; busy = false }
    fun back() { if (selected != null) selected = null else { cancel(); onBack() } }
    BackHandler(onBack = ::back)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); job?.cancel() }
    }
    fun search(provider: MetadataProvider) {
        cancel(); selected = null; error = null; batch = null
        if (query.trim().length < 2) { error = "Digite ao menos 2 caracteres."; return }
        busy = true
        val requestGeneration = generation
        job = scope.launch { try { batch = repository.search(query, provider) } finally { if (generation == requestGeneration) busy = false } }
    }
    Column(Modifier.fillMaxSize().background(TvColors.Background).verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sobre a obra", color = Color.White, fontSize = 28.sp)
        TvButton(if (selected == null) "Voltar" else "Voltar aos candidatos", ::back, Modifier.focusRequester(first))
        Text("Dados da obra ou série, que podem abranger várias edições. Confira autor, ano e identificadores. Notas de filmes e adaptações não são usadas. Sua edição não é alterada.", color = TvColors.Muted)
        val work = selected
        if (work == null) {
            Text("Título, autor ou ISBN · OK abre o teclado", color = Color.White)
            TvTextInput(query, { cancel(); query = it.take(200); batch = null }, Modifier.fillMaxWidth().heightIn(min = 48.dp), maxLength = 200)
            // Controls remain mounted during requests; completion never removes the focused action.
            TvButton("Consultar Open Library · livros e quadrinhos", { search(MetadataProvider.OPEN_LIBRARY) })
            TvButton("Consultar AniList · mangás", { search(MetadataProvider.ANILIST) })
            TvButton("Cancelar consulta", ::cancel)
            if (busy) Text("Consultando…", color = TvColors.Muted)
            error?.let { Text(it, color = TvColors.Amber) }
            batch?.let { result ->
                result.message?.let { Text(it, color = TvColors.Amber) }
                if (result.cached) Text("Dados em cache · consulta anterior", color = TvColors.Muted)
                if (result.works.isEmpty()) Text("Nenhum candidato disponível. Isso não confirma ausência da obra na fonte.", color = TvColors.Muted)
                result.works.forEach { candidate ->
                    TvButton("${candidate.title} · ${candidate.authors.ifBlank { "Autor indisponível" }} · ${candidate.year.ifBlank { "Ano indisponível" }} · ${candidate.provider.label}", { selected = candidate })
                }
            }
        } else {
            Text(work.title, color = Color.White, fontSize = 24.sp)
            Text("Fonte: ${work.provider.label} · ${work.format}", color = TvColors.Muted)
            Text("Autores/créditos: ${work.authors.ifBlank { "Indisponíveis" }}\nAno: ${work.year.ifBlank { "Indisponível" }}\nIdentificadores: ${work.identifiers.ifBlank { "Indisponíveis" }}", color = Color.White)
            Text(work.rating?.let { "Nota da obra: ${it.value}/${it.scale} · ${it.count?.let { count -> "$count avaliações" } ?: "Quantidade de avaliações não informada"}" } ?: "Nota da obra: indisponível nesta fonte.", color = Color.White)
            Text(work.synopsis.ifBlank { "Sinopse indisponível nesta consulta." }, color = Color.White)
            Text(work.url, color = TvColors.Muted)
            TvButton("Abrir página da fonte", { runCatching { uri.openUri(work.url) } })
        }
        Text("Atribuição: Open Library / Internet Archive e AniList. Consulta pública complementar de leitura, sem sincronização de listas. Disponibilidade e notas dependem da fonte.", color = TvColors.Muted)
    }
    LaunchedEffect(selected) { first.requestFocus() }
}

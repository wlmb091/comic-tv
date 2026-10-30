package com.pelican.comictv.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.pelican.comictv.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class SiteSearchResult(val hits: List<BrowserSiteHit> = emptyList(), val loading: Boolean = true, val error: String? = null)
internal class BrowserSiteSearchModel(private val fetch: suspend (BrowserSite, String) -> List<BrowserSiteHit> = BrowserSites::search) : ViewModel() {
    var results by mutableStateOf<Map<String, SiteSearchResult>>(emptyMap()); private set
    private val cache = linkedMapOf<String, Map<String, SiteSearchResult>>()
    private var key = ""
    private var generation = 0
    private var job: Job? = null
    fun search(query: String, retry: Int = 0) {
        val q = query.trim().take(300)
        val refresh = generation != retry
        if (q == key && !refresh) return
        key = q; generation = retry; job?.cancel()
        if (refresh) cache.remove(q)
        if (q.isBlank()) { results = emptyMap(); return }
        cache[q]?.let { results = it; return }
        results = BrowserSites.all.associate { it.id to SiteSearchResult() }
        job = viewModelScope.launch {
            delay(700)
            val permits = Semaphore(2)
            coroutineScope { BrowserSites.all.forEach { site -> launch {
                val result = if (site.kind == "manual") SiteSearchResult(loading = false, error = "Pesquisa disponível pelo navegador.")
                else permits.withPermit {
                    try { SiteSearchResult(fetch(site, q), loading = false) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { SiteSearchResult(loading = false, error = "Não foi possível consultar este site. Abra a pesquisa no navegador.") }
                }
                ensureActive()
                if (key == q) results = results + (site.id to result)
            } } }
            if (key == q) {
                cache[q] = results
                while (cache.size > 5) cache.remove(cache.keys.first())
            }
        }
    }
}

@Composable internal fun BrowserSiteIcon(site: BrowserSite, compact: Boolean = false) {
    var failed by remember(site.id) { mutableStateOf(false) }
    Box(Modifier.size(if (compact) 28.dp else 36.dp), contentAlignment = Alignment.Center) {
        if (failed) Text(site.name.take(2).uppercase(), color = Color.White, fontSize = 17.sp)
        else AsyncImage(site.icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, onError = { failed = true })
    }
}

@Composable internal fun BrowserHome(onOpen: (String) -> Unit, onSheet: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sites = if (expanded) BrowserSites.all else BrowserSites.all.take(12)
    LazyVerticalGrid(GridCells.Adaptive(165.dp), Modifier.fillMaxSize().background(TvColors.Background),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 145.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Navegador", color = Color.White, fontSize = 28.sp)
            Text("Sites de quadrinhos", color = TvColors.Muted, fontSize = 18.sp)
        } }
        items(sites, key = { it.id }) { site ->
            Card(onClick = { onOpen(site.home) }, modifier = Modifier.fillMaxWidth().focusRaised(),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusAccent))),
                scale = CardDefaults.scale(focusedScale = 1.04f),
                colors = CardDefaults.colors(containerColor = TvColors.Panel, focusedContainerColor = TvColors.Panel)) {
                Row(Modifier.padding(12.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrowserSiteIcon(site, compact = true)
                    Text(site.name, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item(key = "sites-more", span = { GridItemSpan(maxLineSpan) }) {
            TvButton(if (expanded) "Ver menos" else "Ver mais · ${BrowserSites.all.size - sites.size} sites", { expanded = !expanded })
        }
        item(span = { GridItemSpan(maxLineSpan) }) { FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton("Google", { onOpen("https://www.google.com/") })
            TvButton("Favoritos", { onSheet("bookmarks") })
            TvButton("Histórico", { onSheet("history") })
            TvButton("Downloads", { onSheet("downloads") })
        } }
    }
}

@Composable internal fun BrowserSiteSearch(query: String, retry: Int, onOpen: (String) -> Unit, onFocused: () -> Unit) {
    val model: BrowserSiteSearchModel = viewModel()
    var selected by rememberSaveable { mutableStateOf(BrowserSites.all.first().id) }
    var lastFocus by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(query, retry) { model.search(query, retry) }
    @Composable fun restore(key: String): Modifier {
        val requester = remember { FocusRequester() }
        LaunchedEffect(Unit) { if (lastFocus == key) { withFrameNanos { }; requester.requestFocus() } }
        return Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused) { lastFocus = key; onFocused() } }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nos sites", color = Color.White, fontSize = 22.sp)
        LazyRow(contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(BrowserSites.all, key = { it.id }) { site ->
                val result = model.results[site.id]
                Card(onClick = { if (query.isBlank()) onOpen(site.home) else selected = site.id },
                    modifier = Modifier.width(160.dp).focusRaised().then(restore("site:${site.id}")),
                    scale = CardDefaults.scale(focusedScale = 1.03f),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusAccent))),
                    colors = CardDefaults.colors(containerColor = TvColors.Panel, focusedContainerColor = TvColors.Panel)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrowserSiteIcon(site, compact = true)
                        Column {
                            Text((if (query.isNotBlank() && selected == site.id) "● " else "") + site.name, color = Color.White, fontSize = 14.sp, maxLines = 2)
                            if (query.isNotBlank()) Text(when { result == null || result.loading -> "Buscando…"; result.error != null -> "Abrir no navegador"; else -> "${result.hits.size} resultados" }, color = TvColors.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (query.isNotBlank()) {
            val site = BrowserSites.all.first { it.id == selected }
            val result = model.results[selected]
            Text(site.name + " · " + when {
                result == null || result.loading -> "Buscando…"
                result.error != null -> result.error
                result.hits.isEmpty() -> "Nenhum resultado nesta primeira página."
                else -> "Resultados da web; disponibilidade do arquivo deve ser conferida no site."
            }, color = TvColors.Muted, fontSize = 14.sp)
            if (result?.hits?.isNotEmpty() == true) LazyRow(contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                items(result.hits, key = { it.url }) { hit ->
                    Card(onClick = { onOpen(hit.url) }, modifier = Modifier.width(180.dp).focusRaised().then(restore(hit.url)),
                        scale = CardDefaults.scale(focusedScale = 1.03f),
                        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.FocusAccent))),
                        colors = CardDefaults.colors(containerColor = TvColors.Panel, focusedContainerColor = TvColors.Panel)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hit.cover != null) AsyncImage(hit.cover, hit.title, Modifier.fillMaxWidth().height(145.dp), contentScale = ContentScale.Fit)
                            Text(hit.title, color = Color.White, fontSize = 15.sp, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            TvButton("Pesquisar ‘${query.take(30)}’ em ${site.name}", { onOpen(site.searchUrl(query)) }, restore("open:$selected"))
        }
    }
}

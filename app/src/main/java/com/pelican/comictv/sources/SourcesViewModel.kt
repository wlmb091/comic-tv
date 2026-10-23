package com.pelican.comictv.sources

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelican.comictv.mihon.MihonEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class SourcesViewModel(private val preferences: SourcePreferences, private val engine: MihonEngine,
    private val installer: ExtensionInstaller, private val online: OnlineReaderRepository,
    private val client: MihonRepoClient = MihonRepoClient()) : ViewModel() {
    private val _state = MutableStateFlow(SourcesState(repositories = preferences.repositories()))
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var page = 1

    init { refreshInstalled() }
    fun installationMessage(message: String) { _state.update { it.copy(message = message) } }

    private fun task(block: suspend () -> Unit) {
        job?.cancel()
        _state.update { it.copy(busy = true, message = null) }
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("ComicTV-Sources", "Source operation failed", e); _state.update { it.copy(message = e.message?.take(350) ?: "Não foi possível consultar a fonte.") } }
            catch (e: LinkageError) { Log.e("ComicTV-Sources", "Extension compatibility failure", e); _state.update { it.copy(message = "Esta extensão exige uma API ainda não compatível. ${e.javaClass.simpleName}") } }
            finally { if (currentCoroutineContext().isActive) _state.update { it.copy(busy = false) } }
        }
    }

    fun addRepository(url: String) = task {
        val fetched = withContext(Dispatchers.IO) { runInterruptible { client.fetch(url) } }
        require(fetched.fingerprint.matches(Regex("[a-f0-9]{64}"))) { "Use o repo.json com a chave de assinatura do repositório." }
        val old = _state.value.repositories.firstOrNull { it.baseUrl == fetched.baseUrl }
        require(old == null || old.fingerprint == fetched.fingerprint) { "A chave deste repositório mudou. Remova e cadastre novamente após verificar a origem." }
        val records = (_state.value.repositories.filterNot { it.baseUrl == fetched.baseUrl } + SavedRepository(fetched.baseUrl, fetched.name, fetched.fingerprint))
        withContext(Dispatchers.IO) { preferences.save(records) }
        _state.update { it.copy(repositories = records, selectedRepository = fetched.baseUrl, extensions = fetched.extensions,
            activeSource = null, details = null, message = "${fetched.extensions.size} extensões disponíveis.") }
    }

    fun selectRepository(base: String) = task {
        val trusted = _state.value.repositories.first { it.baseUrl == base }
        val fetched = withContext(Dispatchers.IO) { runInterruptible { client.fetch(base) } }
        require(fetched.fingerprint == trusted.fingerprint) { "A assinatura do repositório mudou; atualização bloqueada." }
        _state.update { it.copy(selectedRepository = base, extensions = fetched.extensions, activeSource = null, details = null) }
    }
    fun refresh() { _state.value.selectedRepository?.let(::selectRepository) ?: refreshInstalled() }
    fun removeRepository(base: String) = task {
        val next = _state.value.repositories.filterNot { it.baseUrl == base }
        withContext(Dispatchers.IO) { preferences.save(next) }
        _state.update { it.copy(repositories = next, selectedRepository = null, extensions = emptyList()) }
        refreshInstalledInternal()
    }
    fun refreshInstalled() = task { refreshInstalledInternal() }
    private suspend fun refreshInstalledInternal() {
        val records = preferences.repositories()
        val installed = withContext(Dispatchers.IO) { engine.installed() }
        val sources = ArrayList<SourceHandle>()
        val failures = linkedMapOf<String, String>()
        for (extension in installed.take(100)) {
            currentCoroutineContext().ensureActive()
            val pin = records.firstOrNull { it.fingerprint in extension.fingerprints }?.fingerprint ?: continue
            try { sources += withContext(Dispatchers.IO) { engine.load(extension.packageName, pin) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failures[extension.packageName] = extensionLoadDiagnostic(e); Log.e("ComicTV-Sources", "Extension load failed: ${extension.packageName}", e) }
            catch (e: LinkageError) { failures[extension.packageName] = extensionLoadDiagnostic(e); Log.e("ComicTV-Sources", "Extension linkage failed: ${extension.packageName}", e) }
        }
        _state.update { it.copy(repositories = records, installed = installed, loadFailures = failures,
            sources = sources.sortedWith(compareBy<SourceHandle> { !it.language.startsWith("pt") }.thenBy { it.name }),
            message = when (failures.size) { 0 -> "${sources.size} fontes disponíveis."; 1 -> "Uma extensão falhou ao abrir. Veja o motivo em Extensões no aparelho, abaixo."; else -> "${failures.size} extensões falharam ao abrir. Veja os motivos abaixo." }) }
    }

    fun prepareInstall(entry: ExtensionEntry, onReady: (File) -> Unit) = task {
        val repo = _state.value.repositories.firstOrNull { it.baseUrl == _state.value.selectedRepository } ?: error("Abra o repositório da extensão.")
        require(entry in _state.value.extensions) { "Extensão não pertence ao índice aberto." }
        val file = installer.prepare(entry, repo.fingerprint)
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(message = "APK verificado. Confirme a instalação na TV e atualize as instaladas ao voltar.") }
        onReady(file)
    }
    fun activateSource(source: SourceHandle) = task {
        require(source in _state.value.sources)
        val result = engine.catalog(source)
        page = 1
        _state.update { it.copy(activeSource = source, selectedRepository = null, details = null, titles = result.titles,
            hasNextPage = result.hasNextPage, latest = false, query = "") }
    }
    fun search(query: String) = catalog(query.trim().take(200), _state.value.latest)
    fun showCatalog(latest: Boolean) = catalog("", latest)
    private fun catalog(query: String, latest: Boolean) = task {
        val source = _state.value.activeSource ?: error("Escolha uma fonte.")
        val result = engine.catalog(source, query, latest)
        page = 1
        _state.update { it.copy(titles = result.titles, details = null, query = query, latest = latest, hasNextPage = result.hasNextPage) }
    }
    fun loadMore() = task {
        val old = _state.value
        val source = old.activeSource ?: error("Escolha uma fonte.")
        if (!old.hasNextPage) return@task
        val result = engine.catalog(source, old.query, old.latest, page + 1)
        page++
        _state.update { it.copy(titles = (it.titles + result.titles).distinctBy { title -> title.key }, hasNextPage = result.hasNextPage) }
    }
    fun showDetails(title: OnlineTitle) = task {
        val source = _state.value.activeSource ?: error("Escolha uma fonte.")
        val details = engine.details(source, title)
        _state.update { it.copy(details = details) }
    }
    fun openChapter(chapter: OnlineChapter, onRead: (String) -> Unit) = task {
        val old = _state.value
        val source = old.activeSource ?: error("Escolha uma fonte.")
        val title = old.details?.title ?: error("Abra um título.")
        val info = old.installed.firstOrNull { it.packageName == source.packageName } ?: error("A extensão não está instalada.")
        val pin = old.repositories.firstOrNull { it.fingerprint in info.fingerprints }?.fingerprint ?: error("O repositório da extensão não é confiável.")
        val id = online.open(source, title, chapter, pin)
        currentCoroutineContext().ensureActive()
        onRead(id)
    }
    fun back(): Boolean {
        val old = _state.value
        if (old.details == null && old.activeSource == null && old.selectedRepository == null) return false
        job?.cancel()
        _state.value = when {
            old.details != null -> old.copy(details = null, busy = false, message = null)
            old.activeSource != null -> old.copy(activeSource = null, titles = emptyList(), busy = false, message = null)
            else -> old.copy(selectedRepository = null, extensions = emptyList(), busy = false, message = null)
        }
        return true
    }
}

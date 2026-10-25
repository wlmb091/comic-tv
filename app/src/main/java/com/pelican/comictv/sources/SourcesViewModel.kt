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
    private var catalogJob: Job? = null
    private var initialLoading = true
    private var desiredLatest = false
    private val entryGate = SourceEntryGate()
    private var page = 1
    private val previews = TitlePreviewResolver(engine::titlePreview)
    private val extensionQueue = ExtensionDownloadQueue(viewModelScope) { candidate, progress ->
        requireExtensionTrust(candidate)
        val file = installer.prepare(candidate.entry,candidate.repository.fingerprint,progress)
        try { currentCoroutineContext().ensureActive(); requireExtensionTrust(candidate); file }
        catch (e: Exception) { file.delete(); throw e }
    }
    val extensionDownloads = extensionQueue.state
    private fun requireExtensionTrust(candidate: AvailableExtension) {
        require(preferences.repositories().any {it.baseUrl == candidate.repository.baseUrl && it.fingerprint == candidate.repository.fingerprint}) {
            "O repositório da extensão não é mais confiável. Atualize a lista."
        }
    }
    fun extensionDownloadsVisible(owner: Any, value: Boolean) = extensionQueue.setVisible(owner,value)
    fun clearExtensionDownloads() = extensionQueue.clear()
    fun extensionInstallationReturned(file: File?, remove: Boolean) { if (file != null) extensionQueue.installationReturned(file,remove) }
    fun toggleExtensionDownload(candidate: AvailableExtension, install: (File) -> Unit) {
        val current = extensionDownloads.value.firstOrNull {it.candidate.entry.packageName == candidate.entry.packageName}
        if (current?.status == ExtensionTransferStatus.Queued || current?.status == ExtensionTransferStatus.Downloading) {
            extensionQueue.toggle(candidate); return
        }
        if (current?.status == ExtensionTransferStatus.Ready && current.candidate == candidate) {
            extensionQueue.requestInstall(candidate, { file ->
                    requireExtensionTrust(candidate)
                    installer.revalidate(file,candidate.entry,candidate.repository.fingerprint)
                    currentCoroutineContext().ensureActive(); requireExtensionTrust(candidate)
            },install,::installationMessage)
        } else try {
            val state=_state.value
            require(candidate in state.availableExtensions || (candidate.repository.baseUrl == state.selectedRepository && candidate.entry in state.extensions)) {
                "Atualize o índice do repositório antes de baixar."
            }
            requireExtensionTrust(candidate); extensionQueue.toggle(candidate)
        } catch (e: Exception) { installationMessage(e.message ?: "Não foi possível adicionar à fila.") }
    }
    fun toggleRepositoryExtension(entry: ExtensionEntry, install: (File) -> Unit) {
        val repo=_state.value.repositories.firstOrNull {it.baseUrl == _state.value.selectedRepository} ?: return
        toggleExtensionDownload(AvailableExtension(repo,entry),install)
    }
    override fun onCleared() { extensionQueue.close(); super.onCleared() }

    init { task { try { refreshInstalledInternal() } finally { initialLoading = false } } }
    fun installationMessage(message: String) { _state.update { it.copy(message = message) } }

    private fun task(block: suspend () -> Unit) {
        job?.cancel()
        _state.update { it.copy(busy = true, message = null) }
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("ComicTV-Sources", "Source operation failed", e); _state.update { it.copy(message = e.message?.take(350) ?: "Não foi possível consultar a fonte.") } }
            catch (e: LinkageError) { Log.e("ComicTV-Sources", "Extension compatibility failure", e); _state.update { it.copy(message = "Falha de compatibilidade da extensão: ${extensionLoadDiagnostic(e)}") } }
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
        _state.update { it.copy(repositories = records, extensionCatalogLoaded = false, availableExtensions = emptyList(), selectedRepository = fetched.baseUrl, extensions = fetched.extensions,
            managerPage = SourceManagerPage.REPOSITORIES, details = null, message = "${fetched.extensions.size} extensões disponíveis.") }
    }

    fun selectRepository(base: String) = task {
        val trusted = _state.value.repositories.first { it.baseUrl == base }
        val fetched = withContext(Dispatchers.IO) { runInterruptible { client.fetch(base) } }
        require(fetched.fingerprint == trusted.fingerprint) { "A assinatura do repositório mudou; atualização bloqueada." }
        _state.update { it.copy(selectedRepository = base, extensions = fetched.extensions, managerPage = SourceManagerPage.REPOSITORIES, details = null) }
    }
    fun refresh() { _state.value.selectedRepository?.let(::selectRepository) ?: refreshInstalled() }
    fun removeRepository(base: String) = task {
        val next = _state.value.repositories.filterNot { it.baseUrl == base }
        withContext(Dispatchers.IO) { preferences.save(next) }
        _state.update { it.copy(repositories = next, extensionCatalogLoaded = false, availableExtensions = emptyList(), selectedRepository = null, extensions = emptyList()) }
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
            message = when (failures.size) { 0 -> "${sources.size} fontes disponíveis."; 1 -> "Uma extensão falhou ao abrir. Veja o motivo em Gerenciar / Extensões."; else -> "${failures.size} extensões falharam ao abrir. Veja os motivos em Gerenciar / Extensões." }) }
        val selection = _state.value.activeSource?.let { SourceSelection(it.packageName, it.id) } ?: preferences.selectedSource()
        val restored = selection?.resolve(sources)
        if (restored != null && _state.value.activeSource == null && _state.value.managerPage == null) {
            activateSourceInternal(restored, restoring = true)
        } else if (restored != null && _state.value.activeSource == null) {
            _state.update { it.copy(activeSource = restored) }
        } else if (restored != null && _state.value.activeSource != null) {
            _state.update { it.copy(activeSource = restored) }
        } else if (selection != null && restored == null) {
            withContext(Dispatchers.IO) { preferences.saveSelectedSource(null) }
            _state.update { it.copy(activeSource = null, details = null, titles = emptyList(), catalogLoaded = false, managerPage = SourceManagerPage.SOURCES,
                message = "A fonte anterior não está disponível. Escolha outra fonte.") }
        }
    }

    fun openSourceManager(page: SourceManagerPage = SourceManagerPage.SOURCES) {
        if (!initialLoading) job?.cancel()
        _state.update { it.copy(managerPage = page, details = null, selectedRepository = null,
            busy = initialLoading && it.busy) }
    }
    fun enterMainSection(entryId: String, route: String) {
        if (!entryGate.enter(entryId)) return
        when (route) {
            "extensions" -> openExtensions()
            "recent" -> navigateCatalog(true)
            else -> navigateCatalog(false)
        }
    }
    fun navigateCatalog(latest: Boolean) {
        desiredLatest = latest
        val old = _state.value
        _state.update { it.copy(managerPage = null, details = null, selectedRepository = null) }
        if (initialLoading) return
        if (old.activeSource != null && (job?.isActive == true || old.latest != latest || old.query.isNotEmpty() || old.titles.isEmpty())) catalog("", latest)
        else { job?.cancel(); _state.update { it.copy(busy = false) } }
    }

    fun openExtensions() {
        openSourceManager(SourceManagerPage.EXTENSIONS)
        if (!_state.value.extensionCatalogLoaded && catalogJob?.isActive != true) refreshExtensionCatalogs()
    }

    fun refreshExtensionCatalogs() {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            val repositories = _state.value.repositories.take(32)
            val available = mutableListOf<AvailableExtension>()
            val failures = mutableListOf<String>()
            for (repo in repositories) {
                try {
                    val fetched = withContext(Dispatchers.IO) { runInterruptible { client.fetch(repo.baseUrl) } }
                    require(fetched.fingerprint == repo.fingerprint) { "A assinatura do repositório mudou; atualização bloqueada." }
                    available += fetched.extensions.map { AvailableExtension(repo, it) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failures += "${repo.name}: ${e.message?.take(120)}" }
            }
            currentCoroutineContext().ensureActive()
            _state.update { old ->
                if (old.repositories != repositories) old.copy(extensionCatalogLoaded = false)
                else old.copy(availableExtensions = available, extensionCatalogLoaded = true,
                    message = failures.takeIf { it.isNotEmpty() }?.joinToString("\n")?.take(600))
            }
        }
    }

    fun prepareAvailableInstall(candidate: AvailableExtension, onReady: (File) -> Unit) = task {
        require(candidate.isCurrentTrustedCandidate(_state.value.availableExtensions, preferences.repositories())) { "Atualize a lista de extensões e verifique a confiança do repositório." }
        val file = installer.prepare(candidate.entry, candidate.repository.fingerprint)
        currentCoroutineContext().ensureActive()
        require(candidate.isCurrentTrustedCandidate(_state.value.availableExtensions, preferences.repositories())) { "O repositório da extensão não é mais confiável." }
        _state.update { it.copy(message = "APK verificado. Confirme a instalação na TV e atualize as instaladas ao voltar.") }
        onReady(file)
    }
    fun prepareInstall(entry: ExtensionEntry, onReady: (File) -> Unit) = task {
        val repo = _state.value.repositories.firstOrNull { it.baseUrl == _state.value.selectedRepository } ?: error("Abra o repositório da extensão.")
        require(entry in _state.value.extensions) { "Extensão não pertence ao índice aberto." }
        val file = installer.prepare(entry, repo.fingerprint)
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(message = "APK verificado. Confirme a instalação na TV e atualize as instaladas ao voltar.") }
        onReady(file)
    }
    suspend fun resolveTitlePreview(title: OnlineTitle) {
        val source = _state.value.activeSource ?: return
        try {
            val preview = previews.resolve(source, title)
            currentCoroutineContext().ensureActive()
            _state.update { old ->
                if (old.activeSource != source) old else old.copy(titles = old.titles.map { if (it.key == title.key) preview else it })
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w("ComicTV-Sources", "Cover details unavailable: ${source.packageName}", e) }
        catch (e: LinkageError) { Log.w("ComicTV-Sources", "Cover details compatibility failure: ${source.packageName}", e) }
    }
    fun activateSource(source: SourceHandle) = task { activateSourceInternal(source) }
    private suspend fun activateSourceInternal(source: SourceHandle, restoring: Boolean = false) {
        require(source in _state.value.sources)
        withContext(Dispatchers.IO) { preferences.saveSelectedSource(source) }
        currentCoroutineContext().ensureActive()
        if (restoring && _state.value.activeSource != null) return
        if (restoring && _state.value.managerPage != null) {
            // Keep management on screen while remembering the source for later catalog navigation.
            _state.update { it.copy(activeSource = source) }
            return
        }
        _state.update { it.copy(activeSource = source, managerPage = null, selectedRepository = null, details = null, titles = emptyList(), catalogLoaded = false, hasNextPage = false, query = "", latest = desiredLatest) }
        var requestedLatest = desiredLatest
        var result = engine.catalog(source, latest = requestedLatest)
        while (requestedLatest != desiredLatest) {
            requestedLatest = desiredLatest
            result = engine.catalog(source, latest = requestedLatest)
        }
        currentCoroutineContext().ensureActive()
        page = 1
        _state.update { it.copy(activeSource = source, selectedRepository = null, details = null, titles = result.titles, catalogLoaded = true,
            hasNextPage = result.hasNextPage, latest = requestedLatest, query = "") }
    }
    fun search(query: String) = catalog(query.trim().take(200), _state.value.latest)
    fun showCatalog(latest: Boolean) { desiredLatest = latest; catalog("", latest) }
    private fun catalog(query: String, latest: Boolean) = task {
        val source = _state.value.activeSource ?: error("Escolha uma fonte.")
        val result = engine.catalog(source, query, latest)
        currentCoroutineContext().ensureActive()
        page = 1
        _state.update { it.copy(titles = result.titles, catalogLoaded = true, details = null, query = query, latest = latest, hasNextPage = result.hasNextPage) }
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
        if (old.details == null && old.managerPage == null && old.selectedRepository == null) return false
        job?.cancel()
        _state.value = when {
            old.details != null -> old.copy(details = null, busy = false, message = null)
            old.selectedRepository != null -> old.copy(selectedRepository = null, extensions = emptyList(), busy = false, message = null)
            old.activeSource != null -> old.copy(managerPage = null, busy = false, message = null)
            else -> return false
        }
        if (old.details == null && old.selectedRepository == null && old.activeSource != null && !old.catalogLoaded) navigateCatalog(desiredLatest)
        return true
    }
}

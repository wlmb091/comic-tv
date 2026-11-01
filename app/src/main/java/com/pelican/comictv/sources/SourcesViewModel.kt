package com.pelican.comictv.sources

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pelican.comictv.mihon.MihonEngine
import com.pelican.comictv.search.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import com.pelican.comictv.performance.AppTimings
import com.pelican.comictv.performance.LoadStage

class SourcesViewModel(private val preferences: SourcePreferences, private val engine: MihonEngine,
    private val installer: ExtensionInstaller, private val online: OnlineReaderRepository,
    private val searchCoverDirectory: File,
    private val client: MihonRepoClient = MihonRepoClient()) : ViewModel() {
    private val _state = MutableStateFlow(SourcesState(repositories = preferences.repositories()))
    val state = _state.asStateFlow()
    private val _homePreviews = MutableStateFlow(HomeSourcePreviews())
    val homePreviews = _homePreviews.asStateFlow()

    /** Explicit source membership and pinned trust checks also apply to background discovery. */
    private fun requireSearchSource(source: SourceHandle) {
        require(source in state.value.sources) { "A fonte não está mais disponível." }
        val installed = state.value.installed.firstOrNull { it.packageName == source.packageName && it.compatible }
        require(installed != null && preferences.repositories().any { it.fingerprint in installed.fingerprints }) {
            "A extensão não pertence a um repositório confiável."
        }
    }
    fun globalSearchSources(): List<SourceHandle> = state.value.sources.filter { source ->
        runCatching { requireSearchSource(source) }.isSuccess
    }
    suspend fun globalSearchPage(source: SourceHandle, query: String, latest: Boolean): List<OnlineTitle> {
        requireSearchSource(source)
        val result = fetchCatalog(CatalogSearchQuery(source, query.take(300), latest))
        currentCoroutineContext().ensureActive(); requireSearchSource(source)
        return result.titles.take(12)
    }
    private val searchCovers = SearchCoverLoader(
        resolve = { source, title ->
            requireSearchSource(source)
            engine.titlePreview(source, title).coverUrl.also { currentCoroutineContext().ensureActive(); requireSearchSource(source) }
        },
        cached = { key ->
            withContext(Dispatchers.IO) {
                File(searchCoverDirectory, "$key.img").takeIf { it.isFile && it.length() > 0 }
                    ?.also { it.setLastModified(System.currentTimeMillis()) }
            }
        },
        fetch = { source, key, url -> withContext(Dispatchers.IO) {
            requireSearchSource(source)
            searchCoverDirectory.mkdirs()
            check(searchCoverDirectory.usableSpace > 64L * 1024 * 1024) { "Espaço insuficiente para capas." }
            val target = File(searchCoverDirectory, "$key.img")
            val temporary = File.createTempFile("cover-", ".part", searchCoverDirectory)
            try {
                engine.downloadCover(source, url, temporary, 4L * 1024 * 1024)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temporary.path, bounds)
                check(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 48_000_000)
                currentCoroutineContext().ensureActive(); requireSearchSource(source)
                check(temporary.renameTo(target))
                synchronized(searchCoverDirectory) {
                    var total = searchCoverDirectory.listFiles()?.sumOf { it.length() } ?: 0L
                    searchCoverDirectory.listFiles()?.filter { it.extension == "img" && it != target }
                        ?.sortedBy { it.lastModified() }?.forEach {
                            if (total > 48L * 1024 * 1024) { val bytes = it.length(); if (it.delete()) total -= bytes }
                        }
                }
                target
            } finally { temporary.delete() }
        } },
    )
    internal suspend fun globalSearchCover(hit: SearchHit, attempt: Long): SearchCoverResult {
        requireSearchSource(hit.source)
        // Reuse already validated 1.4.1 cache entries; do not download them again after updating.
        withContext(Dispatchers.IO) {
            val target = File(searchCoverDirectory, "${searchCoverKey(hit.source, hit.title.key)}.img")
            if (!target.exists()) {
                val oldIdentity = "${hit.source.packageName}:${hit.source.id}:${hit.title.key}:${hit.title.coverUrl.orEmpty()}"
                val oldKey = java.security.MessageDigest.getInstance("SHA-256").digest(oldIdentity.toByteArray()).joinToString("") { "%02x".format(it) }
                val old = File(searchCoverDirectory, "$oldKey.img")
                if (old.isFile && old.length() > 0) old.renameTo(target)
            }
        }
        val started = System.nanoTime()
        val result = searchCovers.load(hit.source, hit.title, attempt)
        currentCoroutineContext().ensureActive(); requireSearchSource(hit.source)
        if (result is SearchCoverResult.Failed) Log.d("ComicTV-Covers", "${hit.source.packageName}: ${result.reason}, ${(System.nanoTime() - started) / 1_000_000}ms")
        return result
    }

    suspend fun globalSearchLibraryLinks(ids: List<String>) = online.globalSearchLibraryLinks(ids)

    /** A result click is an explicit source choice; discovery itself never saves a selection. */
    fun showGlobalSearchDetails(source: SourceHandle, title: OnlineTitle) = task {
        requireSearchSource(source)
        withContext(Dispatchers.IO) { preferences.saveSelectedSource(source) }
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(activeSource = source, managerPage = null, selectedRepository = null,
            details = null, titles = emptyList(), catalogLoaded = false) }
        val details = sourceRequest(SourceRequest.Details) { engine.details(source, title) }
        searchCovers.remember(source, listOf(details.title))
        currentCoroutineContext().ensureActive(); requireSearchSource(source)
        _state.update { it.copy(details = details) }
    }
    fun leaveGlobalSearchDetails() { job?.cancel(); _state.update { it.copy(details = null, busy = false) } }
    fun pauseGlobalSearchDetails() { job?.cancel(); _state.update { it.copy(busy = false) } }

    /** Independent first-page samples: never changes catalog mode, pagination, selection or focus. */
    suspend fun collectHomePreviews() {
        state.map { Triple(it.activeSource, it.sourceRevision, it.sourcesRefreshing || it.busy) }.distinctUntilChanged().collectLatest { (source, generation, blocked) ->
            if (_homePreviews.value.source != source || state.value.sourcesRefreshing) _homePreviews.value = HomeSourcePreviews(source = source)
            if (source == null || blocked) return@collectLatest
            for (latest in listOf(false, true)) {
                val query = CatalogSearchQuery(source, "", latest)
                try {
                    val cached = snapshots.get(query)
                    cached?.snapshot?.titles?.let { titles ->
                        val sample = titles.distinctBy { it.key }.take(com.pelican.comictv.library.HOME_SOURCE_POOL_LIMIT)
                        _homePreviews.update { if (latest) it.copy(latest = sample) else it.copy(catalog = sample) }
                    }
                    val titles = if (cached?.fresh == true) cached.snapshot.titles else {
                        val result = fetchCatalog(query)
                        currentCoroutineContext().ensureActive()
                        if (sourceGeneration != generation || state.value.activeSource != source) return@collectLatest
                        snapshots.put(query, result)
                        result.titles
                    }
                    currentCoroutineContext().ensureActive()
                    if (sourceGeneration != generation || state.value.activeSource != source) return@collectLatest
                    val sample = titles.distinctBy { it.key }.take(com.pelican.comictv.library.HOME_SOURCE_POOL_LIMIT)
                    _homePreviews.update { if (latest) it.copy(latest = sample) else it.copy(catalog = sample) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w("ComicTV-Sources", "Home preview unavailable", error) }
                catch (error: LinkageError) { Log.w("ComicTV-Sources", "Home preview incompatible", error) }
            }
        }
    }

    private var job: Job? = null
    private var catalogJob: Job? = null
    private var initialLoading = true
    private val catalogIntent = CatalogSearchIntent()
    private var desiredLatest: Boolean
        get() = catalogIntent.latest
        set(value) { catalogIntent.latest = value }
    private val entryGate = SourceEntryGate()
    private var page = 1
    private var sourceGeneration = 0L
    private val snapshots = CatalogSnapshots()
    private suspend fun fetchCatalog(query: CatalogSearchQuery, page: Int = 1) =
        AppTimings.measure(LoadStage.Catalog) { sourceRequest(SourceRequest.Catalog) { engine.catalog(query.source, query.text, query.latest, page) } }.also { searchCovers.remember(query.source, it.titles) }
    private val previews = TitlePreviewResolver { source, title ->
        AppTimings.measure(LoadStage.CoverMetadata) { sourceRequest(SourceRequest.Preview) { engine.titlePreview(source, title) } }
    }
    private val liveSearch = LatestSearch<CatalogSearchQuery, CatalogResult>(viewModelScope,
        fetch = { fetchCatalog(it) },
        publish = { query, result ->
            page = 1
            snapshots.put(query, result)
            _state.update { old -> if (old.activeSource != query.source) old else old.copy(
                titles = result.titles, catalogLoaded = true, details = null, query = query.text,
                latest = query.latest, hasNextPage = result.hasNextPage, busy = false) }
        },
        failed = { error ->
            Log.w("ComicTV-Sources", "Search unavailable", error)
            _state.update { it.copy(busy = false, message = error.message?.take(350) ?: "Não foi possível buscar nesta fonte.") }
        })
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
        liveSearch.cancel()
        job?.cancel()
        _state.update { it.copy(busy = true, message = null) }
        job = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); Log.e("ComicTV-Sources", "Source operation failed", e); _state.update { it.copy(message = e.message?.take(350) ?: "Não foi possível consultar a fonte.") } }
            catch (e: LinkageError) { currentCoroutineContext().ensureActive(); Log.e("ComicTV-Sources", "Extension compatibility failure", e); _state.update { it.copy(message = "Falha de compatibilidade da extensão: ${extensionLoadDiagnostic(e)}") } }
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
        val generation = ++sourceGeneration
        _state.update { it.copy(sourceRevision = generation, sourcesRefreshing = true) }
        try {
            snapshots.clear() // A package/version/trust refresh invalidates every retained catalog.
            previews.clear()
            searchCovers.clear()
            val records = preferences.repositories()
            val installed = withContext(Dispatchers.IO) { engine.installed() }
            val sources = ArrayList<SourceHandle>()
            val failures = linkedMapOf<String, String>()
            for (extension in installed.take(100)) {
                currentCoroutineContext().ensureActive()
                val pin = records.firstOrNull { it.fingerprint in extension.fingerprints }?.fingerprint ?: continue
                try { sources += withContext(Dispatchers.IO) { engine.reload(extension.packageName, pin) } }
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
        } finally {
            if (sourceGeneration == generation) _state.update { it.copy(sourcesRefreshing = false) }
        }
    }

    fun openSourceManager(page: SourceManagerPage = SourceManagerPage.SOURCES) {
        liveSearch.cancel()
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
        liveSearch.cancel()
        desiredLatest = latest
        val old = _state.value
        _state.update { it.copy(managerPage = null, details = null, selectedRepository = null, latest = latest, query = "") }
        if (initialLoading) return
        val request = catalogQueryOnEntry(old, latest, initialLoading)
        if (request != null) catalog(request.text, request.latest)
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
        if (_state.value.sourcesRefreshing) return
        val source = _state.value.activeSource ?: return
        val generation = sourceGeneration
        try {
            val preview = previews.resolve(source, title)
            searchCovers.remember(source, listOf(preview))
            currentCoroutineContext().ensureActive()
            _state.update { old ->
                if (generation != sourceGeneration || old.activeSource != source || old.titles.none { it.key == title.key && it.coverUrl.isNullOrBlank() }) old else {
                    snapshots.updateTitle(CatalogSearchQuery(source, old.query, old.latest), preview)
                    old.copy(titles = old.titles.map { if (it.key == title.key && it.coverUrl.isNullOrBlank()) preview else it })
                }
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
        var result = initialCatalog(source, requestedLatest)
        while (requestedLatest != desiredLatest) {
            requestedLatest = desiredLatest
            result = initialCatalog(source, requestedLatest)
        }
        currentCoroutineContext().ensureActive()
        page = result.page
        _state.update { it.copy(activeSource = source, selectedRepository = null, details = null, titles = result.titles, catalogLoaded = true,
            hasNextPage = result.hasNextPage, latest = requestedLatest, query = "") }
    }
    private suspend fun initialCatalog(source: SourceHandle, latest: Boolean): CatalogSnapshot {
        val query = CatalogSearchQuery(source, "", latest)
        val cached = snapshots.get(query)
        if (cached?.fresh == true) { AppTimings.hit(LoadStage.CatalogMemory); return cached.snapshot }
        cached?.snapshot?.let { snapshot ->
            page = snapshot.page
            _state.update { it.copy(titles = snapshot.titles, hasNextPage = snapshot.hasNextPage, catalogLoaded = true) }
        }
        val result = fetchCatalog(query)
        currentCoroutineContext().ensureActive()
        snapshots.put(query, result)
        return CatalogSnapshot(result.titles, result.hasNextPage, 1, 0)
    }
    fun search(query: String, owner: Any? = null) = submitSearch(query, immediate = true, owner = owner)
    fun searchWhileTyping(query: String, owner: Any) = submitSearch(query, immediate = false, owner = owner)
    private fun submitSearch(query: String, immediate: Boolean, owner: Any?) {
        val source = _state.value.activeSource ?: return
        job?.cancel()
        _state.update { it.copy(busy = true, message = null) }
        liveSearch.submit(catalogIntent.capture(source, query), immediate, owner)
    }
    fun stopLiveSearch(owner: Any) {
        if (liveSearch.cancelFor(owner)) _state.update { it.copy(busy = false) }
    }
    fun showCatalog(latest: Boolean) { desiredLatest = latest; catalog("", latest) }
    private fun catalog(query: String, latest: Boolean) {
        val source = _state.value.activeSource ?: error("Escolha uma fonte.")
        val request = CatalogSearchQuery(source, query, latest)
        val cached = snapshots.get(request)
        if (cached?.fresh == true) {
            liveSearch.cancel(); job?.cancel()
            AppTimings.hit(LoadStage.CatalogMemory)
            page = cached.snapshot.page
            _state.update { it.copy(titles = cached.snapshot.titles, catalogLoaded = true, details = null, query = query,
                latest = latest, hasNextPage = cached.snapshot.hasNextPage, busy = false, message = null) }
            return
        }
        task {
            val snapshot = cached?.snapshot
            page = snapshot?.page ?: 1
            _state.update { it.copy(titles = snapshot?.titles.orEmpty(), hasNextPage = snapshot?.hasNextPage ?: false,
                catalogLoaded = snapshot != null, details = null, query = query, latest = latest) }
            val result = fetchCatalog(request)
            currentCoroutineContext().ensureActive()
            snapshots.put(request, result)
            page = 1
            _state.update { it.copy(titles = result.titles, catalogLoaded = true, details = null, query = query, latest = latest, hasNextPage = result.hasNextPage) }
        }
    }
    fun loadMore() = task {
        val old = _state.value
        val source = old.activeSource ?: error("Escolha uma fonte.")
        if (!old.hasNextPage) return@task
        val request = CatalogSearchQuery(source, old.query, old.latest)
        val nextPage = page + 1
        val result = fetchCatalog(request, nextPage)
        currentCoroutineContext().ensureActive()
        val current = _state.value
        val merged = appendCatalogPage(current, request, result)
        if (merged === current) return@task
        page = nextPage
        snapshots.put(request, CatalogResult(merged.titles, merged.hasNextPage), page)
        _state.value = merged
    }
    fun showDetails(title: OnlineTitle) = task {
        val source = _state.value.activeSource ?: error("Escolha uma fonte.")
        val details = sourceRequest(SourceRequest.Details) { engine.details(source, title) }
        searchCovers.remember(source, listOf(details.title))
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(details = details) }
    }
    fun openChapter(chapter: OnlineChapter, onRead: (String) -> Unit) = task {
        val old = _state.value
        val source = old.activeSource ?: error("Escolha uma fonte.")
        val title = old.details?.title ?: error("Abra um título.")
        val info = old.installed.firstOrNull { it.packageName == source.packageName } ?: error("A extensão não está instalada.")
        val pin = old.repositories.firstOrNull { it.fingerprint in info.fingerprints }?.fingerprint ?: error("O repositório da extensão não é confiável.")
        val id = AppTimings.measure(LoadStage.OpenChapter) { online.open(source, title, chapter, pin) }
        currentCoroutineContext().ensureActive()
        onRead(id)
    }
    fun back(): Boolean {
        val old = _state.value
        if (old.details == null && old.managerPage == null && old.selectedRepository == null) return false
        liveSearch.cancel()
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

package com.pelican.comictv.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.pelican.comictv.archive.*
import com.pelican.comictv.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import com.pelican.comictv.storage.*
import com.pelican.comictv.performance.AppTimings
import com.pelican.comictv.performance.LoadStage
import com.pelican.comictv.cinema.validateCinemaArtwork

internal data class LibraryBackdropImage(val file: File, val width: Int, val height: Int)

class LibraryRepository(private val context: Context, private val dao: ComicDao,
    private val coverGenerator: CoverGenerator = BitmapCoverGenerator(),
    private val collectionDao: CollectionDao? = null,
    private val pendingRestores: com.pelican.comictv.backup.RestorePendingStore? = null) : ReaderRepository {
    private val sagaStore = SagaStore(context.filesDir)
    private val sagaMutex = Mutex()
    private val sagaMetadataCache = linkedMapOf<String, SagaMetadataCacheEntry>()
    private val sagaCoverDirectory = File(context.filesDir, "saga-covers")
    private val sagaCoverStore by lazy { com.pelican.comictv.cinema.CinemaArtworkStore(sagaCoverDirectory,
        ::validateSagaCover, maxFileBytes = 20L * 1024 * 1024, maxTotalBytes = 128L * 1024 * 1024) }

    private val automaticSagaDirectory = File(context.cacheDir, "automatic-saga-covers")
    private val automaticSagaStore by lazy { com.pelican.comictv.cinema.CinemaArtworkStore(automaticSagaDirectory,
        ::validateSagaCover, maxFileBytes = 8L * 1024 * 1024, maxTotalBytes = 64L * 1024 * 1024) }

    /** A user choice always wins, including a choice made while an automatic transfer is running. */
    internal suspend fun automaticSagaKey(id: String, identity: String): String? = sagaMutex.withLock {
        val settings = sagaStore.read()
        val key = settings.assignments[id] ?: identity
        if (settings.covers.containsKey(key) ||
            (sagaCoverDirectory.exists() && sagaCoverStore.file(sagaCoverId(key)) != null) ||
            (automaticSagaDirectory.exists() && automaticSagaStore.file(sagaCoverId(key)) != null)) null else key
    }

    internal suspend fun saveAutomaticSagaCover(id: String, identity: String, key: String, source: File) = withContext(Dispatchers.IO) {
        val active = currentCoroutineContext()
        source.inputStream().use { automaticSagaStore.stage(it) { active.ensureActive() } }.use { staged ->
            sagaMutex.withLock {
                val settings = sagaStore.read()
                if ((settings.assignments[id] ?: identity) == key && !settings.covers.containsKey(key) &&
                    (!sagaCoverDirectory.exists() || sagaCoverStore.file(sagaCoverId(key)) == null) &&
                    automaticSagaStore.file(sagaCoverId(key)) == null) {
                    automaticSagaStore.commit(sagaCoverId(key), staged) { active.ensureActive() }
                    _sagaRevision.value++
                }
            }
        }
    }

    internal suspend fun sagaMetadataChanged() = sagaMutex.withLock {
        sagaMetadataCache.clear()
        _sagaRevision.value++
    }

    private val _sagaRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val sagaRevision: kotlinx.coroutines.flow.StateFlow<Long> = _sagaRevision

    suspend fun sagaGroups(comics: List<ComicEntity>): List<SagaGroup> = withContext(Dispatchers.IO) {
        val metadata = sagaMutex.withLock { readSagaMetadata(File(context.filesDir, "online-records"), comics, sagaMetadataCache) }
        val settings = sagaMutex.withLock { sagaStore.read() }
        val names = observeCollections().first().associate { it.id to it.name }
        val groups = buildSagaGroups(comics, metadata, settings, names, ::cover)
        sagaMutex.withLock {
            groups.map { group ->
                val file = (if (sagaCoverDirectory.exists()) sagaCoverStore.file(sagaCoverId(group.key)) else null)
                    ?: (if (!settings.covers.containsKey(group.key) && automaticSagaDirectory.exists())
                        automaticSagaStore.file(sagaCoverId(group.key)) else null)
                if (file != null) group.copy(cover = file) else group
            }
        }
    }

    /** Assign only this edition explicitly; ordinary collection membership never implies a saga. */
    suspend fun assignSaga(id: String, collectionId: String?) = withContext(Dispatchers.IO) {
        require(dao.get(id) != null)
        if (collectionId != null) require(observeCollections().first().any { it.id == collectionId })
        sagaMutex.withLock {
            val settings = sagaStore.read()
            val assignments = settings.assignments.toMutableMap()
            if (collectionId == null) assignments.remove(id) else assignments[id] = "manual:$collectionId"
            sagaStore.write(settings.copy(assignments = assignments))
            _sagaRevision.value++
        }
    }

    suspend fun chooseSagaCover(group: SagaGroup, editionId: String?) = withContext(Dispatchers.IO) {
        require(editionId == null || group.editions.any { it.id == editionId })
        sagaMutex.withLock {
            val settings = sagaStore.read()
            val covers = settings.covers.toMutableMap()
            if (editionId == null) covers.remove(group.key) else covers[group.key] = editionId
            sagaStore.write(settings.copy(covers = covers))
            if (sagaCoverDirectory.exists()) sagaCoverStore.remove(sagaCoverId(group.key))
            _sagaRevision.value++
        }
    }

    /** Independent SAF image; never replaces an edition cover or original. */
    suspend fun importSagaCover(groupKey: String, uri: Uri): File = withContext(Dispatchers.IO) {
        require(groupKey.length in 1..200 && uri.scheme == "content") { "Escolha uma imagem pelo seletor de arquivos." }
        val active = currentCoroutineContext()
        val prepared = java.util.concurrent.atomic.AtomicReference<com.pelican.comictv.cinema.StagedArtwork?>()
        try {
            val staged = runInterruptible {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    sagaCoverStore.stage(input) { active.ensureActive() }.also(prepared::set)
                } ?: throw IOException("Não foi possível abrir a imagem escolhida.")
            }
            sagaMutex.withLock {
                active.ensureActive()
                val file = sagaCoverStore.commit(sagaCoverId(groupKey), staged) { active.ensureActive() }
                _sagaRevision.value++
                file
            }
        } finally { prepared.get()?.close() }
    }

    /** Copy a displayed original page into the manual artwork store, without touching reader files. */
    suspend fun saveSagaCoverFromPage(editionId: String, source: File): File = withContext(Dispatchers.IO) {
        require(dao.get(editionId) != null) { "Esta edição não está mais na biblioteca." }
        val canonical = source.canonicalFile
        require(listOf(context.filesDir, context.cacheDir).any { root ->
            canonical.toPath().startsWith(root.canonicalFile.toPath())
        } && canonical.isFile) { "A página não está disponível no armazenamento do aplicativo." }
        val group = sagaGroups(dao.observe().first()).firstOrNull { group -> group.editions.any { it.id == editionId } }
            ?: throw IOException("Não foi possível identificar a saga desta edição.")
        val assignment = sagaMutex.withLock { sagaStore.read().assignments[editionId] }
        require(if (group.key.startsWith("manual:")) assignment == group.key else assignment == null) {
            "A saga mudou. Tente salvar a capa novamente."
        }
        val active = currentCoroutineContext()
        val prepared = java.util.concurrent.atomic.AtomicReference<com.pelican.comictv.cinema.StagedArtwork?>()
        try {
            val staged = runInterruptible {
                canonical.inputStream().use { input ->
                    sagaCoverStore.stage(input) { active.ensureActive() }.also(prepared::set)
                }
            }
            val currentGroup = sagaGroups(dao.observe().first()).firstOrNull { row -> row.editions.any { it.id == editionId } }
            require(currentGroup?.key == group.key) { "A saga mudou. Tente salvar a capa novamente." }
            sagaMutex.withLock {
                active.ensureActive()
                require(sagaStore.read().assignments[editionId] == assignment) { "A saga mudou. Tente novamente." }
                val file = sagaCoverStore.commit(sagaCoverId(group.key), staged) { active.ensureActive() }
                _sagaRevision.value++
                file
            }
        } finally { prepared.get()?.close() }
    }

    private val archive = ComicArchive(pdfReader = PdfComicArchive())
    private val store = LocalComicStore(File(context.filesDir, "originals"), archive)
    private val pageDirectory = File(context.cacheDir, "pages").apply { mkdirs() }
    private val covers = File(context.filesDir, "covers").apply { mkdirs() }
    private val cache = PageCache(pageDirectory)
    private val thumbnailDirectory = File(context.cacheDir, "thumbnails").apply { mkdirs() }
    private val thumbnailCache = PageCache(thumbnailDirectory, 32L * 1024 * 1024)
    private val thumbnailGenerator = BitmapCoverGenerator(240, 360)
    private val mutex = Mutex()
    private val cinemaDirectory = File(context.filesDir,"cinema-artwork")
    private val cinemaStore by lazy { com.pelican.comictv.cinema.CinemaArtworkStore(cinemaDirectory,
        ::validateCinemaArtwork) }
    private val _cinemaRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val cinemaRevision: kotlinx.coroutines.flow.StateFlow<Long> = _cinemaRevision

    suspend fun cinemaArtwork(id: String): File? = withContext(Dispatchers.IO) {
        mutex.withLock { if (dao.get(id) == null || !cinemaDirectory.exists()) null else cinemaStore.file(id) }
    }

    /** Local files only. Backdrop preparation never resolves a source or downloads a page. */
    internal suspend fun libraryBackdrop(id: String, preferArtwork: Boolean = true): LibraryBackdropImage? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (dao.get(id) == null) return@withLock null
            val active = currentCoroutineContext()
            runInterruptible {
                active.ensureActive()
                try {
                    if (preferArtwork && cinemaDirectory.exists()) backdropImage(cinemaStore.file(id))?.let { return@runInterruptible it }
                } catch (_: IOException) { active.ensureActive() }
                // Full first pages preserve more detail than the separately generated 480×720 cover.
                backdropImage(File(pageDirectory, "$id-0"))?.let { return@runInterruptible it }
                if (com.pelican.comictv.sources.OnlineIdentity.isOnline(id)) {
                    // Read only the already downloaded page, without package-wide hashing or network IO.
                    try {
                        val offline = com.pelican.comictv.offline.OfflineStore(File(context.filesDir, "offline"))
                        if (offline.hasPackage(id)) backdropImage(offline.page(id, 0))?.let { return@runInterruptible it }
                    } catch (_: Exception) { active.ensureActive() }
                } else {
                    val original = store.original(id)
                    if (original.isFile) {
                        try {
                            val first = archive.index(original).firstOrNull()
                            active.ensureActive()
                            if (first != null && !first.key.startsWith("epub:text:")) {
                                val full = cache.materialize("$id-${first.index}", setOfNotNull(protectedPage),
                                    write = { archive.extract(original, first, it); active.ensureActive() },
                                    validate = { validateDimensions(it) })
                                backdropImage(full)?.let { return@runInterruptible it }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { active.ensureActive() /* Keep the stored cover usable. */ }
                    }
                }
                active.ensureActive()
                backdropImage(cover(id))
            }
        }
    }

    /** The edition first page, never an independent cinematic artwork or a network download. */
    suspend fun detailsCover(id: String): File? = libraryBackdrop(id, preferArtwork = false)?.file

    private fun backdropImage(file: File?): LibraryBackdropImage? {
        if (file?.isFile != true) return null
        return try {
            val bounds = validateDimensions(file)
            LibraryBackdropImage(file, bounds.outWidth, bounds.outHeight)
        } catch (_: IOException) { null }
    }

    suspend fun importCinemaArtwork(id: String, uri: Uri): File = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Escolha a imagem pelo seletor de arquivos." }
        val active = currentCoroutineContext()
        // Capture inside the interruptible block: prompt cancellation may discard its return value.
        val prepared = java.util.concurrent.atomic.AtomicReference<com.pelican.comictv.cinema.StagedArtwork?>()
        try {
            val staged = runInterruptible {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cinemaStore.stage(input) { active.ensureActive() }.also(prepared::set)
                } ?: throw IOException("Não foi possível abrir a imagem escolhida.")
            }
            mutex.withLock {
                active.ensureActive()
                require(dao.get(id) != null) { "Esta edição não está mais na biblioteca." }
                val file = cinemaStore.commit(id, staged) { active.ensureActive() }
                _cinemaRevision.value++
                file
            }
        } finally { prepared.get()?.close() }
    }

    suspend fun removeCinemaArtwork(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { if (cinemaDirectory.exists()) { cinemaStore.remove(id); _cinemaRevision.value++ }; Unit }
    }
    private val batchMutex = Mutex()
    private val importScratch = File(context.cacheDir, "collection-imports")
    private val onlineScratch = File(context.cacheDir, "online-fetches").apply { mkdirs() }
    private val transfers = PageTransferScheduler()
    private val imageRequests = ImageRequestTickets()
    private val coverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coverQueue = BackgroundCoverQueue(coverScope) { id, file ->
        com.pelican.comictv.performance.AppTimings.measure(com.pelican.comictv.performance.LoadStage.CoverCreation) { writeOnlineCover(id, file) }
    }
    private var protectedPage: String? = null
    private var ready = false
    suspend fun <T> exclusiveStorage(block: suspend () -> T): T = mutex.withLock { imageRequests.invalidateAll(); block() }
    fun originalFile(id: String): File = store.original(id)
    suspend fun updatePageCount(id: String, count: Int) = dao.updatePageCount(id,count)

    private fun storage() = LibraryStorage(File(context.filesDir, "originals"), covers, pageDirectory, thumbnailDirectory,
        offlineDirectory = File(context.filesDir,"offline"), cinemaDirectory = cinemaDirectory)
    suspend fun storageReport(): StorageReport = withContext(Dispatchers.IO) {
        mutex.withLock { runInterruptible { storage().report() } }
    }
    suspend fun clearReaderCaches(selection: CacheSelection): CacheCleanup = withContext(Dispatchers.IO) {
        mutex.withLock {
            imageRequests.invalidateAll()
            val result = runInterruptible { storage().clear(selection) }
            if (selection != CacheSelection.Thumbnails) protectedPage = null
            coil3.SingletonImageLoader.get(context).memoryCache?.clear()
            result
        }
    }

    suspend fun setHistoryHidden(id: String, hidden: Boolean) = dao.setHistoryHidden(id, hidden)
    /** Confirmed local removal. External import URI/source is never retained or touched. */
    suspend fun deleteImported(id: String): Boolean = withContext(Dispatchers.IO) {
        require(!id.startsWith("online-") && id.matches(Regex("[A-Za-z0-9-]+")))
        mutex.withLock {
            initialize()
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                store.stageRemoval(id)
                try { dao.delete(id) } catch (e: Exception) { store.rollbackRemoval(id); throw e }
                val originalClean = runCatching { store.finishRemoval(id) }.getOrDefault(false)
                cleanupEditionAssets(id) && originalClean
            }
        }
    }
    internal suspend fun deleteOnline(id: String): Boolean = withContext(Dispatchers.IO) {
        require(com.pelican.comictv.sources.OnlineIdentity.isOnline(id))
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) { imageRequests.invalidate(id); dao.delete(id); cleanupEditionAssets(id) }
        }
    }
    private fun cleanupEditionAssets(id: String): Boolean = runCatching {
        var cleaned = true
        if (cinemaDirectory.exists()) {
            if (!runCatching { cinemaStore.remove(id); true }.getOrDefault(false)) cleaned = false
            _cinemaRevision.value++
        }
        val owned = listOf(cover(id)) + listOf(pageDirectory, thumbnailDirectory).flatMap { root ->
            root.listFiles().orEmpty().filter { it.name.matches(Regex(Regex.escape(id) + "-[0-9]+(?:\\..*)?")) }
        }
        for (file in owned) {
            val parent = file.parentFile ?: continue
            val removed = runCatching {
                file.canonicalFile.parentFile == parent.canonicalFile && !file.isDirectory && (!file.exists() || file.delete())
            }.getOrDefault(false)
            if (!removed) cleaned = false
        }
        protectedPage = protectedPage?.takeUnless { it.startsWith("$id-") }
        if (!runCatching { context.getSharedPreferences("reader-image", Context.MODE_PRIVATE).edit().remove("$id:preset").remove("$id:intensity").commit() }.getOrDefault(false)) cleaned = false
        runCatching { coil3.SingletonImageLoader.get(context).memoryCache?.clear() }
        cleaned
    }.getOrDefault(false)
    fun observeComics() = dao.observe()
    fun observeCollections() = collectionDao?.observe() ?: kotlinx.coroutines.flow.flowOf(emptyList<CollectionEntity>())
    fun observeMemberships() = collectionDao?.observeMemberships() ?: kotlinx.coroutines.flow.flowOf(emptyList<CollectionMembership>())
    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun collectionsFor(id: String) = requireNotNull(collectionDao).forComic(id).toSet()
    suspend fun createCollection(value: String): String {
        val (name, key) = validatedCollectionName(value)
        val id = UUID.randomUUID().toString()
        try { requireNotNull(collectionDao).insert(CollectionEntity(id, name, key)) }
        catch (_: android.database.sqlite.SQLiteConstraintException) { throw IllegalArgumentException("Já existe uma coleção com esse nome.") }
        return id
    }
    suspend fun renameCollection(id: String, value: String) {
        val (name, key) = validatedCollectionName(value)
        try { check(requireNotNull(collectionDao).rename(id, name, key) > 0) { "Esta coleção não está mais disponível." } }
        catch (_: android.database.sqlite.SQLiteConstraintException) { throw IllegalArgumentException("Já existe uma coleção com esse nome.") }
    }
    suspend fun deleteCollection(id: String) = requireNotNull(collectionDao).delete(id)
    suspend fun setCollections(id: String, collections: Set<String>) = requireNotNull(collectionDao).replaceMemberships(id, collections)
    override suspend fun getComic(id: String) = dao.get(id) ?: throw IOException("Esta edição não está mais disponível.")
    fun cover(id: String) = File(covers, "$id.jpg")

    internal suspend fun writeOnlineCover(id: String, source: File) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (dao.get(id) == null) return@withLock
            val target = cover(id)
            if (!target.isFile) {
                val temporary = File.createTempFile("$id-cover-", ".part", covers)
                try {
                    coverGenerator.generate(source, temporary)
                    currentCoroutineContext().ensureActive()
                    if (!temporary.renameTo(target)) throw IOException("Não foi possível salvar a capa.")
                } finally { temporary.delete() }
            }
        }
    }
    internal fun queueOnlineCover(id: String, source: File) { coverQueue.enqueue(id, source) }
    internal fun closeBackgroundWork() { coverQueue.close(); coverScope.cancel() }

    /** Network waits are outside storage serialization; only bounded transfers may stage a file. */
    internal suspend fun externalImage(id: String, index: Int, thumbnail: Boolean, foreground: Boolean = true,
        fetch: suspend (File) -> Unit): File = withContext(Dispatchers.IO) {
        require(id.matches(Regex("online-[a-f0-9]{64}")) && index in 0 until 5000)
        val ticket = mutex.withLock {
            initialize()
            if (dao.get(id) == null) throw IOException("Esta edição não está mais na biblioteca.")
            imageRequests.begin(id, currentCoroutineContext()[Job])
        }
        val name = "$id-$index"
        try {
            transfers.load(name, foreground && !thumbnail, cached = {
                AppTimings.measure(LoadStage.CacheLookup) {
                    mutex.withLock { imageRequests.check(ticket); runInterruptible { materializeExternal(name, thumbnail, foreground, null) } }
                }.also { if (it != null) AppTimings.hit(LoadStage.PageCacheHit) }
            }, fetch = {
                val temporary = File.createTempFile("$name-", ".part", onlineScratch)
                try {
                    currentCoroutineContext().ensureActive()
                    fetch(temporary)
                    currentCoroutineContext().ensureActive()
                    AppTimings.measure(LoadStage.CacheCommit) {
                        mutex.withLock {
                            imageRequests.check(ticket)
                            runInterruptible { materializeExternal(name, thumbnail, foreground, temporary)
                                ?: throw IOException("Não foi possível preparar a página.") }
                        }
                    }
                } finally { temporary.delete(); File(temporary.path + ".part").delete() }
            })
        } finally { withContext(NonCancellable) { mutex.withLock { imageRequests.finish(ticket) } } }
    }

    /** Storage mutex held. Cache hits and thumbnail generation never wait for network permits. */
    private fun materializeExternal(name: String, thumbnail: Boolean, foreground: Boolean, downloaded: File?): File? {
        val full = File(pageDirectory, name)
        val target = File(if (thumbnail) thumbnailDirectory else pageDirectory, name)
        if (!target.isFile && downloaded == null && (!thumbnail || !full.isFile)) return null
        val selected = if (thumbnail) thumbnailCache else cache
        val result = selected.materialize(name, if (thumbnail) emptySet() else setOfNotNull(protectedPage), write = { destination ->
            if (thumbnail) {
                cache.materialize(name, setOfNotNull(protectedPage), write = { adoptDownloaded(downloaded, it) }, validate = { validateDimensions(it) })
                val temporary = File(thumbnailDirectory, "$name.part")
                try {
                    thumbnailGenerator.generate(full, temporary)
                    if (Thread.currentThread().isInterrupted) throw IOException("Prévia cancelada.")
                    if (!temporary.renameTo(destination)) throw IOException("Não foi possível salvar a miniatura.")
                } finally { temporary.delete() }
            } else adoptDownloaded(downloaded, destination)
        }, validate = { validateDimensions(it) })
        if (!thumbnail && foreground) { protectedPage = result.name; cache.trimIfNeeded(setOf(result.name)) }
        return result
    }
    private fun adoptDownloaded(source: File?, destination: File) {
        if (source == null) throw IOException("A imagem em cache não está mais disponível.")
        validateDimensions(source)
        if (!source.renameTo(destination)) throw IOException("Não foi possível salvar a página.")
    }

    private suspend fun initialize() {
        if (!ready) {
            store.reconcile(dao.ids().toSet())
            pageDirectory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            thumbnailDirectory.listFiles()?.filter { it.name.endsWith(".part") || it.name.endsWith(".source") }?.forEach { it.delete() }
            onlineScratch.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }?.forEach { it.delete() }
            covers.listFiles()?.filter { it.isFile && it.name.matches(Regex("online-[a-f0-9]{64}-cover-.*\\.part")) }?.forEach { it.delete() }
            thumbnailCache.trim()
            cache.trim(setOfNotNull(protectedPage))
            ready = true
        }
    }

    suspend fun importComic(uri: Uri): ComicEntity = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Quadrinho"
        importComic(name) { context.contentResolver.openInputStream(uri)
            ?: throw IOException("Não foi possível abrir o arquivo selecionado.") }
    }

    suspend fun importComic(file: File, name: String): ComicEntity = importComic(name) { file.inputStream() }

    suspend fun importReceived(uri: Uri, progress: (String) -> Unit): ImportBatchResult = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Quadrinho"
        val staging = File(context.cacheDir, "collection-input-${UUID.randomUUID()}")
        try {
            progress("Recebendo arquivo…")
            runInterruptible {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    var total = 0L
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw IOException("Importação cancelada.")
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > ArchiveLimits().maxArchiveBytes) throw ComicArchiveException("O arquivo excede o limite de 1 GiB.")
                            checkImportSpace(buffer.size.toLong() + CollectionImportRunner.RESERVE)
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: throw IOException("Não foi possível abrir o arquivo selecionado.")
            }
            importReceived(staging, name, progress)
        } finally { staging.delete() }
    }

    suspend fun importReceived(file: File, name: String, progress: (String) -> Unit): ImportBatchResult = withContext(Dispatchers.IO) {
        batchMutex.withLock {
            progress("Conferindo arquivos já importados…")
            // Originals are immutable. Rebuild once per batch to include pre-feature imports and restored IDs.
            val known = mutableMapOf<String, String>()
            mutex.withLock {
                initialize()
                for (id in dao.ids().filterNot { it.startsWith("online-") }) {
                    currentCoroutineContext().ensureActive()
                    val original = store.original(id)
                    if (original.isFile) known[importDigest(original)] = id
                }
            }
            CollectionImportRunner(importScratch, ::checkImportSpace) { input, title ->
                val digest = importDigest(input)
                val existing = known[digest]?.let { id -> mutex.withLock {
                    dao.get(id)?.takeIf { store.original(id).isFile }
                } }
                if (existing != null) ImportedEdition(existing.id, true)
                else {
                    val comic = importComic(input, title)
                    known[digest] = comic.id
                    ImportedEdition(comic.id, false)
                }
            }.run(file, name, progress)
        }
    }

    private suspend fun importDigest(file: File): String = runInterruptible {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw IOException("Importação cancelada.")
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun checkImportSpace(required: Long) {
        if (minOf(context.filesDir.usableSpace, context.cacheDir.usableSpace) < required)
            throw IOException("Espaço insuficiente para importar. Libere armazenamento e tente novamente.")
    }

    private suspend fun importComic(name: String, openInput: () -> InputStream): ComicEntity = withContext(Dispatchers.IO) {
        mutex.withLock {
            initialize()
            val pendingDocument = pendingRestores?.snapshot()
            val restored = if (pendingDocument?.comics?.isNotEmpty() == true) {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                openInput().use { input -> val buffer = ByteArray(16384); while (true) {
                    currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
                    bytes += count; require(bytes <= ArchiveLimits().maxArchiveBytes); digest.update(buffer, 0, count)
                } }
                pendingRestores?.find(digest.digest().joinToString("") { "%02x".format(it) })
            } else null
            val id = restored?.id ?: UUID.randomUUID().toString()
            val existing = if (restored != null) dao.get(id) else null
            if (existing != null && store.original(id).isFile) { pendingRestores?.finish(id); return@withLock existing }
            val title = name.substringBeforeLast('.', name).take(200).ifBlank { "Quadrinho" }
            try {
                val pages = runInterruptible {
                    openInput().use { store.import(id, it) }
                }
                if (restored != null) {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    store.original(id).inputStream().use { input -> val buffer = ByteArray(16384); while (true) {
                        currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
                        digest.update(buffer,0,count)
                    } }
                    require(digest.digest().joinToString("") { "%02x".format(it) } == restored.sha256) { "O arquivo mudou durante a reimportação. Tente novamente." }
                }
                // Validate the first page before publishing the import.
                cache.materialize("$id-0", setOfNotNull(protectedPage),
                    write = { archive.extract(store.original(id), pages.first(), it) },
                    validate = { if (pages.first().key.startsWith("epub:text:")) generateTextCover(title, cover(id)) else coverGenerator.generate(it, cover(id)) })
                val comic = restored?.let { ComicEntity(id, it.title, pages.size, it.currentPage.coerceIn(0, pages.lastIndex),
                    it.lastRead, it.importedAt, it.fit, it.rtl, it.hiddenFromHistory, it.favorite) } ?: ComicEntity(id, title, pages.size)
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    if (restored != null && collectionDao != null) {
                        val available = observeCollections().first().map { it.id }.toSet()
                        val links = pendingDocument!!.links.filter { it.comicId == id }.map { it.collectionId }.toSet().intersect(available)
                        collectionDao.insertRestoredComic(comic, links)
                    } else dao.insert(comic)
                    if (restored != null) pendingRestores?.finish(id)
                }
                comic
            } catch (e: Exception) {
                // If insert committed during cancellation, preserve its original.
                val committed = withContext(NonCancellable) { dao.get(id) != null }
                if (!committed) {
                    store.original(id).delete(); cover(id).delete()
                    File(pageDirectory, "$id-0").delete()
                }
                throw e
            }
        }
    }

    override suspend fun pages(comicId: String): List<PageRef> = withContext(Dispatchers.IO) {
        mutex.withLock { initialize(); runInterruptible { archive.index(store.original(comicId)) } }
    }

    override suspend fun materialize(comicId: String, page: PageRef): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            val original = store.original(comicId)
            val target = runInterruptible {
                cache.materialize("$comicId-${page.index}", setOfNotNull(protectedPage),
                    write = { archive.extract(original, page, it) }, validate = { if (!page.key.startsWith("epub:text:")) validateDimensions(it) })
            }
            protectedPage = target.name
            cache.trimIfNeeded(setOf(target.name))
            target
        }
    }

    override suspend fun prefetch(comicId: String, page: PageRef): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            initialize()
            runInterruptible {
                cache.materialize("$comicId-" + page.index, setOfNotNull(protectedPage),
                    write = { archive.extract(store.original(comicId), page, it) },
                    validate = { if (!page.key.startsWith("epub:text:")) validateDimensions(it) })
            }
        }
    }

    override suspend fun thumbnail(comicId: String, page: PageRef): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            initialize()
            val original = store.original(comicId)
            runInterruptible {
                thumbnailCache.materialize("$comicId-${page.index}", emptySet(), write = { target ->
                    val source = File(thumbnailDirectory, "$comicId-${page.index}.source")
                    val temporary = File(target.parentFile, target.name + ".part")
                    try {
                        val full = File(pageDirectory, "$comicId-" + page.index)
                        val imageSource = if (full.isFile) full else source.also { archive.extract(original, page, it) }
                        if (Thread.currentThread().isInterrupted) throw IOException("Prévia cancelada.")
                        if (page.key.startsWith("epub:text:")) generateTextCover(imageSource.readText(Charsets.UTF_8).take(200), temporary, 240, 360)
                        else thumbnailGenerator.generate(imageSource, temporary)
                        if (Thread.currentThread().isInterrupted) throw IOException("Prévia cancelada.")
                        if (!temporary.renameTo(target)) throw IOException("Não foi possível salvar a miniatura.")
                        try { cache.materialize("$comicId-" + page.index, setOfNotNull(protectedPage),
                            write = { imageSource.copyTo(it, overwrite = true) }, validate = { if (!page.key.startsWith("epub:text:")) validateDimensions(it) }) } catch (_: IOException) { }
                    } finally { source.delete(); File(source.path + ".part").delete(); temporary.delete() }
                }, validate = { validateDimensions(it) })
            }
            // Never change protectedPage: thumbnails do not become the reading page.
        }
    }

    override suspend fun saveProgress(id: String, page: Int) = dao.saveProgress(id, page, System.currentTimeMillis())
    override suspend fun savePreferences(id: String, fit: Int, rtl: Boolean) = dao.savePreferences(id, fit, rtl)

    private fun validateDimensions(file: File): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Esta página não contém uma imagem válida.")
        if (options.outWidth.toLong() * options.outHeight > 100_000_000L) throw IOException("A imagem excede o limite de resolução.")
        return options
    }

}

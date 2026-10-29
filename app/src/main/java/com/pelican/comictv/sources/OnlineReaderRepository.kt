package com.pelican.comictv.sources

import android.content.Context
import android.util.AtomicFile
import com.pelican.comictv.library.ComicDao
import com.pelican.comictv.library.ComicEntity
import com.pelican.comictv.library.LibraryRepository
import com.pelican.comictv.library.ReaderRepository
import com.pelican.comictv.mihon.MihonEngine
import com.pelican.comictv.model.PageRef
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.pelican.comictv.performance.AppTimings
import com.pelican.comictv.performance.LoadStage
import java.io.File
import java.io.IOException

data class OnlineEditions(
    val source: SourceHandle,
    val title: OnlineTitle,
    val current: OnlineChapter,
    val chapters: List<OnlineChapter>,
    val pin: String,
)

/** Durable chapter records share the library's progress, validation and bounded image caches. */
class OnlineReaderRepository(
    context: Context,
    private val dao: ComicDao,
    private val library: LibraryRepository,
    private val engine: MihonEngine,
    private val preferences: SourcePreferences,
) : ReaderRepository {
    private val directory = File(context.filesDir, "online-records").apply { mkdirs() }
    private val coverCoordinator = SagaCoverCoordinator()
    private val coverTemporaryDirectory = File(context.cacheDir, "saga-cover-transfers").apply {
        mkdirs()
        // Only this coordinator writes here; recover bounded abandoned transfers on process startup.
        listFiles()?.filter { it.isFile && it.name.matches(Regex("title-[0-9]+\\.part")) }?.forEach { it.delete() }
    }
    private val mutex = Mutex()
    private val sessions = LinkedHashMap<String, OnlineReadingRecord>()
    private val prefetched = mutableSetOf<String>()
    /** Optional title artwork only. This does not select a source or request chapter pages. */
    suspend fun ensureSagaCover(id: String) = withContext(Dispatchers.IO) {
        if (!OnlineIdentity.isOnline(id)) return@withContext
        try {
            val saved = mutex.withLock { read(id) }
            val identity = com.pelican.comictv.library.sagaIdentity(saved.source.packageName, saved.source.id, saved.title.key)
            val key = library.automaticSagaKey(id, identity) ?: return@withContext
            coverCoordinator.ensure(key) {
                if (library.automaticSagaKey(id, identity) != key) return@ensure
                sourceRequest(SourceRequest.Preview, timeoutMillis = 30_000) {
                    trusted(saved.pin)
                    val source = loadSource(saved.source, saved.pin)
                    coverTemporaryDirectory.mkdirs()
                    require(coverTemporaryDirectory.usableSpace >= 72L * 1024 * 1024) { "Insufficient cover space" }
                    val temporary = File.createTempFile("title-", ".part", coverTemporaryDirectory)
                    try {
                        suspend fun download(url: String) {
                            trusted(saved.pin)
                            sourceRequest(SourceRequest.Image, timeoutMillis = 10_000) {
                                engine.downloadCover(source, url, temporary, 8L * 1024 * 1024)
                            }
                            trusted(saved.pin)
                            com.pelican.comictv.library.validateSagaCover(temporary)
                            loadSource(saved.source, saved.pin) // Recheck certificate after transfer.
                        }
                        val oldUrl = normalizeTitleCoverUrl(saved.title.coverUrl)
                        var downloaded = false
                        if (oldUrl != null) {
                            try { download(oldUrl); downloaded = true }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { currentCoroutineContext().ensureActive() }
                        }
                        if (!downloaded) {
                            trusted(saved.pin)
                            val refreshed = sourceRequest(SourceRequest.Preview, timeoutMillis = 8_000) { engine.titlePreview(source, saved.title) }
                            trusted(saved.pin)
                            loadSource(saved.source, saved.pin)
                            val url = requireNotNull(normalizeTitleCoverUrl(refreshed.coverUrl))
                            download(url)
                            mutex.withLock {
                                val latest = read(id)
                                // Preserve current pages and any reader updates, changing only this title's metadata.
                                write(id, latest.copy(title = latest.title.copy(coverUrl = url)))
                                sessions[id]?.let { sessions[id] = it.copy(title = it.title.copy(coverUrl = url)) }
                            }
                            library.sagaMetadataChanged()
                        }
                        library.saveAutomaticSagaCover(id, identity, key, temporary)
                    } finally { temporary.delete() }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Missing, untrusted or broken sources cannot interrupt reading. */ }
    }

    internal suspend fun forgetEdition(id: String): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        require(OnlineIdentity.isOnline(id))
        sessions.remove(id); prefetched.remove(id)
        listOf("$id.json", "$id.json.bak", "$id.json.new").map { name ->
            runCatching {
                val file = File(directory,name)
                file.canonicalFile.parentFile == directory.canonicalFile && !file.isDirectory && (!file.exists() || file.delete())
            }.getOrDefault(false)
        }.all {it}
    } }
    internal suspend fun prepareOffline(id: String): List<OnlinePage> = withContext(Dispatchers.IO) { mutex.withLock { refresh(id).pages } }
    internal suspend fun downloadOffline(id: String, index: Int, target: File) = withContext(Dispatchers.IO) {
        val record = mutex.withLock { checkNotNull(sessions[id]) { "Retome o download para atualizar a fonte." } }
        trusted(record.pin)
        AppTimings.measure(LoadStage.RemoteImage) { sourceRequest(SourceRequest.Image) { engine.download(record.source, record.pages[index], target) } }
    }

    internal suspend fun backupMetadata(id: String): com.pelican.comictv.backup.BackupOnline = mutex.withLock {
        val record = read(id)
        com.pelican.comictv.backup.BackupOnline(record.source, record.title.copy(coverUrl = null, title = record.title.title.take(300),
            description = record.title.description?.take(16000),author = record.title.author?.take(300),artist = record.title.artist?.take(300),
            publisher = record.title.publisher?.take(300),characters = record.title.characters?.take(2000),director = record.title.director?.take(300)),
            record.chapter.copy(title = record.chapter.title.take(300)), record.pin)
    }
    internal suspend fun restoreMetadata(id: String, metadata: com.pelican.comictv.backup.BackupOnline) = mutex.withLock {
        require(OnlineIdentity.id(metadata.source, metadata.title, metadata.chapter) == id)
        if (!File(directory, "$id.json").isFile) write(id, OnlineReadingRecord(metadata.source, metadata.title, metadata.chapter, metadata.pin, emptyList()))
        sessions.remove(id); prefetched.remove(id)
    }

    suspend fun editionInfo(id: String): OnlineEditionInfo = withContext(Dispatchers.IO) {
        require(OnlineIdentity.isOnline(id)) { "Identificador online inválido." }
        mutex.withLock { read(id).editionInfo() }
    }
    /** Exact durable title identities; matching display names alone do not establish a link. */
    internal suspend fun globalSearchLibraryLinks(ids: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            buildMap {
                for (id in ids.filter(OnlineIdentity::isOnline)) {
                    currentCoroutineContext().ensureActive()
                    val record = try { read(id) } catch (_: Exception) { continue }
                    put("${record.source.packageName}:${record.source.id}:${record.title.key}", id)
                }
            }
        }
    }
    /** Read-only discovery. Never opens pages or updates reading progress. */
    suspend fun related(id: String): RelatedResult = withContext(Dispatchers.IO) {
        if (!OnlineIdentity.isOnline(id)) return@withContext RelatedResult(emptyList(),
            "Este arquivo local não tem créditos cadastrados para encontrar relações.")
        mutex.withLock {
            val saved = read(id)
            trusted(saved.pin)
            val source = loadSource(saved.source, saved.pin)
            suspend fun preview(title: OnlineTitle): OnlineTitle = try {
                withTimeoutOrNull(6_000) { sourceRequest(SourceRequest.Preview) { engine.titlePreview(source, title) } } ?: title
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { title }
            val base = preview(saved.title)
            write(id, saved.copy(title = base))
            sessions[id]?.let { sessions[id] = it.copy(title = base) }
            val candidates = linkedMapOf<Pair<SourceHandle, String>, RelatedComic>()
            val rows = dao.observe().first().filter { it.id != id && OnlineIdentity.isOnline(it.id) }.take(60)
            var refreshed = 0
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                val record = try { read(row.id) } catch (e: Exception) { continue }
                if (!preferences.isTrusted(record.pin)) continue
                val same = record.source.packageName == source.packageName && record.source.id == source.id
                val title = if (same && refreshed < 6 && record.title.author.isNullOrBlank() && record.title.artist.isNullOrBlank()) {
                    refreshed++
                    preview(record.title).also { updated ->
                        write(row.id, record.copy(title = updated))
                        sessions[row.id]?.let { sessions[row.id] = it.copy(title = updated) }
                    }
                } else record.title
                val reasons = relatedReasons(base, title, same)
                if (reasons.isNotEmpty()) candidates.putIfAbsent(record.source to title.key,
                    RelatedComic(row.id, record.source, title, record.pin, reasons))
            }
            // Source catalogs rarely expose author filters; confirm credits on a bounded sample.
            var partial = false
            val finished = withTimeoutOrNull(35_000) {
                val pool = linkedMapOf<String, OnlineTitle>()
                val queries = listOfNotNull(base.author, base.artist).map { it.split(';', ',').first().trim() }
                    .filter { it.isNotBlank() }.distinct().take(2) + ""
                for (query in queries) {
                    try { sourceRequest(SourceRequest.Catalog) { engine.catalog(source, query = query) }.titles.take(24)
                        .forEach { if (it.key != base.key) pool.putIfAbsent(it.key, it) } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { partial = true }
                }
                for (title in pool.values.take(16)) {
                    currentCoroutineContext().ensureActive()
                    if (candidates.containsKey(source to title.key)) continue
                    val resolved = preview(title)
                    val reasons = relatedReasons(base, resolved, true)
                    if (reasons.isNotEmpty()) candidates[source to title.key] = RelatedComic(null, source, resolved, saved.pin, reasons)
                }
                true
            }
            if (finished == null) partial = true
            val result = selectRelated(id, candidates.values.toList())
            RelatedResult(result, if (result.isEmpty())
                "Nenhuma relação confirmada na biblioteca e na amostra consultada da fonte. Créditos ausentes limitam as sugestões."
                else if (partial) "Resultados parciais da biblioteca e da fonte."
                else "Relações confirmadas na biblioteca e em uma amostra do catálogo da fonte.")
        }
    }
    suspend fun relatedDetails(item: RelatedComic): OnlineDetails = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = loadSource(item.source, item.pin)
            sourceRequest(SourceRequest.Details) { engine.details(source, item.title) }
        }
    }

    /** Call after leaving the reader: loading an extension replaces its active page context. */
    suspend fun editions(id: String): OnlineEditions = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(OnlineIdentity.isOnline(id)) { "Identificador online inválido." }
            // Reading the durable record avoids refreshing pages or touching reading progress.
            val saved = read(id)
            trusted(saved.pin)
            val source = loadSource(saved.source, saved.pin)
            val details = sourceRequest(SourceRequest.Details) { engine.details(source, saved.title) }
            val sequence = chapterSequence(details.chapters, saved.chapter.key)
            OnlineEditions(source, details.title, saved.chapter, sequence.chapters, saved.pin)
        }
    }

    suspend fun open(source: SourceHandle, title: OnlineTitle, chapter: OnlineChapter, pin: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = OnlineIdentity.id(source, title, chapter)
            sessions.remove(id)
            prefetched.remove(id)
            trusted(pin)
            val actual = loadSource(source, pin)
            val pages = AppTimings.measure(LoadStage.PageList) { sourceRequest(SourceRequest.PageList) { engine.pages(actual, chapter, title) } }
            requirePages(pages)
            val record = OnlineReadingRecord(actual, title, chapter, pin, pages)
            write(id, record)
            val existing = dao.get(id)
            if (existing == null) dao.insert(ComicEntity(id, "${title.title} · ${chapter.title}".take(300), pages.size))
            else dao.updatePageCount(id, pages.size)
            sessions[id] = record
            prefetched += id
            trimSessions()
            id
        }
    }

    override suspend fun pages(comicId: String): List<PageRef> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val record = if (prefetched.remove(comicId)) session(comicId) else refresh(comicId)
            record.pages.indices.map { PageRef(it, "online:$it") }
        }
    }

    override suspend fun materialize(comicId: String, page: PageRef): File = image(comicId, page, false)
    override suspend fun thumbnail(comicId: String, page: PageRef): File = image(comicId, page, true)
    override suspend fun prefetch(comicId: String, page: PageRef): File = image(comicId, page, false, foreground = false)

    private suspend fun image(id: String, page: PageRef, thumbnail: Boolean, foreground: Boolean = true): File = withContext(Dispatchers.IO) {
        val record = mutex.withLock {
            val current = session(id)
            require(page.index in current.pages.indices && page.key == "online:${page.index}") { "Página online inválida." }
            current
        }
        trusted(record.pin)
        val stage = if (thumbnail) LoadStage.Thumbnail else if (foreground) LoadStage.PageMaterialize else LoadStage.PagePrefetch
        val result = AppTimings.measure(stage) { library.externalImage(id, page.index, thumbnail, foreground) { target ->
            trusted(record.pin)
            AppTimings.measure(LoadStage.RemoteImage) { sourceRequest(SourceRequest.Image) { engine.download(record.source, record.pages[page.index], target) } }
        } }
        trusted(record.pin)
        if (!thumbnail && page.index == 0) {
            library.queueOnlineCover(id, result)
        }
        result
    }

    /** Refresh once after process/session restart; saved signed URLs are never blindly reused. */
    private suspend fun session(id: String): OnlineReadingRecord {
        require(OnlineIdentity.isOnline(id)) { "Identificador online inválido." }
        sessions[id]?.let { trusted(it.pin); return it }
        return refresh(id)
    }

    private suspend fun refresh(id: String): OnlineReadingRecord {
        require(OnlineIdentity.isOnline(id)) { "Identificador online inválido." }
        val saved = sessions[id] ?: read(id)
        trusted(saved.pin)
        val source = loadSource(saved.source, saved.pin)
        val pages = AppTimings.measure(LoadStage.PageList) { sourceRequest(SourceRequest.PageList) { engine.pages(source, saved.chapter, saved.title) } }
        requirePages(pages)
        val refreshed = saved.copy(source = source, pages = pages)
        write(id, refreshed)
        dao.updatePageCount(id, pages.size)
        sessions[id] = refreshed
        trimSessions()
        return refreshed
    }

    private fun loadSource(source: SourceHandle, pin: String): SourceHandle {
        trusted(pin)
        val sources = extensionOperation { engine.load(source.packageName, pin) }
        return sources.firstOrNull { it.id == source.id } ?: throw IOException("Esta fonte não está mais disponível na extensão.")
    }

    private fun trusted(pin: String) {
        check(pin.matches(Regex("[a-f0-9]{64}")) && preferences.isTrusted(pin)) { "O repositório desta fonte não está mais confiável. Confira os repositórios antes de continuar." }
    }
    private fun requirePages(pages: List<OnlinePage>) = OnlineRecordCodec.requirePages(pages)
    private fun trimSessions() { while (sessions.size > 2) { val oldest = sessions.keys.first(); sessions.remove(oldest); prefetched.remove(oldest) } }

    private fun read(id: String): OnlineReadingRecord {
        val atomic = AtomicFile(File(directory, "$id.json"))
        val bytes = atomic.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= OnlineRecordCodec.MAX_BYTES) { "Registro online excede o limite." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return OnlineRecordCodec.decode(id, bytes)
    }
    private fun write(id: String, record: OnlineReadingRecord) {
        val bytes = OnlineRecordCodec.encode(record)
        val atomic = AtomicFile(File(directory, "$id.json"))
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (failure: Throwable) { atomic.failWrite(output); throw failure }
    }

    override suspend fun getComic(id: String): ComicEntity = dao.get(id) ?: throw IOException("Este capítulo não está mais disponível.")
    override suspend fun saveProgress(id: String, page: Int) = dao.saveProgress(id, page, System.currentTimeMillis())
    override suspend fun savePreferences(id: String, fit: Int, rtl: Boolean) = dao.savePreferences(id, fit, rtl)
}

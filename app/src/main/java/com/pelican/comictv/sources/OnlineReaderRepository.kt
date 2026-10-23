package com.pelican.comictv.sources

import android.content.Context
import android.util.AtomicFile
import com.pelican.comictv.library.ComicDao
import com.pelican.comictv.library.ComicEntity
import com.pelican.comictv.library.LibraryRepository
import com.pelican.comictv.library.ReaderRepository
import com.pelican.comictv.mihon.MihonEngine
import com.pelican.comictv.model.PageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val mutex = Mutex()
    private val sessions = LinkedHashMap<String, OnlineReadingRecord>()
    private val prefetched = mutableSetOf<String>()

    /** Call after leaving the reader: loading an extension replaces its active page context. */
    suspend fun editions(id: String): OnlineEditions = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(OnlineIdentity.isOnline(id)) { "Identificador online inválido." }
            // Reading the durable record avoids refreshing pages or touching reading progress.
            val saved = read(id)
            trusted(saved.pin)
            val source = loadSource(saved.source, saved.pin)
            val details = extensionOperation { engine.details(source, saved.title) }
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
            val pages = extensionOperation { engine.pages(actual, chapter) }
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

    private suspend fun image(id: String, page: PageRef, thumbnail: Boolean): File = withContext(Dispatchers.IO) {
        val record = mutex.withLock {
            val current = session(id)
            require(page.index in current.pages.indices && page.key == "online:${page.index}") { "Página online inválida." }
            current
        }
        trusted(record.pin)
        val result = library.externalImage(id, page.index, thumbnail) { target ->
            trusted(record.pin)
            extensionOperation { engine.download(record.source, record.pages[page.index], target) }
        }
        if (!thumbnail && page.index == 0) {
            // An optional cover failure must not hide an already validated reading page.
            try { library.writeOnlineCover(id, result) } catch (_: IOException) { }
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
        val pages = extensionOperation { engine.pages(source, saved.chapter) }
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

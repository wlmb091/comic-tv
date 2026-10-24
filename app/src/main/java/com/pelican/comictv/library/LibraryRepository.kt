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
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class LibraryRepository(private val context: Context, private val dao: ComicDao,
    private val coverGenerator: CoverGenerator = BitmapCoverGenerator()) : ReaderRepository {
    private val archive = ComicArchive(pdfReader = PdfComicArchive())
    private val store = LocalComicStore(File(context.filesDir, "originals"), archive)
    private val pageDirectory = File(context.cacheDir, "pages").apply { mkdirs() }
    private val covers = File(context.filesDir, "covers").apply { mkdirs() }
    private val cache = PageCache(pageDirectory)
    private val thumbnailDirectory = File(context.cacheDir, "thumbnails").apply { mkdirs() }
    private val thumbnailCache = PageCache(thumbnailDirectory, 32L * 1024 * 1024)
    private val thumbnailGenerator = BitmapCoverGenerator(240, 360)
    private val mutex = Mutex()
    private var protectedPage: String? = null
    private var ready = false

    suspend fun setHistoryHidden(id: String, hidden: Boolean) = dao.setHistoryHidden(id, hidden)
    fun observeComics() = dao.observe()
    override suspend fun getComic(id: String) = dao.get(id) ?: throw IOException("Esta edição não está mais disponível.")
    fun cover(id: String) = File(covers, "$id.jpg")

    internal suspend fun writeOnlineCover(id: String, source: File) = withContext(Dispatchers.IO) {
        val target = cover(id)
        if (!target.isFile) {
            val temporary = File(covers, "$id.jpg.part")
            try {
                coverGenerator.generate(source, temporary)
                currentCoroutineContext().ensureActive()
                if (!temporary.renameTo(target)) throw IOException("Não foi possível salvar a capa.")
            } finally { temporary.delete() }
        }
    }

    /** Remote and local pages share quotas, serialization and the visible-page protection. */
    internal suspend fun externalImage(id: String, index: Int, thumbnail: Boolean, foreground: Boolean = true,
        fetch: suspend (File) -> Unit): File = withContext(Dispatchers.IO) {
        require(id.matches(Regex("online-[a-f0-9]{64}")) && index in 0 until 5000)
        mutex.withLock {
            initialize()
            val directory = if (thumbnail) thumbnailDirectory else pageDirectory
            val selectedCache = if (thumbnail) thumbnailCache else cache
            val name = "$id-$index"
            val temporarySource = File(directory, "$name.remote.part")
            val cachedFull = File(pageDirectory, name)
            val source = if (thumbnail && cachedFull.isFile) cachedFull else temporarySource
            val target = File(directory, name)
            try {
                if (!target.isFile) {
                    if (source === temporarySource) fetch(source)
                }
                currentCoroutineContext().ensureActive()
                val result = runInterruptible {
                    selectedCache.materialize(name, if (thumbnail) emptySet() else setOfNotNull(protectedPage),
                        write = { destination ->
                            validateDimensions(source)
                            if (thumbnail) {
                                val temporary = File(directory, "$name.part")
                                try {
                                    thumbnailGenerator.generate(source, temporary)
                                    if (Thread.currentThread().isInterrupted) throw IOException("Prévia cancelada.")
                                    if (!temporary.renameTo(destination)) throw IOException("Não foi possível salvar a miniatura.")
                                } finally { temporary.delete() }
                            } else if (!source.renameTo(destination)) throw IOException("Não foi possível salvar a página.")
                        }, validate = { validateDimensions(it) })
                }
                if (thumbnail && source.isFile) {
                    try { cache.materialize(name, setOfNotNull(protectedPage),
                        write = { source.copyTo(it, overwrite = true) }, validate = { validateDimensions(it) }) } catch (_: IOException) { }
                }
                if (!thumbnail && foreground) {
                    protectedPage = result.name
                    cache.trim(setOf(result.name))
                }
                result
            } finally { temporarySource.delete(); File(temporarySource.path + ".part").delete() }
        }
    }

    private suspend fun initialize() {
        if (!ready) {
            store.reconcile(dao.ids().toSet())
            pageDirectory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            thumbnailDirectory.listFiles()?.filter { it.name.endsWith(".part") || it.name.endsWith(".source") }?.forEach { it.delete() }
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

    private suspend fun importComic(name: String, openInput: () -> InputStream): ComicEntity = withContext(Dispatchers.IO) {
        mutex.withLock {
            initialize()
            val id = UUID.randomUUID().toString()
            val title = name.substringBeforeLast('.', name).take(200).ifBlank { "Quadrinho" }
            try {
                val pages = runInterruptible {
                    openInput().use { store.import(id, it) }
                }
                // Validate the first page before publishing the import.
                cache.materialize("$id-0", setOfNotNull(protectedPage),
                    write = { archive.extract(store.original(id), pages.first(), it) },
                    validate = { if (pages.first().key.startsWith("epub:text:")) generateTextCover(title, cover(id)) else coverGenerator.generate(it, cover(id)) })
                val comic = ComicEntity(id, title, pages.size)
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) { dao.insert(comic) }
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
            cache.trim(setOf(target.name))
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

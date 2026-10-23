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
import java.util.UUID

class LibraryRepository(private val context: Context, private val dao: ComicDao) : ReaderRepository {
    private val archive = ZipComicArchive()
    private val store = LocalComicStore(File(context.filesDir, "originals"), archive)
    private val pageDirectory = File(context.cacheDir, "pages").apply { mkdirs() }
    private val covers = File(context.filesDir, "covers").apply { mkdirs() }
    private val cache = PageCache(pageDirectory)
    private val mutex = Mutex()
    private var protectedPage: String? = null
    private var ready = false

    fun observeComics() = dao.observe()
    override suspend fun getComic(id: String) = dao.get(id) ?: throw IOException("Esta edição não está mais disponível.")
    fun cover(id: String) = File(covers, "$id.jpg")

    private suspend fun initialize() {
        if (!ready) {
            store.reconcile(dao.ids().toSet())
            pageDirectory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            cache.trim(setOfNotNull(protectedPage))
            ready = true
        }
    }

    suspend fun importComic(uri: Uri): ComicEntity = withContext(Dispatchers.IO) {
        mutex.withLock {
            initialize()
            val id = UUID.randomUUID().toString()
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Quadrinho"
            val title = name.substringBeforeLast('.', name).take(200).ifBlank { "Quadrinho" }
            try {
                val pages = runInterruptible {
                    context.contentResolver.openInputStream(uri)?.use { store.import(id, it) }
                        ?: throw IOException("Não foi possível abrir o arquivo selecionado.")
                }
                // Validate the first page before publishing the import.
                cache.materialize("$id-0", setOfNotNull(protectedPage),
                    write = { archive.extract(store.original(id), pages.first(), it) },
                    validate = { createCover(it, cover(id)) })
                val comic = ComicEntity(id, title, pages.size)
                withContext(NonCancellable) { dao.insert(comic) }
                comic
            } catch (e: Exception) {
                // If insert committed during cancellation, preserve its original.
                val committed = withContext(NonCancellable) { dao.get(id) != null }
                if (!committed) { store.original(id).delete(); cover(id).delete() }
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
                    write = { archive.extract(original, page, it) }, validate = { validateDimensions(it) })
            }
            protectedPage = target.name
            cache.trim(setOf(target.name))
            target
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

    private fun createCover(input: File, output: File) {
        val options = validateDimensions(input)
        options.inJustDecodeBounds = false
        var sample = 1
        while (options.outWidth / sample > 480 || options.outHeight / sample > 720) sample *= 2
        options.inSampleSize = sample
        val bitmap = BitmapFactory.decodeFile(input.path, options) ?: throw IOException("Não foi possível gerar a capa.")
        try { output.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) throw IOException("Falha ao salvar a capa.") } }
        finally { bitmap.recycle() }
    }
}

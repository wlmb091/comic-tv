package com.pelican.comictv.reader.narration

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.Writer
import java.util.UUID

/** One parser/session, serialized off the UI thread. Never alters or rasterizes the original. */
class PdfNarrationSource(private val context: Context, private val file: File, override val count: Int) : NarrationSource {
    override val label = "Página"
    private val mutex = Mutex()
    private var document: PDDocument? = null
    private var scratch: File? = null
    private var closed = false
    override suspend fun text(page: Int): String? = withContext(Dispatchers.IO) {
        mutex.withLock { runInterruptible {
            check(!closed) { "A sessão de texto foi encerrada." }
            require(page in 0 until count)
            val pdf = document ?: open().also { document = it }
            val writer = BoundedPdfTextWriter()
            PDFTextStripper().apply { sortByPosition = true; startPage = page + 1; endPage = page + 1 }.writeText(pdf, writer)
            writer.toString().replace("\r\n", "\n").trim().takeIf { it.isNotBlank() }
        } }
    }
    private fun open(): PDDocument {
        if (!file.isFile || file.length() <= 0) throw IOException("O PDF original não está disponível.")
        if (file.length() > 64L * MIB) throw IOException("Narração disponível para PDFs de até 64 MB. O leitor visual continua disponível.")
        require(count in 1..5000)
        synchronized(resourceLock) { if (!resourcesReady) { PDFBoxResourceLoader.init(context.applicationContext); resourcesReady = true } }
        val root = File(context.cacheDir, "narration-scratch").apply { mkdirs() }
        val available = root.usableSpace - 64L * MIB
        if (available < MIB) throw IOException("Espaço insuficiente para preparar o texto. É necessário manter 64 MB livres.")
        val temporary = newScratch(root)
        scratch = temporary
        try {
            val pdf = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly(minOf(128L * MIB, available)).setTempDir(temporary))
            if (pdf.isEncrypted || pdf.numberOfPages != count) { pdf.close(); throw IOException("PDF protegido ou alterado. Abra novamente pelo leitor visual.") }
            return pdf
        } catch (e: Exception) { cleanupScratch(); throw e }
    }
    override suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock { closed = true; try { document?.close() } finally { document = null; cleanupScratch() }; Unit }
    }
    private fun cleanupScratch() {
        val directory = scratch ?: return
        synchronized(resourceLock) { try { directory.deleteRecursively() } finally { active.remove(directory.absolutePath); scratch = null } }
    }
    companion object {
        private const val MIB = 1024L * 1024
        private val resourceLock = Any()
        private var resourcesReady = false
        private val active = mutableSetOf<String>()
        private fun newScratch(root: File): File = synchronized(resourceLock) {
            root.listFiles()?.filter { it.isDirectory && it.name.startsWith("session-") && it.absolutePath !in active && it.canonicalFile.parentFile == root.canonicalFile }
                ?.forEach { it.deleteRecursively() }
            File(root, "session-${UUID.randomUUID()}").also { check(it.mkdirs()); active += it.absolutePath }
        }
    }
}

internal class BoundedPdfTextWriter : Writer() {
    private val text = StringBuilder()
    override fun write(buffer: CharArray, offset: Int, length: Int) {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Extração cancelada.")
        if (text.length + length > 120_000) throw IOException("Esta página excede o limite de texto para narração.")
        text.append(buffer, offset, length)
    }
    override fun flush() = Unit
    override fun close() = Unit
    override fun toString() = text.toString()
}

class EpubNarrationSource(override val count: Int, private val read: suspend (Int) -> String?) : NarrationSource {
    override val label = "Seção"
    override suspend fun text(page: Int): String? = read(page)
    override suspend fun close() = Unit
}

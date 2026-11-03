package com.pelican.comictv.companion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.pelican.comictv.AppContainer
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*

internal data class ExportState(val state: String = "idle", val done: Int = 0, val total: Int = 0, val message: String = "")

/** Builds a CBZ with the drawings applied, one edition at a time. Pages without a drawing keep their original bytes. */
internal class ComicExporter(private val container: AppContainer, private val folder: File, private val scope: CoroutineScope) {
    private var job: Job? = null
    @Volatile private var comicId = ""
    @Volatile private var current = ExportState()
    fun state(id: String) = if (id == comicId) current else ExportState()
    fun file(id: String): File? = File(folder, "export.cbz").takeIf { id == comicId && current.state == "done" && it.isFile }
    fun start(id: String) {
        check(job?.isActive != true) { "Já há uma exportação em andamento." }
        comicId = id; current = ExportState("running")
        job = scope.launch(Dispatchers.IO) {
            try { run(id); current = current.copy(state = "done") }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { current = ExportState("error", message = e.message?.take(200) ?: "Não foi possível exportar.") }
        }
    }
    fun clear() { job?.cancel(); job = null; comicId = ""; current = ExportState(); folder.deleteRecursively() }
    private suspend fun run(id: String) {
        val reader = if (id.startsWith("online-")) container.onlineReader else container.library
        val pages = reader.pages(id)
        check(pages.none { it.key.startsWith("epub:text:") }) { "Só quadrinhos de imagem podem virar CBZ." }
        folder.mkdirs()
        val target = File(folder, "export.cbz"); val staged = File(folder, "export.tmp")
        target.delete()
        current = ExportState("running", 0, pages.size)
        val digits = pages.size.toString().length.coerceAtLeast(3)
        try {
            ZipOutputStream(staged.outputStream().buffered()).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                pages.forEachIndexed { index, page ->
                    currentCoroutineContext().ensureActive()
                    check(folder.usableSpace > 64L * 1024 * 1024) { "Espaço insuficiente na TV." }
                    val source = reader.prefetch(id, page)
                    val number = (index + 1).toString().padStart(digits, '0')
                    val drawing = container.pageDrawings.file(id, index)
                    val composed = drawing?.let { compose(source, it) }
                    if (composed != null) {
                        zip.putNextEntry(ZipEntry("$number.jpg")); zip.write(composed)
                    } else {
                        zip.putNextEntry(ZipEntry("$number.${extension(source)}")); source.inputStream().use { it.copyTo(zip) }
                    }
                    zip.closeEntry()
                    current = current.copy(done = index + 1)
                }
            }
            check(staged.renameTo(target)) { "Não foi possível salvar o CBZ." }
        } finally { staged.delete() }
    }
    private fun extension(file: File): String {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return when (options.outMimeType) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
    }
    /** A page too big for this device's memory goes at half the size instead of failing the whole file. */
    private fun compose(page: File, drawing: File): ByteArray? {
        for (sample in listOf(1, 2, 4)) {
            try {
                val options = BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888; inSampleSize = sample }
                val base = BitmapFactory.decodeFile(page.path, options) ?: return null
                try {
                    val layer = BitmapFactory.decodeFile(drawing.path) ?: return null
                    Canvas(base).drawBitmap(layer, null, RectF(0f, 0f, base.width.toFloat(), base.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                    layer.recycle()
                    return java.io.ByteArrayOutputStream().also { base.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                } finally { base.recycle() }
            } catch (_: OutOfMemoryError) { }
        }
        error("Página grande demais para a memória da TV.")
    }
}

package com.pelican.comictv.library

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.pelican.comictv.archive.*
import com.pelican.comictv.model.PageRef
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/** Render one PDF page at a time. The original PDF stays untouched. */
class PdfComicArchive : ComicArchiveReader {
    private inline fun <T> withPdf(file: File, block: (PdfRenderer) -> T): T = try {
        if (file.length() > ArchiveLimits().maxArchiveBytes) throw ComicArchiveException("O PDF excede o limite de 1 GiB.")
        if (Thread.currentThread().isInterrupted) throw IOException("Leitura cancelada.")
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount !in 1..5_000) throw ComicArchiveException("O PDF está vazio ou contém páginas demais.")
                block(renderer)
            }
        }
    } catch (e: ComicArchiveException) { throw e }
      catch (e: Exception) { throw ComicArchiveException("Não foi possível abrir o PDF. Verifique se está íntegro e sem senha.", e) }

    override fun index(file: File): List<PageRef> = withPdf(file) { renderer ->
        (0 until renderer.pageCount).map { PageRef(it, "pdf:$it") }
    }

    override fun extract(file: File, page: PageRef, destination: File): File = withPdf(file) { renderer ->
        if (page.index !in 0 until renderer.pageCount || page.key != "pdf:${page.index}") throw ComicArchiveException("Página PDF não encontrada.")
        val temporary = File(destination.parentFile, destination.name + ".part")
        try {
            renderer.openPage(page.index).use { pdfPage ->
                if (pdfPage.width <= 0 || pdfPage.height <= 0) throw ComicArchiveException("O PDF contém uma página com dimensões inválidas.")
                val scale = 2048.0 / maxOf(pdfPage.width, pdfPage.height)
                val width = (pdfPage.width * scale).roundToInt().coerceAtLeast(1)
                val height = (pdfPage.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    if (Thread.currentThread().isInterrupted) throw IOException("Leitura cancelada.")
                    destination.parentFile?.mkdirs()
                    temporary.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Falha ao renderizar o PDF.") }
                } finally { bitmap.recycle() }
            }
            if (!temporary.renameTo(destination)) throw IOException("Falha ao salvar a página PDF.")
            destination
        } finally { temporary.delete() }
    }
}

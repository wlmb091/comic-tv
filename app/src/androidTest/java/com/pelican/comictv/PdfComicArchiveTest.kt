package com.pelican.comictv

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.archive.ComicArchive
import com.pelican.comictv.library.PdfComicArchive
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PdfComicArchiveTest {
    @Test fun rendersSyntheticPdfAndRecognizesWrongExtension() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "pdf-test").apply { mkdirs() }
        try {
            val file = File(root, "wrong.cbz")
            val doc = PdfDocument()
            try {
                repeat(2) { i ->
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(600, 800, i + 1).create())
                    page.canvas.drawText("Page ${i + 1}", 20f, 40f, Paint().apply { textSize = 24f })
                    doc.finishPage(page)
                }
                file.outputStream().use(doc::writeTo)
            } finally { doc.close() }
            val archive = ComicArchive(pdfReader = PdfComicArchive())
            val pages = archive.index(file)
            assertEquals(2, pages.size)
            val output = archive.extract(file, pages.last(), File(root, "page"))
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.path, options)
            assertEquals(2048, options.outHeight)
            assertFalse(File(root, "page.part").exists())
        } finally { root.deleteRecursively() }
    }
}

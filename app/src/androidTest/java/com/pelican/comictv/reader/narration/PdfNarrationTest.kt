package com.pelican.comictv.reader.narration

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class PdfNarrationTest {
    @Test fun extractsSelectableTextButDoesNotInventTextOnBlankPagesAndCleansScratch() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(app)
        val root = File(app.cacheDir, "pdf-narration-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getCacheDir(): File = root }
        val file = File(root, "book.pdf")
        PDDocument().use { pdf ->
            val page = PDPage(); pdf.addPage(page)
            val scanned = PDPage(); pdf.addPage(scanned)
            PDPageContentStream(pdf, page).use { stream ->
                stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 16f)
                stream.newLineAtOffset(40f, 700f); stream.showText("A book with selectable text."); stream.endText()
            }
            val bitmap = android.graphics.Bitmap.createBitmap(300, 100, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).apply {
                drawColor(android.graphics.Color.WHITE)
                drawText("Only pixels, not selectable text", 5f, 50f, android.graphics.Paint().apply { textSize = 18f })
            }
            PDPageContentStream(pdf, scanned).use { it.drawImage(LosslessFactory.createFromImage(pdf, bitmap), 40f, 600f) }
            bitmap.recycle()
            pdf.save(file)
        }
        val source = PdfNarrationSource(context, file, 2)
        try {
            assertEquals("A book with selectable text.", source.text(0))
            assertNull(source.text(1))
        } finally { source.close() }
        assertTrue(File(root, "narration-scratch").listFiles().orEmpty().isEmpty())
        val oversized = File(root, "large.pdf")
        RandomAccessFile(oversized, "rw").use { it.setLength(64L * 1024 * 1024 + 1) }
        val limited = PdfNarrationSource(context, oversized, 1)
        try { try { limited.text(0); fail("Oversized PDF accepted") } catch (e: IOException) { assertTrue(e.message.orEmpty().contains("64 MB")) } }
        finally { limited.close(); root.deleteRecursively() }
    }
}

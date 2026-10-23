package com.pelican.comictv.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import com.pelican.comictv.archive.ComicImportException
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

fun interface CoverGenerator { fun generate(input: File, output: File) }

internal fun generateTextCover(title: String, output: File) {
    val bitmap = Bitmap.createBitmap(360, 540, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(25, 28, 34))
        canvas.drawRect(28f, 40f, 84f, 46f, Paint().apply { color = Color.rgb(255, 191, 88) })
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
        val layout = StaticLayout.Builder.obtain(title.take(200), 0, title.take(200).length, paint, 300)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(10).build()
        canvas.save(); canvas.translate(30f, 100f); layout.draw(canvas); canvas.restore()
        canvas.drawText("EPUB · TEXTO", 30f, 500f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 191, 88); textSize = 19f })
        output.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) throw IOException("Falha ao salvar a capa do EPUB.") }
    } finally { bitmap.recycle() }
}

class BitmapCoverGenerator : CoverGenerator {
    override fun generate(input: File, output: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw ComicImportException("A primeira página não contém uma imagem válida em JPEG, PNG ou WebP.")
        if (options.outWidth.toLong() * options.outHeight > 100_000_000L) throw ComicImportException("A imagem excede o limite de resolução.")
        options.inJustDecodeBounds = false
        var sample = 1
        while (options.outWidth / sample > 480 || options.outHeight / sample > 720) sample *= 2
        options.inSampleSize = sample
        val bitmap = BitmapFactory.decodeFile(input.path, options) ?: throw ComicImportException("Não foi possível decodificar a imagem da primeira página.")
        try { output.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) throw IOException("Falha ao salvar a capa.") } }
        finally { bitmap.recycle() }
    }
}

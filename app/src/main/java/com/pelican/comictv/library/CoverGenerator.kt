package com.pelican.comictv.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import com.pelican.comictv.archive.ComicImportException

fun interface CoverGenerator { fun generate(input: File, output: File) }

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

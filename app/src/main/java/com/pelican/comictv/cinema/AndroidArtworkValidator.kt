package com.pelican.comictv.cinema

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File

internal fun validateCinemaArtwork(file: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var width = bounds.outWidth
    var height = bounds.outHeight
    if (bounds.outMimeType == "image/jpeg") {
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (orientation in setOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE)) {
            width = bounds.outHeight; height = bounds.outWidth
        }
    }
    CinemaImagePolicy.validate(width, height, bounds.outMimeType)
    if (bounds.outMimeType == "image/webp") {
        val header = ByteArray(21)
        java.io.DataInputStream(file.inputStream()).use { it.readFully(header) }
        require(!(String(header,12,4,Charsets.US_ASCII) == "VP8X" && (header[20].toInt() and 2) != 0)) {
            "Escolha uma imagem estática, sem animação." }
    }
    var sample = 1
    while (maxOf(bounds.outWidth,bounds.outHeight) / sample > 256) sample *= 2
    val preview = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalArgumentException("A imagem está incompleta ou não pôde ser aberta.")
    preview.recycle()
}

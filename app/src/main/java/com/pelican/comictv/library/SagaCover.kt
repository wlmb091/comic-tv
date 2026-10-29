package com.pelican.comictv.library

import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

internal fun sagaCoverId(key: String): String = "saga-" + MessageDigest.getInstance("SHA-256")
    .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Bounds-only validation followed by a tiny decode; no full-resolution image retained in RAM. */
internal fun validateSagaCover(file: File) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    require(bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Escolha JPEG, PNG ou WebP estático." }
    require(bounds.outWidth in 64..12000 && bounds.outHeight in 64..12000 &&
        bounds.outWidth.toLong() * bounds.outHeight <= 48_000_000) { "Imagem inválida ou acima de 48 megapixels." }
    if (bounds.outMimeType == "image/webp") {
        val header = ByteArray(21)
        java.io.DataInputStream(file.inputStream()).use { it.readFully(header) }
        require(!(String(header, 12, 4, Charsets.US_ASCII) == "VP8X" && (header[20].toInt() and 2) != 0)) {
            "Escolha uma imagem estática, sem animação." }
    }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
    val preview = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalArgumentException("A imagem está incompleta ou não pôde ser aberta.")
    preview.recycle()
}

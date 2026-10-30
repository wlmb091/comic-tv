package com.pelican.comictv.archive

import java.io.File
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.util.zip.ZipFile

/** Keep existing UTF-8 keys; older unflagged ZIP names use the ZIP standard's CP437. */
fun openComicZip(file: File, maxEntries: Int = 5_000): ZipFile {
    fun open(charset: Charset): ZipFile {
        val zip = ZipFile(file, charset)
        try {
            // Android may decode names lazily, while desktop Java validates them in the constructor.
            val entries = zip.entries()
            var count = 0
            while (entries.hasMoreElements()) {
                if (Thread.currentThread().isInterrupted) throw java.io.IOException("Importação cancelada.")
                if (++count > maxEntries) throw ComicArchiveException("O arquivo contém entradas demais.")
                entries.nextElement().name
            }
            return zip
        } catch (failure: Exception) {
            zip.close()
            throw failure
        }
    }
    return try { open(Charsets.UTF_8) }
    catch (failure: Exception) {
        val nameDecodingFailure = generateSequence<Throwable>(failure) { it.cause }.take(8).any {
            it is CharacterCodingException ||
                it.message.orEmpty().contains("MALFORMED", ignoreCase = true) ||
                it.message.orEmpty().contains("bad entry name", ignoreCase = true)
        }
        if (!nameDecodingFailure) throw failure
        // ZIP entries explicitly marked UTF-8 remain UTF-8 even with this fallback.
        open(Charset.forName("IBM437"))
    }
}

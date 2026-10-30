package com.pelican.comictv.explorer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pelican.comictv.archive.ProtectedArchiveNormalizer
import com.pelican.comictv.archive.SelectedArchiveParts
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Caller deletes the returned private directory after the ordinary importer has copied the CBZ. */
internal object ProtectedArchiveImport {
    suspend fun prepare(context: Context, uris: List<Uri>, password: CharArray): File = runInterruptible(Dispatchers.IO) {
        require(uris.isNotEmpty() && uris.size <= 100) { "Escolha até 100 partes." }
        val directory = File(context.cacheDir, "protected-imports/${UUID.randomUUID()}").apply { mkdirs() }
        val sources = File(directory, "sources").apply { mkdirs() }
        try {
            var total = 0L
            val selected = uris.distinct().map { uri ->
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: error("Nome do arquivo indisponível.")
                require(name.length <= 240 && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' }) { "Nome de arquivo inválido." }
                val file = File(sources, name)
                require(!file.exists()) { "Partes com nomes repetidos." }
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                        val n = input.read(buffer); if (n < 0) break
                        total += n
                        require(total <= 1024L*1024*1024) { "O conjunto excede 1 GiB." }
                        check(directory.usableSpace >= 64L*1024*1024+n) { "Espaço insuficiente." }
                        output.write(buffer, 0, n)
                    }
                } } ?: error("Arquivo indisponível.")
                file
            }
            val ordered = SelectedArchiveParts.ordered(selected)
            val input = if (ordered.size > 1 || ordered[0].name.endsWith(".001"))
                SelectedArchiveParts.join(ordered, File(directory,"joined.archive")) else ordered[0]
            val result = ProtectedArchiveNormalizer.normalize(input, File(directory,"prepared.cbz"), password)
            selected.forEach { it.delete() }; if (input != selected.first()) input.delete()
            sources.delete()
            result
        } catch (failure: Exception) { directory.deleteRecursively(); throw failure }
        finally { password.fill('\u0000') }
    }
}

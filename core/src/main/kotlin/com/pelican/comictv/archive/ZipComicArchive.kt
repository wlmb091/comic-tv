package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import java.io.File
import java.io.InputStream
import java.io.IOException

data class ArchiveLimits(
    val maxEntries: Int = 5_000,
    val maxPageBytes: Long = 32L * 1024 * 1024,
    val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxArchiveBytes: Long = 1024L * 1024 * 1024,
)

class ComicArchiveException(message: String, cause: Throwable? = null) : ComicImportException(message, cause)

class ZipComicArchive(private val limits: ArchiveLimits = ArchiveLimits()) : ComicArchiveReader {
    override fun index(file: File): List<PageRef> = guarded {
        if (file.length() > limits.maxArchiveBytes) throw ComicArchiveException("O arquivo excede o limite de 1 GiB.")
        val signature = file.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
        if (signature[0] != 0x50.toByte() || signature[1] != 0x4b.toByte()) {
            throw ComicArchiveException("O arquivo não é um CBZ/ZIP válido.")
        }
        openComicZip(file, limits.maxEntries).use { zip ->
            val names = ArrayList<String>()
            val seen = HashSet<String>()
            val entries = zip.entries()
            var count = 0
            var total = 0L
            var containsComicFiles = false
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (++count > limits.maxEntries) throw ComicArchiveException("O arquivo contém entradas demais.")
                validateName(entry.name)
                if (!seen.add(entry.name)) throw ComicArchiveException("O arquivo contém páginas com nomes duplicados.")
                if (entry.isDirectory || entry.name.split('/').any { it == "__MACOSX" || it.startsWith(".") }) continue
                if (entry.name.substringAfterLast('.', "").lowercase() in setOf("cbz", "zip")) containsComicFiles = true
                if (entry.name.substringAfterLast('.', "").lowercase() !in setOf("jpg", "jpeg", "png", "webp")) continue
                if (entry.size > limits.maxPageBytes) throw ComicArchiveException("Uma página excede o limite permitido.")
                total += entry.size.coerceAtLeast(0)
                if (total > limits.maxTotalBytes) throw ComicArchiveException("O conteúdo descompactado é grande demais.")
                names.add(entry.name)
            }
            if (names.isEmpty()) {
                if (containsComicFiles) throw ComicArchiveException("Este ZIP contém outros arquivos CBZ/ZIP, não as páginas da HQ. Envie os arquivos CBZ/ZIP diretamente, sem compactá-los novamente.")
                throw ComicArchiveException("Nenhuma página JPEG, PNG ou WebP foi encontrada.")
            }
            names.sortedWith(NaturalOrder).mapIndexed { i, key -> PageRef(i, key) }
        }
    }

    override fun extract(file: File, page: PageRef, destination: File): File = guarded {
        validateName(page.key)
        openComicZip(file, limits.maxEntries).use { zip ->
            val entry = zip.getEntry(page.key) ?: throw ComicArchiveException("Página não encontrada no arquivo.")
            zip.getInputStream(entry).use { copyLimited(it, destination, limits.maxPageBytes) }
        }
    }

    private fun validateName(name: String) {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith('/') || ':' in normalized || normalized.split('/').any { it == ".." } || '\u0000' in normalized) {
            throw ComicArchiveException("O arquivo contém um caminho de página inválido.")
        }
    }

    private inline fun <T> guarded(block: () -> T): T = try { block() }
    catch (e: ComicArchiveException) { throw e }
    catch (e: IOException) { throw ComicArchiveException("Não foi possível ler o arquivo. Ele pode estar corrompido ou protegido por senha.", e) }
    catch (e: IllegalArgumentException) { throw ComicArchiveException("O arquivo ZIP não é compatível.", e) }
}

fun copyLimited(input: InputStream, destination: File, limit: Long): File {
    val temporary = File(destination.parentFile, destination.name + ".part")
    try {
        destination.parentFile?.mkdirs()
        temporary.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                if (Thread.currentThread().isInterrupted) throw IOException("Transferência cancelada.")
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) throw ComicArchiveException("O arquivo excede o limite permitido.")
                output.write(buffer, 0, count)
            }
        }
        if (!temporary.renameTo(destination)) throw IOException("Não foi possível concluir a gravação.")
        return destination
    } finally { temporary.delete() }
}

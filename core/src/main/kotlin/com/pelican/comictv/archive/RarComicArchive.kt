package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * RAR and RAR5 comics through [RarBackends.current] (libarchive in the app). Encrypted, split and linked entries are
 * refused; every size is checked before and while decoding.
 */
class RarComicArchive(private val limits: ArchiveLimits = ArchiveLimits(), private val backend: RarBackend = RarBackends.current) : ComicArchiveReader {
    internal fun collectionEntries(file: File): List<CollectionEntry> = guarded {
        open(file).second.filterNot { it.directory }.map { CollectionEntry(it.name, it.size) }
    }

    /** Validated headers in archive order, and whether the archive is solid. */
    private fun open(file: File): Pair<Boolean, List<RarEntry>> {
        if (file.length() > limits.maxArchiveBytes) throw ComicArchiveException("O arquivo excede o limite de 1 GiB.")
        val solid = preflightRar(file, limits)
        val headers = backend.entries(file, limits.maxEntries + 1)
        if (headers.size > limits.maxEntries) throw ComicArchiveException("O arquivo contém entradas demais.")
        val seen = HashSet<String>()
        var total = 0L
        for (header in headers) {
            checkInterrupted()
            if (header.encrypted) throw ComicArchiveException("RAR protegido por senha não é suportado. Converta para CBZ ou 7z no computador.")
            val name = header.name.replace('\\', '/')
            if (name.isEmpty() || name.startsWith('/') || ':' in name || '\u0000' in name || name.split('/').any { it == ".." })
                throw ComicArchiveException("O arquivo contém um caminho de página inválido.")
            if (!seen.add(name.trimEnd('/'))) throw ComicArchiveException("O arquivo contém páginas com nomes duplicados.")
            if (header.link || header.special) throw ComicArchiveException("O arquivo RAR contém links não suportados.")
            if (!header.directory) {
                val size = header.size
                if (!header.sizeKnown || size < 0 || size > limits.maxPageBytes)
                    throw ComicArchiveException("Uma entrada RAR excede o limite permitido ou tem tamanho desconhecido.")
                if (size > limits.maxTotalBytes - total) throw ComicArchiveException("O conteúdo descompactado é grande demais.")
                total += size
            }
        }
        return solid to headers
    }

    override fun index(file: File): List<PageRef> = guarded {
        val headers = open(file).second
        val pages = headers.filter { header ->
            val name = header.name.replace('\\', '/')
            !header.directory && name.split('/').none { it == "__MACOSX" || it.startsWith('.') } &&
                name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }
        if (pages.isEmpty()) {
            if (headers.any { it.name.substringAfterLast('.', "").lowercase() in setOf("zip", "cbz", "rar", "cbr") })
                throw ComicArchiveException("Este RAR contém outros quadrinhos. Envie os arquivos diretamente, sem compactá-los novamente.")
            throw ComicArchiveException("Nenhuma página JPEG, PNG ou WebP foi encontrada.")
        }
        pages.map { it.name }.sortedWith(NaturalOrder).mapIndexed { i, key -> PageRef(i, key) }
    }

    override fun extract(file: File, page: PageRef, destination: File): File = guarded {
        val temporary = File(destination.parentFile, destination.name + ".part")
        try {
            val (solid, headers) = open(file)
            val target = headers.indexOfFirst { it.name == page.key && !it.directory }
            if (target < 0) throw ComicArchiveException("Página não encontrada no arquivo.")
            val expected = headers[target].size
            destination.parentFile?.mkdirs()
            temporary.outputStream().use { output ->
                // A solid archive decodes the earlier files first; each one is bounded by its declared size.
                val sizes = headers.filter { !it.directory }.map { minOf(it.size, limits.maxPageBytes) }
                val sink = LimitedSink(output, minOf(expected, limits.maxPageBytes), sizes)
                val size = backend.extract(file, target, solid, sink)
                if (size != expected || sink.count != expected) throw ComicArchiveException("A página RAR está incompleta.")
            }
            if (!temporary.renameTo(destination)) throw IOException("Não foi possível concluir a gravação.")
            destination
        } finally { temporary.delete() }
    }

    /** Target bytes go to [output] up to [limit]; replayed bytes are bounded by the total of the declared sizes. */
    private class LimitedSink(private val output: OutputStream, private val limit: Long, sizes: List<Long>) : RarSink {
        private val replayLimit = sizes.fold(0L) { a, b -> if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b }
        var count = 0L
            private set
        private var replayed = 0L
        override fun chunk(target: Boolean, buffer: ByteArray, length: Int) {
            checkInterrupted()
            if (target) {
                if (length.toLong() > limit - count) throw ComicArchiveException("A página RAR excede o limite permitido.")
                output.write(buffer, 0, length)
                count += length
            } else {
                if (length.toLong() > replayLimit - replayed) throw ComicArchiveException("O conteúdo descompactado é grande demais.")
                replayed += length
            }
        }
    }

    private inline fun <T> guarded(block: () -> T): T = try { block() }
    catch (e: ComicArchiveException) { throw e }
    catch (e: Exception) {
        var cause: Throwable? = e
        repeat(10) {
            if (cause is ComicArchiveException) throw cause as ComicArchiveException
            cause = cause?.cause
        }
        throw ComicArchiveException("Não foi possível ler o RAR. Ele pode estar corrompido, incompleto ou protegido por senha.", e)
    }

    companion object {
        private fun checkInterrupted() {
            if (Thread.currentThread().isInterrupted) throw IOException("Leitura cancelada.")
        }
    }
}

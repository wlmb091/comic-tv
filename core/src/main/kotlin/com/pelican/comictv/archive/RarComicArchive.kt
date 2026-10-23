package com.pelican.comictv.archive

import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import com.github.junrar.rarfile.FileHeader
import com.github.junrar.rarfile.HostSystem
import com.github.junrar.rarfile.rar5.Rar5MainHeader
import com.github.junrar.volume.FileVolume
import com.github.junrar.volume.VolumeManager
import com.pelican.comictv.model.PageRef
import java.io.File
import java.io.IOException
import java.io.OutputStream

class RarComicArchive(private val limits: ArchiveLimits = ArchiveLimits()) : ComicArchiveReader {
    private fun open(file: File): Archive {
        if (file.length() > limits.maxArchiveBytes) throw ComicArchiveException("O arquivo excede o limite de 1 GiB.")
        preflightRar(file, limits)
        // File-backed random access, without whole-input caching or neighbour discovery.
        val volumes = VolumeManager { archive, previous -> if (previous == null) FileVolume(archive, file) else null }
        return Archive(volumes, ArchiveOptions.builder().maxDictionarySize(32L * 1024 * 1024).build())
    }

    private fun headers(archive: Archive): List<FileHeader> {
        if (archive.isPasswordProtected) throw ComicArchiveException("RAR protegido por senha não é suportado.")
        if (archive.hasBrokenHeaders()) throw ComicArchiveException("O arquivo RAR está corrompido ou incompleto.")
        if (archive.mainHeader?.isMultiVolume == true || archive.headers.filterIsInstance<Rar5MainHeader>().any { it.isVolume || it.isNextVolume })
            throw ComicArchiveException("RAR dividido em partes não é suportado. Envie um arquivo único.")
        val headers = archive.fileHeaders
        if (headers.size > limits.maxEntries) throw ComicArchiveException("O arquivo contém entradas demais.")
        val seen = HashSet<String>()
        var total = 0L
        for (header in headers) {
            checkInterrupted()
            val name = header.fileName.replace('\\', '/')
            if (name.startsWith('/') || ':' in name || '\u0000' in name || name.split('/').any { it == ".." })
                throw ComicArchiveException("O arquivo contém um caminho de página inválido.")
            if (!seen.add(name)) throw ComicArchiveException("O arquivo contém páginas com nomes duplicados.")
            if (header.isSplitBefore || header.isSplitAfter) throw ComicArchiveException("RAR dividido em partes não é suportado. Envie um arquivo único.")
            if (header.redirection != null || header.hostOS == HostSystem.unix && header.fileAttr and 0xf000 == 0xa000)
                throw ComicArchiveException("O arquivo RAR contém links não suportados.")
            if (!header.isDirectory) {
                val size = header.fullUnpackSize
                if (header.isUnpSizeUnknown || size < 0 || size > limits.maxPageBytes)
                    throw ComicArchiveException("Uma entrada RAR excede o limite permitido ou tem tamanho desconhecido.")
                if (size > limits.maxTotalBytes - total) throw ComicArchiveException("O conteúdo descompactado é grande demais.")
                total += size
                if (header.rar5WinSize > 32L * 1024 * 1024) throw ComicArchiveException("Este RAR exige memória demais para o leitor. Recompacte com dicionário de até 32 MiB.")
            }
        }
        return headers
    }

    override fun index(file: File): List<PageRef> = guarded {
        open(file).use { archive ->
            val headers = headers(archive)
            val pages = headers.filter { header ->
                val name = header.fileName.replace('\\', '/')
                !header.isDirectory && name.split('/').none { it == "__MACOSX" || it.startsWith('.') } &&
                    name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp")
            }
            if (pages.isEmpty()) {
                if (headers.any { it.fileName.substringAfterLast('.', "").lowercase() in setOf("zip", "cbz", "rar", "cbr") })
                    throw ComicArchiveException("Este RAR contém outros quadrinhos. Envie os arquivos diretamente, sem compactá-los novamente.")
                throw ComicArchiveException("Nenhuma página JPEG, PNG ou WebP foi encontrada.")
            }
            pages.map { it.fileName }.sortedWith(NaturalOrder).mapIndexed { i, key -> PageRef(i, key) }
        }
    }

    override fun extract(file: File, page: PageRef, destination: File): File = guarded {
        val temporary = File(destination.parentFile, destination.name + ".part")
        try {
            open(file).use { archive ->
                val headers = headers(archive)
                val target = headers.indexOfFirst { it.fileName == page.key && !it.isDirectory }
                if (target < 0) throw ComicArchiveException("Página não encontrada no arquivo.")
                destination.parentFile?.mkdirs()
                temporary.outputStream().use { output ->
                    // Replay solid predecessors explicitly, with byte and cancellation bounds.
                    // This avoids the decoder's unbounded implicit skip of previous entries.
                    val solid = archive.mainHeader?.isSolid == true || headers[target].isSolid
                    val first = if (solid) 0 else target
                    for (i in first..target) {
                        val header = headers[i]
                        if (header.isDirectory) continue
                        val sink = if (i == target) output else null
                        val bounded = LimitedOutput(sink, minOf(header.fullUnpackSize, limits.maxPageBytes))
                        archive.extractFile(header, bounded)
                        if (bounded.count != header.fullUnpackSize) throw ComicArchiveException("A página RAR está incompleta.")
                    }
                }
            }
            if (!temporary.renameTo(destination)) throw IOException("Não foi possível concluir a gravação.")
            destination
        } finally { temporary.delete() }
    }

    private class LimitedOutput(private val output: OutputStream?, private val limit: Long) : OutputStream() {
        var count = 0L
            private set
        override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            checkInterrupted()
            if (length.toLong() > limit - count) throw ComicArchiveException("A página RAR excede o limite permitido.")
            output?.write(bytes, offset, length)
            count += length
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
        throw ComicArchiveException("Não foi possível ler o RAR. Ele pode estar corrompido, incompleto, protegido por senha ou exigir memória demais.", e)
    }

    companion object {
        private fun checkInterrupted() {
            if (Thread.currentThread().isInterrupted) throw IOException("Leitura cancelada.")
        }
    }
}

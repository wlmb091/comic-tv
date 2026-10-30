package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import java.io.File

interface ComicArchiveReader {
    fun index(file: File): List<PageRef>
    fun extract(file: File, page: PageRef, destination: File): File
}

/** The extension is only a display name; bytes select the decoder. */
class ComicArchive(private val limits: ArchiveLimits = ArchiveLimits(), private val pdfReader: ComicArchiveReader? = null) : ComicArchiveReader {
    private fun reader(file: File): ComicArchiveReader {
        if (file.length() > limits.maxArchiveBytes) throw ComicArchiveException("O arquivo excede o limite de 1 GiB.")
        val format=ComicContainers.detect(file)
        if(format==ComicContainer.Pdf)return pdfReader ?: throw ComicArchiveException("A leitura de PDF requer o leitor Android.")
        if(format==ComicContainer.SevenZip)return SevenZComicArchive(limits)
        if(format==ComicContainer.Zip) {
            val epub = try { openComicZip(file, limits.maxEntries).use { it.getEntry("META-INF/container.xml") != null } }
                catch (e: Exception) { throw ComicArchiveException("Não foi possível ler o ZIP/EPUB. Ele pode estar corrompido.", e) }
            return if (epub) EpubComicArchive(limits) else ZipComicArchive(limits)
        }
        if(format==ComicContainer.Rar)return RarComicArchive(limits)
        throw ComicArchiveException(ComicContainers.failure(format))
    }
    override fun index(file: File) = reader(file).index(file)
    override fun extract(file: File, page: PageRef, destination: File) = reader(file).extract(file, page, destination)
}

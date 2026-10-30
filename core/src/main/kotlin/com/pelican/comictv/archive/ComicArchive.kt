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
        val magic = file.inputStream().use { input -> ByteArray(8).also { bytes ->
            var offset = 0
            while (offset < bytes.size) {
                val read = input.read(bytes, offset, bytes.size - offset)
                if (read < 0) break
                offset += read
            }
        } }
        if (magic.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-")
            return pdfReader ?: throw ComicArchiveException("A leitura de PDF requer o leitor Android.")
        if (magic[0] == 0x50.toByte() && magic[1] == 0x4b.toByte()) {
            val epub = try { openComicZip(file, limits.maxEntries).use { it.getEntry("META-INF/container.xml") != null } }
                catch (e: Exception) { throw ComicArchiveException("Não foi possível ler o ZIP/EPUB. Ele pode estar corrompido.", e) }
            return if (epub) EpubComicArchive(limits) else ZipComicArchive(limits)
        }
        if (magic.take(6) == listOf<Byte>(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07) &&
            (magic[6] == 0.toByte() || magic[6] == 1.toByte() && magic[7] == 0.toByte())) return RarComicArchive(limits)
        throw ComicArchiveException("Formato não reconhecido. Envie ZIP/CBZ, RAR/CBR, PDF ou EPUB.")
    }
    override fun index(file: File) = reader(file).index(file)
    override fun extract(file: File, page: PageRef, destination: File) = reader(file).extract(file, page, destination)
}

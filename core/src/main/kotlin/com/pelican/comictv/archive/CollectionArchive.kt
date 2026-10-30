package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import java.io.File

data class CollectionEntry(val name: String, val size: Long)

/** Only lists inner edition files. No paths from an archive are used as output paths. */
class CollectionArchive(
    private val maxComics: Int = 100,
    private val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024,
    private val maxEntries: Int = 5_000,
) {
    private val limits = ArchiveLimits(maxEntries = maxEntries, maxPageBytes = 1024L * 1024 * 1024,
        maxTotalBytes = maxTotalBytes)
    private val rar = RarComicArchive(limits)
    private val editions = setOf("cbz", "zip", "cbr", "rar", "pdf", "epub")
    private val images = setOf("jpg", "jpeg", "png", "webp")
    private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()
    private fun kind(file: File): Int {
        if (file.length() > limits.maxArchiveBytes) throw ComicArchiveException("O arquivo excede o limite de 1 GiB.")
        val bytes = file.inputStream().use { input -> ByteArray(8).also { input.read(it) } }
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) return 1
        if (bytes.take(6) == listOf<Byte>(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07)) return 2
        return 0
    }
    fun inspect(file: File): List<CollectionEntry>? {
        val format = kind(file)
        if (format == 1) guardCollectionZip(file, maxEntries)
        val entries = when (format) {
            1 -> openComicZip(file, maxEntries).use { zip ->
                val result = mutableListOf<CollectionEntry>()
                val iterator = zip.entries()
                var count = 0
                while (iterator.hasMoreElements()) {
                    checkInterrupted()
                    val entry = iterator.nextElement()
                    if (++count > maxEntries) throw ComicArchiveException("A coleção contém entradas demais.")
                    // Directory names are validated too; sizes of every file count, even ignored files.
                    result += CollectionEntry(entry.name, if (entry.isDirectory) 0 else entry.size)
                }
                result
            }
            2 -> rar.collectionEntries(file)
            else -> return null
        }
        var total = 0L
        val seen = HashSet<String>()
        entries.forEach { entry ->
            val name = entry.name.replace('\\', '/')
            if (name.startsWith('/') || ':' in name || '\u0000' in name || name.split('/').any { it == ".." })
                throw ComicArchiveException("A coleção contém um caminho inválido.")
            if (!seen.add(name)) throw ComicArchiveException("A coleção contém nomes duplicados.")
            if (entry.size < 0 || entry.size > limits.maxPageBytes || entry.size > maxTotalBytes - total)
                throw ComicArchiveException("O conteúdo da coleção excede os limites permitidos.")
            total += entry.size
        }
        val visible = entries.filter { it.name.replace('\\', '/').split('/').none { part -> part == "__MACOSX" || part.startsWith('.') } }
        // Preserve ordinary image archives and EPUBs, including their optional attachments.
        if (visible.any { extension(it.name) in images } || seen.contains("META-INF/container.xml")) return null
        val comics = visible.filter { extension(it.name) in editions }
        if (comics.isEmpty()) return null
        if (comics.size > maxComics) throw ComicArchiveException("A coleção excede o limite de $maxComics HQs.")
        return comics.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    }
    fun extract(file: File, entry: CollectionEntry, destination: File): File {
        require(entry.size in 0..limits.maxPageBytes)
        try {
            checkInterrupted()
            when (kind(file)) {
                1 -> openComicZip(file, maxEntries).use { zip ->
                    val source = zip.getEntry(entry.name) ?: throw ComicArchiveException("Arquivo da coleção não encontrado.")
                    if (source.size != entry.size) throw ComicArchiveException("A coleção mudou durante a importação.")
                    zip.getInputStream(source).use { copyLimited(it, destination, entry.size) }
                }
                2 -> rar.extract(file, PageRef(0, entry.name), destination)
                else -> throw ComicArchiveException("Formato de coleção inválido.")
            }
            if (destination.length() != entry.size) throw ComicArchiveException("Arquivo da coleção incompleto.")
            return destination
        } catch (e: Exception) { destination.delete(); throw e }
    }
    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw java.io.IOException("Importação cancelada.")
    }
}

package com.pelican.comictv.archive

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import net.lingala.zip4j.ZipFile

/** Creates an ordinary CBZ copy; original bytes and password are never written or changed. */
object ProtectedArchiveNormalizer {
    fun normalize(input: File, output: File, password: CharArray, limits: ArchiveLimits = ArchiveLimits()): File {
        require(input.canonicalFile != output.canonicalFile)
        require(password.size <= 1024) { "Senha longa demais." }
        val secret = password.copyOf()
        val temporary = File(output.parentFile, output.name + ".part")
        try {
            require(input.length() <= limits.maxArchiveBytes) { "Arquivo maior que 1 GiB." }
            output.parentFile?.mkdirs()
            ZipOutputStream(PreparationOutput(temporary, limits.maxArchiveBytes)).use { target ->
                when (ComicContainers.detect(input)) {
                    ComicContainer.Zip -> fromZip(input, target, secret, limits)
                    ComicContainer.Rar, ComicContainer.SevenZip -> {
                        val reader: ComicArchiveReader = if (ComicContainers.detect(input) == ComicContainer.Rar)
                            RarComicArchive(limits, secret) else SevenZComicArchive(limits, secret)
                        val pageFile = File(output.parentFile, output.name + ".page")
                        try {
                            for (page in reader.index(input)) {
                                reader.extract(input, page, pageFile)
                                append(target, page.index, page.key, pageFile.inputStream(), pageFile.length(), limits)
                                pageFile.delete()
                            }
                        } finally { pageFile.delete() }
                    }
                    else -> throw ComicArchiveException("Escolha ZIP/CBZ, RAR/CBR ou 7z/CB7 sem DRM.")
                }
            }
            require(temporary.length() <= limits.maxArchiveBytes) { "O CBZ preparado excede 1 GiB." }
            check(!output.exists() && temporary.renameTo(output)) { "Não foi possível concluir a preparação." }
            return output
        } catch (failure: Exception) {
            throw ComicArchiveException("Não foi possível preparar: senha incorreta, partes ausentes, arquivo inválido ou proteção não suportada. " + (failure.message ?: ""), failure)
        } finally { secret.fill('\u0000'); password.fill('\u0000'); temporary.delete() }
    }
    private class PreparationOutput(private val file: File, private val maximum: Long) : java.io.OutputStream() {
        private val delegate = file.outputStream()
        private var count = 0L
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            require(length <= maximum - count) { "O CBZ preparado excede 1 GiB." }
            check(file.parentFile.usableSpace >= 64L*1024*1024 + length) { "Espaço insuficiente." }
            delegate.write(bytes, offset, length); count += length
        }
        override fun close() = delegate.close()
    }
    private fun fromZip(input: File, target: ZipOutputStream, password: CharArray, limits: ArchiveLimits) {
        guardZipMetadata(input, limits.maxEntries)
        ZipFile(input, password).use { zip ->
            require(!zip.isSplitArchive) { "ZIP .z01 dividido ainda não é suportado; use partes sequenciais .001 de um arquivo único." }
            val headers = zip.fileHeaders
            require(headers.size <= limits.maxEntries)
            val seen = HashSet<String>(); var total = 0L
            headers.forEach { header ->
                val name = header.fileName.replace('\\', '/')
                require(name.length <= 4096 && !name.startsWith('/') && ':' !in name && '\u0000' !in name && name.split('/').none { it == ".." } && seen.add(name)) { "Caminho inválido ou duplicado." }
                val attributes = header.externalFileAttributes
                require(attributes.size < 4 || ((attributes[3].toInt() and 0xf0) != 0xa0)) { "Links não são suportados." }
                require(header.uncompressedSize in 0..limits.maxPageBytes && header.uncompressedSize <= limits.maxTotalBytes - total) { "Conteúdo excede os limites." }
                total += header.uncompressedSize
            }
            val pages = headers.filter { !it.isDirectory && it.fileName.replace('\\','/').split('/').none { n -> n.startsWith('.') || n == "__MACOSX" } && it.fileName.substringAfterLast('.').lowercase() in setOf("jpg","jpeg","png","webp") }.sortedWith { a,b -> NaturalOrder.compare(a.fileName,b.fileName) }
            require(pages.isNotEmpty()) { "Nenhuma página foi encontrada." }
            pages.forEachIndexed { index, entry -> append(target, index, entry.fileName, zip.getInputStream(entry), entry.uncompressedSize, limits) }
        }
    }
    private fun append(target: ZipOutputStream, index: Int, key: String, input: java.io.InputStream, expected: Long, limits: ArchiveLimits) {
        input.use { source ->
            target.putNextEntry(ZipEntry("%05d.%s".format(index, key.substringAfterLast('.').lowercase())))
            var count = 0L; val buffer = ByteArray(64 * 1024)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                val n = source.read(buffer); if (n < 0) break
                require(n <= limits.maxPageBytes - count) { "Página grande demais." }
                target.write(buffer, 0, n); count += n
            }
            require(count == expected) { "Página incompleta." }
            target.closeEntry()
        }
    }
}

/** Bound the central directory before a ZIP library allocates its entry objects. ZIP64 is rejected. */
fun guardZipMetadata(file: File, maxEntries: Int) {
    RandomAccessFile(file, "r").use { input ->
        require(input.length() >= 22) { "ZIP incompleto." }
        val size = minOf(input.length(), 65_557).toInt(); val tail = ByteArray(size)
        input.seek(input.length()-size); input.readFully(tail)
        fun u16(at: Int) = (tail[at].toInt() and 255) or ((tail[at+1].toInt() and 255) shl 8)
        fun u32(at: Int) = (0..3).fold(0L) { n,i -> n or ((tail[at+i].toLong() and 255) shl (8*i)) }
        val at = (size-22 downTo 0).firstOrNull { i -> u32(i)==0x06054b50L && i+22+u16(i+20)==size } ?: error("ZIP incompleto.")
        require(u16(at+4)==0 && u16(at+6)==0 && u16(at+8)==u16(at+10)) { "ZIP dividido .z01 não é suportado." }
        require(u16(at+10) in 1..maxEntries && u16(at+10)!=65535) { "ZIP contém entradas demais." }
        require(u32(at+12)<=8L*1024*1024 && u32(at+16)<=input.length() && u32(at+12)<=input.length()-u32(at+16)) { "Metadados ZIP excedem o limite." }
    }
}

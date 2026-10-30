package com.pelican.comictv.archive

import java.io.File
import java.io.InterruptedIOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** New outputs only. Does not re-encode images or execute extracted files. */
class ArchiveTools(private val check: () -> Unit = { if (Thread.currentThread().isInterrupted) throw InterruptedIOException() }) {
    private val archive = CollectionArchive()
    fun list(input: File): List<CollectionEntry> {
        check()
        val entries = archive.contents(input)
        require(entries.none { it.name.replace('\\','/').trimStart('/') == "AndroidManifest.xml" }) { "APK deve ser instalado pelo Android, não descompactado como quadrinho." }
        return entries.sortedWith { a,b -> NaturalOrder.compare(a.name,b.name) }
    }
    fun extract(input: File, destination: File, progress: (Int,Int) -> Unit = {_,_->}): File {
        require(!destination.exists()) { "Escolha uma nova pasta de saída." }
        val entries = list(input)
        require(destination.mkdirs()) { "Não foi possível criar a pasta de saída." }
        try {
            entries.forEachIndexed { i,entry ->
                check(); space(destination, entry.size)
                val target = File(destination,entry.name.replace('\\','/'))
                require(ExplorerFiles.inside(destination,target) && target.canonicalFile != destination.canonicalFile) { "Caminho inválido no arquivo." }
                if (entry.directory) require(target.isDirectory || target.mkdirs())
                else {
                    target.parentFile?.mkdirs()
                    require(!target.exists()) { "O arquivo contém nomes que se sobrepõem." }
                    archive.extract(input,entry,target)
                }
                progress(i+1,entries.size)
            }
            return destination
        } catch(error: Exception) { destination.deleteRecursively(); throw error }
    }
    fun convert(input: File, destination: File, progress: (Int,Int) -> Unit = {_,_->}): File {
        require(!destination.exists()) { "O arquivo de saída já existe." }
        val entries=list(input).associateBy {it.name}
        val reader=ComicArchive(); val pages=reader.index(input)
        val parent=requireNotNull(destination.absoluteFile.parentFile).apply { mkdirs() }
        val scratch=File(parent,".convert-${UUID.randomUUID()}").apply { check(mkdir()) }
        val pending=File(scratch,"output.cbz")
        try {
            ZipOutputStream(pending.outputStream()).use { zip ->
                zip.setLevel(0)
                for((i,page) in pages.withIndex()) {
                    check(); space(parent,32L*1024*1024)
                    val image=File(scratch,"page")
                    try {
                        archive.extract(input,requireNotNull(entries[page.key]),image)
                        val ext=page.key.substringAfterLast('.').lowercase(java.util.Locale.ROOT)
                        zip.putNextEntry(ZipEntry("%05d.%s".format(java.util.Locale.ROOT,i+1,ext)))
                        image.inputStream().use { source ->
                            val buffer=ByteArray(64*1024)
                            while(true) { check(); val count=source.read(buffer);if(count<0)break;space(parent,count.toLong());zip.write(buffer,0,count) }
                        }
                        zip.closeEntry()
                        check(pending.length()<=1024L*1024*1024) { "O CBZ convertido excede 1 GiB." }
                    } finally { image.delete() }
                    progress(i+1,pages.size)
                }
            }
            check(); check(pending.length()<=1024L*1024*1024)
            require(pending.renameTo(destination)) { "Não foi possível salvar o CBZ." }
            return destination
        } finally { scratch.deleteRecursively() }
    }
    private fun space(directory: File, bytes: Long) {
        check(directory.usableSpace>=64L*1024*1024+bytes) { "Espaço insuficiente; mantenha 64 MiB livres além do resultado." }
    }
}

fun copyArchiveToolInput(input: java.io.InputStream, destination: File, limit: Long = 1024L*1024*1024,
    available: ()->Long = {destination.absoluteFile.parentFile.usableSpace}) {
    require(!destination.exists())
    try {
        destination.outputStream().use {out->
            val buffer=ByteArray(64*1024);var size=0L
            while(true){
                if(Thread.currentThread().isInterrupted)throw InterruptedIOException()
                val n=input.read(buffer);if(n<0)break
                size+=n;check(size<=limit){"O arquivo excede 1 GiB."}
                check(available()>=64L*1024*1024+n){"Espaço insuficiente; mantenha 64 MiB livres."}
                out.write(buffer,0,n)
            }
        }
    } catch(error:Exception){destination.delete();throw error}
}

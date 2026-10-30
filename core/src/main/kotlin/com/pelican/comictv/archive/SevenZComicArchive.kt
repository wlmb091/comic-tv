package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream

/** File-backed 7z, including misnamed CBR files; never extracts archive paths to disk. */
class SevenZComicArchive(private val limits: ArchiveLimits = ArchiveLimits()) : ComicArchiveReader {
    private fun open(file: File): SevenZFile {
        if(file.length()>limits.maxArchiveBytes)throw ComicArchiveException("O arquivo excede o limite de 1 GiB.")
        RandomAccessFile(file,"r").use { input ->
            if(input.length()<32)throw ComicArchiveException("O arquivo 7z está incompleto.")
            val h=ByteArray(32);input.readFully(h)
            if(ComicContainers.detect(file)!=ComicContainer.SevenZip)throw ComicArchiveException("O arquivo não é um 7z válido.")
            fun uint(at:Int,n:Int):Long=(0 until n).fold(0L) { v,i->v or ((h[at+i].toLong() and 255) shl (8*i)) }
            val crc=CRC32().apply { update(h,12,20) }.value
            if(crc!=uint(8,4))throw ComicArchiveException("O cabeçalho 7z está corrompido ou incompleto.")
            val offset=uint(12,8);val size=uint(20,8)
            if(offset<0||size<0||size>8L*1024*1024||offset>input.length()-32||size>input.length()-32-offset)
                throw ComicArchiveException("O arquivo 7z está incompleto ou tem metadados demais.")
            val nextHeader=ByteArray(size.toInt())
            input.seek(32+offset);input.readFully(nextHeader)
            if(CRC32().apply { update(nextHeader) }.value!=uint(28,4))
                throw ComicArchiveException("Os metadados 7z estão corrompidos ou incompletos.")
            guardEncodedSevenZHeader(nextHeader)
        }
        val channel=InterruptibleChannel(file)
        return try { SevenZFile.builder().setSeekableByteChannel(channel).setMaxMemoryLimitKiB(32*1024)
            .setTryToRecoverBrokenArchives(false).get() } catch(e:Exception) { channel.close();throw e }
    }
    private fun entries(archive: SevenZFile):List<SevenZArchiveEntry> {
        val entries=ArrayList<SevenZArchiveEntry>();val seen=HashSet<String>();var total=0L
        for(entry in archive.entries) {
            interrupted()
            if(entries.size>=limits.maxEntries)throw ComicArchiveException("O arquivo contém entradas demais.")
            val name=entry.name?:throw ComicArchiveException("O 7z contém uma entrada sem nome.")
            val normalized=name.replace('\\','/')
            if(name.length>4096||normalized.startsWith('/')||':' in normalized||'\u0000' in name||normalized.split('/').any { it==".." })
                throw ComicArchiveException("O arquivo contém um caminho de página inválido.")
            if(!seen.add(normalized))throw ComicArchiveException("O arquivo contém nomes duplicados.")
            if(entry.isAntiItem || entry.hasWindowsAttributes && (entry.windowsAttributes and 0x400 != 0 || (entry.windowsAttributes ushr 16) and 0xf000 == 0xa000))
                throw ComicArchiveException("O 7z contém links ou entradas não suportadas.")
            if(entry.size<0||entry.size>limits.maxPageBytes||entry.size>limits.maxTotalBytes-total)
                throw ComicArchiveException("O conteúdo 7z excede os limites permitidos.")
            total+=entry.size;entries+=entry
        }
        return entries
    }
    fun entryNames(file:File):List<String> = collectionEntries(file).map { it.name }
    internal fun collectionEntries(file:File):List<CollectionEntry> = guarded { open(file).use { archive ->
        entries(archive).map { CollectionEntry(it.name,if(it.isDirectory)0 else it.size,it.isDirectory) }
    } }
    override fun index(file:File):List<PageRef> = guarded { open(file).use { archive ->
        val pages=entries(archive).filter { entry -> !entry.isDirectory && entry.name.replace('\\','/').split('/').none { it=="__MACOSX"||it.startsWith('.') } && entry.name.substringAfterLast('.',"").lowercase() in setOf("jpg","jpeg","png","webp") }
        if(pages.isEmpty())throw ComicArchiveException("Nenhuma página JPEG, PNG ou WebP foi encontrada no 7z.")
        pages.map { it.name }.sortedWith(NaturalOrder).mapIndexed { i,key->PageRef(i,key) }
    } }
    override fun extract(file:File,page:PageRef,destination:File):File = guarded {
        val temp=File(destination.parentFile,destination.name+".decoded")
        try { open(file).use { archive ->
            val entry=entries(archive).firstOrNull { it.name==page.key&&!it.isDirectory }?:throw ComicArchiveException("Página não encontrada no arquivo.")
            val crc=CRC32()
            archive.getInputStream(entry).use { stream -> CheckedInputStream(stream,crc).use { copyLimited(it,temp,limits.maxPageBytes) } }
            if(temp.length()!=entry.size || entry.hasCrc && crc.value!=entry.crcValue)throw ComicArchiveException("A página 7z está corrompida ou incompleta.")
        }
        if(!temp.renameTo(destination))throw IOException("Não foi possível concluir a gravação.")
        destination
        } finally { temp.delete();File(temp.parentFile,temp.name+".part").delete() }
    }
    private inline fun<T> guarded(block:()->T):T=try { block() }
    catch(e:ComicArchiveException) { throw e }
    catch(e:PasswordRequiredException) { throw ComicArchiveException("7z protegido por senha não é suportado.",e) }
    catch(e:MemoryLimitException) { throw ComicArchiveException("A compressão 7z exige mais memória que o limite deste aparelho (32 MiB).",e) }
    catch(e:IOException) { throw ComicArchiveException("Não foi possível ler o 7z. Ele pode estar incompleto, corrompido ou usar compressão não suportada.",e) }
    private class InterruptibleChannel(file:File):SeekableByteChannel {
        private val delegate=Files.newByteChannel(file.toPath(),StandardOpenOption.READ)
        override fun read(dst:ByteBuffer):Int { interrupted();return delegate.read(dst) }
        override fun write(src:ByteBuffer):Int=throw UnsupportedOperationException()
        override fun position():Long=delegate.position()
        override fun position(newPosition:Long):SeekableByteChannel { interrupted();delegate.position(newPosition);return this }
        override fun size():Long=delegate.size()
        override fun truncate(size:Long):SeekableByteChannel=throw UnsupportedOperationException()
        override fun isOpen():Boolean=delegate.isOpen
        override fun close()=delegate.close()
    }
    companion object { private fun interrupted() { if(Thread.currentThread().isInterrupted)throw IOException("Leitura cancelada.") } }
}

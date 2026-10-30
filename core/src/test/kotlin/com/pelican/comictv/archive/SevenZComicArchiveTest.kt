package com.pelican.comictv.archive

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SevenZComicArchiveTest {
    private fun fixture(root: File, vararg names: String): File = File(root,"misnamed.cbr").also { file ->
        SevenZOutputFile(file).use { out ->
            out.setContentCompression(SevenZMethod.COPY)
            names.forEach { name ->
                val entry = SevenZArchiveEntry().apply { this.name = name }
                out.putArchiveEntry(entry); out.write(byteArrayOf(1,2,3)); out.closeArchiveEntry()
            }
        }
    }
    @Test fun detectsMisnamedSevenZipAndExtractsPagesInNaturalOrder() {
        val root=Files.createTempDirectory("sevenzip").toFile()
        try {
            val f=fixture(root,"Máskara 10.jpg","Máskara 2.png")
            assertNull(CollectionArchive().inspect(f))
            val a=ComicArchive();val pages=a.index(f)
            assertEquals(listOf("Máskara 2.png","Máskara 10.jpg"),pages.map { it.key })
            for(p in pages.reversed())assertArrayEquals(byteArrayOf(1,2,3),a.extract(f,p,File(root,"page-${p.index}")).readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun detectsSevenZipCollectionsAndPreservesBoundsAndPaths() {
        val root=Files.createTempDirectory("sevenzip").toFile()
        try {
            val f=fixture(root,"Saga 2.cbz","Saga 10.cbr")
            val a=CollectionArchive();val entries=a.inspect(f)!!
            assertEquals(listOf("Saga 2.cbz","Saga 10.cbr"),entries.map { it.name })
            assertArrayEquals(byteArrayOf(1,2,3),a.extract(f,entries.first(),File(root,"edition")).readBytes())
            assertThrows(ComicArchiveException::class.java) { CollectionArchive(maxComics=1).inspect(f) }
            fixture(root,"../Máskara.jpg")
            assertThrows(ComicArchiveException::class.java) { ComicArchive().index(f) }
            assertThrows(ComicArchiveException::class.java) { a.inspect(f) }
            fixture(root,"1.jpg","2.jpg")
            assertThrows(ComicArchiveException::class.java) { ComicArchive(ArchiveLimits(maxEntries=1)).index(f) }
            assertThrows(ComicArchiveException::class.java) { ComicArchive(ArchiveLimits(maxPageBytes=2)).index(f) }
        } finally { root.deleteRecursively() }
    }
    @Test fun distinguishesHtmlEmptyAndIncompleteDownloads() {
        val root=Files.createTempDirectory("format").toFile()
        try {
            val f=File(root,"book.cbr")
            for((bytes,word) in listOf("<html>Forbidden</html>".toByteArray() to "site",byteArrayOf() to "vazio",byteArrayOf(0x37,0x7a,0xbc.toByte()) to "incompleto")) {
                f.writeBytes(bytes)
                val e=assertThrows(ComicArchiveException::class.java) { ComicArchive().index(f) }
                assertTrue(e.message.orEmpty(),e.message.orEmpty().contains(word,true))
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsExpandedEncodedHeadersBeforeOpeningDecoder() {
        val root=Files.createTempDirectory("seven-header").toFile()
        try {
            val packed=java.io.ByteArrayOutputStream()
            fun b(v:Int)=packed.write(v)
            fun number(v:Long) { b(255);repeat(8) { b((v ushr (it*8)).toInt() and 255) } }
            b(0x17);b(6);number(0);number(1);b(9);number(1);b(0)
            b(7);b(0x0b);number(1);b(0);number(1);b(1);b(0);b(0x0c);number(64L*1024*1024);b(0);b(0)
            val header=packed.toByteArray()
            val start=java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            start.put(byteArrayOf(55,122,0xbc.toByte(),0xaf.toByte(),39,28,0,4));start.putInt(0)
            start.putLong(1);start.putLong(header.size.toLong());start.putInt(java.util.zip.CRC32().apply { update(header) }.value.toInt())
            val bytes=start.array();java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(8,java.util.zip.CRC32().apply { update(bytes,12,20) }.value.toInt())
            val f=File(root,"header.7z");f.writeBytes(bytes+byteArrayOf(0)+header)
            val e=assertThrows(ComicArchiveException::class.java) { ComicArchive().index(f) }
            assertTrue(e.message.orEmpty(),e.message.orEmpty().contains("metadados"))
        } finally { root.deleteRecursively() }
    }
    @Test fun corruptPageLeavesNoOutputOrTemporaryFile() {
        val root=Files.createTempDirectory("seven-crc").toFile()
        try {
            val f=fixture(root,"1.jpg");val a=ComicArchive();val page=a.index(f).single()
            val bytes=f.readBytes();bytes[32]=(bytes[32].toInt() xor 1).toByte();f.writeBytes(bytes)
            val out=File(root,"out")
            assertThrows(ComicArchiveException::class.java) { a.extract(f,page,out) }
            assertFalse(out.exists());assertFalse(File(root,"out.decoded").exists());assertFalse(File(root,"out.decoded.part").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun oversizedDictionaryAndInterruptionDoNotLeavePartialPages() {
        val root=Files.createTempDirectory("seven-memory").toFile()
        try {
            val f=File(root,"dict.7z")
            SevenZOutputFile(f).use { out ->
                out.setContentMethods(listOf(org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration(SevenZMethod.LZMA2,org.tukaani.xz.LZMA2Options().apply { dictSize=1024*1024 })))
                out.putArchiveEntry(SevenZArchiveEntry().apply { name="1.jpg" });out.write(byteArrayOf(1,2,3));out.closeArchiveEntry()
            }
            val bytes=f.readBytes();val b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val start=32+b.getLong(12).toInt();val length=b.getLong(20).toInt()
            val property=(start until bytes.size-3).first { bytes[it]==0x21.toByte()&&bytes[it+1]==0x21.toByte()&&bytes[it+2]==1.toByte() }
            bytes[property+3]=28
            b.putInt(28,java.util.zip.CRC32().apply { update(bytes,start,length) }.value.toInt())
            b.putInt(8,java.util.zip.CRC32().apply { update(bytes,12,20) }.value.toInt());f.writeBytes(bytes)
            val a=ComicArchive();val page=a.index(f).single();val dest=File(root,"page")
            val e=assertThrows(ComicArchiveException::class.java) { a.extract(f,page,dest) }
            assertTrue(e.message.orEmpty(),e.message.orEmpty().contains("32 MiB"));assertFalse(dest.exists())
            val normal=fixture(root,"1.jpg");val normalPage=a.index(normal).single()
            try {
                Thread.currentThread().interrupt()
                assertThrows(ComicArchiveException::class.java) { a.extract(normal,normalPage,dest) }
            } finally { Thread.interrupted() }
            assertFalse(dest.exists());assertFalse(File(root,"page.decoded").exists());assertFalse(File(root,"page.decoded.part").exists())
        } finally { root.deleteRecursively() }
    }

}

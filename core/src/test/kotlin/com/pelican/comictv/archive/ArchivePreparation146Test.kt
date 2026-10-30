package com.pelican.comictv.archive

import java.io.File
import java.nio.file.Files
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.*
import org.junit.Test

class ArchivePreparation146Test {
    @Test fun encryptedZipRequiresCorrectPasswordAndPreservesOriginal() {
        val root = Files.createTempDirectory("protected-test").toFile()
        try {
            val page = File(root, "001.jpg").apply { writeBytes(byteArrayOf(1,2,3,4)) }
            val input = File(root, "original.cbz")
            ZipFile(input,"secret".toCharArray()).use { zip ->
                zip.addFile(page, ZipParameters().apply { isEncryptFiles=true; encryptionMethod=EncryptionMethod.AES })
            }
            val original = input.readBytes()
            val wrong = "wrong".toCharArray()
            assertThrows(ComicArchiveException::class.java) { ProtectedArchiveNormalizer.normalize(input,File(root,"wrong.cbz"),wrong) }
            assertTrue(wrong.all { it=='\u0000' })
            assertFalse(File(root,"wrong.cbz").exists())
            val secret = "secret".toCharArray()
            val result=ProtectedArchiveNormalizer.normalize(input,File(root,"ready.cbz"),secret)
            assertTrue(secret.all { it=='\u0000' })
            openComicZip(result).use { zip -> assertArrayEquals(page.readBytes(),zip.getInputStream(zip.entries().nextElement()).readBytes()) }
            assertArrayEquals(original,input.readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun partsRequireExactContiguousSelectedSet() {
        val root=Files.createTempDirectory("parts-test").toFile()
        try {
            val first=File(root,"comic.7z.001").apply { writeText("first") }
            val second=File(root,"comic.7z.002").apply { writeText("second") }
            assertEquals(listOf(first,second),SelectedArchiveParts.ordered(listOf(second,first)))
            assertThrows(IllegalArgumentException::class.java) { SelectedArchiveParts.ordered(listOf(second)) }
            val other=File(root,"other.7z.002").apply { writeText("other") }
            assertThrows(IllegalArgumentException::class.java) { SelectedArchiveParts.ordered(listOf(first,other)) }
            assertEquals("firstsecond",SelectedArchiveParts.join(listOf(second,first),File(root,"joined")).readText())
        } finally { root.deleteRecursively() }
    }
    private fun entry(key:String,split:String?=null,abi:Set<String> = emptySet(),signer:Set<String> = setOf("signed"))=
        ApkSetEntry(key,"org.example.test",42,signer,split,abi)
    @Test fun apkSetRequiresBaseSameIdentityAndSelectsDeviceAbi() {
        val base=entry("base");val arm=entry("arm","config.arm64_v8a",setOf("arm64-v8a"));val x86=entry("x86","config.x86",setOf("x86"))
        assertEquals(listOf(base,arm),ApkSetPolicy.select(listOf(base,arm,x86),listOf("arm64-v8a","armeabi-v7a")))
        assertThrows(IllegalStateException::class.java) { ApkSetPolicy.select(listOf(arm),listOf("arm64-v8a")) }
        assertThrows(IllegalArgumentException::class.java) { ApkSetPolicy.select(listOf(base,entry("bad","bad",signer=setOf("other"))),listOf("arm64-v8a")) }
        assertThrows(IllegalArgumentException::class.java) { ApkSetPolicy.select(listOf(base,x86),listOf("arm64-v8a")) }
    }
    @Test fun malformedBinaryManifestDoesNotBecomeBase() {
        assertThrows(IllegalArgumentException::class.java) { ApkManifestSplit.read(ByteArray(40)) }
    }
    @Test fun sevenZipPasswordAndKdfCostAreBounded() {
        val root=Files.createTempDirectory("seven-password").toFile()
        try {
            val page=File(root,"001.jpg").apply { writeBytes(byteArrayOf(4,3,2,1)) }
            val archive=File(root,"protected.7z")
            org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(archive,"secret".toCharArray()).use { out ->
                out.putArchiveEntry(out.createArchiveEntry(page,"001.jpg"));out.write(page.readBytes());out.closeArchiveEntry()
            }
            val result=ProtectedArchiveNormalizer.normalize(archive,File(root,"ready.cbz"),"secret".toCharArray())
            openComicZip(result).use { zip -> assertArrayEquals(page.readBytes(),zip.getInputStream(zip.entries().nextElement()).readBytes()) }
            val aes=byteArrayOf(6,0xf1.toByte(),7,1)
            guardSevenZAesCost(aes,byteArrayOf(19))
            assertThrows(ComicArchiveException::class.java) { guardSevenZAesCost(aes,byteArrayOf(62)) }
        } finally { root.deleteRecursively() }
    }
    @Test fun overlappingManifestStringsHaveAnAggregateBudget() {
        // 50k distinct offsets overlap one 51KiB region but request >6MiB of decoded strings.
        val count=50_000
        val start=28+4*count
        val poolSize=start+count+130
        val bytes=ByteArray(8+poolSize)
        val b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putShort(0,3);b.putShort(2,8);b.putInt(4,bytes.size)
        b.putShort(8,1);b.putShort(10,28);b.putInt(12,poolSize)
        b.putInt(16,count);b.putInt(24,256);b.putInt(28,start)
        repeat(count){i->b.putInt(8+28+4*i,i)}
        java.util.Arrays.fill(bytes,8+start,bytes.size,127.toByte())
        val error=assertThrows(IllegalArgumentException::class.java){ApkManifestSplit.read(bytes)}
        assertTrue(error.message.orEmpty().contains("String pool"))
    }

}

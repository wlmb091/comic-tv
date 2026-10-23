package com.pelican.comictv.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ComicArchiveTest {
    private fun fixture(name: String, block: (File, File) -> Unit) {
        val root = Files.createTempDirectory("rar-test").toFile()
        try {
            val file = File(root, "wrong-extension.zip")
            javaClass.getResourceAsStream("/archives/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            block(file, root)
        } finally { root.deleteRecursively() }
    }

    @Test fun `detects RAR4 and solid RAR5 by bytes and extracts in reverse order`() {
        for (name in listOf("pages-rar4.rar", "pages-rar5.rar")) fixture(name) { file, root ->
            val reader = ComicArchive()
            val pages = reader.index(file)
            assertEquals(listOf("2.png", "10.png"), pages.map { it.key })
            for (page in pages.reversed()) {
                val result = reader.extract(file, page, File(root, "page-${page.index}"))
                assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47), result.readBytes().take(4).toByteArray())
                assertFalse(File(root, result.name + ".part").exists())
            }
        }
    }

    @Test fun `rejects password traversal duplicate and corrupt archives`() {
        for (name in listOf("password.rar", "traversal.rar", "duplicate.rar")) fixture(name) { file, _ ->
            assertThrows(ComicArchiveException::class.java) { ComicArchive().index(file) }
        }
        fixture("pages-rar4.rar") { file, root ->
            val reader = ComicArchive()
            val page = reader.index(file).last() // 10.png is physically first in this fixture.
            val bytes = file.readBytes()
            val png = bytes.indices.first { it + 3 < bytes.size && bytes[it] == 0x89.toByte() && bytes[it + 1] == 0x50.toByte() }
            bytes[png + 15] = (bytes[png + 15].toInt() xor 1).toByte()
            file.writeBytes(bytes)
            val destination = File(root, "broken-page")
            assertThrows(ComicArchiveException::class.java) { reader.extract(file, page, destination) }
            assertFalse(destination.exists())
            assertFalse(File(root, "broken-page.part").exists())
        }
    }

    @Test fun `enforces entry page total archive and runtime extraction limits`() {
        fixture("pages-rar4.rar") { file, root ->
            for (limits in listOf(ArchiveLimits(maxEntries = 1), ArchiveLimits(maxPageBytes = 1),
                ArchiveLimits(maxTotalBytes = 1), ArchiveLimits(maxArchiveBytes = 1))) {
                assertThrows(ComicArchiveException::class.java) { ComicArchive(limits).index(file) }
            }
            val page = ComicArchive().index(file).first()
            assertThrows(ComicArchiveException::class.java) {
                ComicArchive(ArchiveLimits(maxPageBytes = 1)).extract(file, page, File(root, "limited"))
            }
            assertFalse(File(root, "limited.part").exists())
        }
    }

    @Test fun `cancelled RAR operation does not publish a partial page`() {
        fixture("pages-rar5.rar") { file, root ->
            val reader = ComicArchive()
            val page = reader.index(file).last()
            Thread.currentThread().interrupt()
            try {
                assertThrows(ComicArchiveException::class.java) { reader.extract(file, page, File(root, "cancelled")) }
            } finally { Thread.interrupted() }
            assertFalse(File(root, "cancelled").exists())
            assertFalse(File(root, "cancelled.part").exists())
        }
    }
}

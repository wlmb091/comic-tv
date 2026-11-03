package com.pelican.comictv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.archive.ArchiveLimits
import com.pelican.comictv.archive.CollectionArchive
import com.pelican.comictv.archive.ComicArchive
import com.pelican.comictv.archive.ComicArchiveException
import com.pelican.comictv.archive.LibArchiveRar
import com.pelican.comictv.archive.RarBackends
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The libarchive RAR decoder that ships in the APK, on the device, with the synthetic fixtures of the core tests. */
@RunWith(AndroidJUnit4::class)
class LibArchiveRarTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun fixture(name: String, block: (File, File) -> Unit) {
        val root = File(context.cacheDir, "libarchive-test-" + System.nanoTime()).apply { mkdirs() }
        try {
            val file = File(root, name)
            assets.open("archives/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
            block(file, root)
        } finally { root.deleteRecursively() }
    }

    @Test fun appUsesLibArchive() {
        assertSame(LibArchiveRar, RarBackends.current)
        assertTrue(LibArchiveRar.version(), LibArchiveRar.version().startsWith("libarchive 3.8.11"))
    }

    @Test fun readsRar4AndSolidRar5PagesInNaturalOrder() {
        for (name in listOf("pages-rar4.rar", "pages-rar5.rar")) fixture(name) { file, root ->
            val reader = ComicArchive()
            val pages = reader.index(file)
            assertEquals(listOf("2.png", "10.png"), pages.map { it.key })
            for (page in pages) {
                val out = reader.extract(file, page, File(root, "page-${page.index}"))
                assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47), out.readBytes().take(4).toByteArray())
                assertFalse(File(root, out.name + ".part").exists())
            }
        }
    }

    @Test fun refusesPasswordTraversalDuplicateAndCorruptPages() {
        for (name in listOf("password.rar", "traversal.rar", "duplicate.rar")) fixture(name) { file, _ ->
            assertThrows(ComicArchiveException::class.java) { ComicArchive().index(file) }
        }
        fixture("pages-rar4.rar") { file, root ->
            val page = ComicArchive().index(file).last()
            val bytes = file.readBytes()
            val png = bytes.indices.first { it + 3 < bytes.size && bytes[it] == 0x89.toByte() && bytes[it + 1] == 0x50.toByte() }
            bytes[png + 15] = (bytes[png + 15].toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertThrows(ComicArchiveException::class.java) { ComicArchive().extract(file, page, File(root, "broken")) }
            assertFalse(File(root, "broken").exists())
        }
    }

    @Test fun enforcesLimitsAndCancellation() {
        fixture("pages-rar5.rar") { file, root ->
            val page = ComicArchive().index(file).first()
            assertThrows(ComicArchiveException::class.java) { ComicArchive(ArchiveLimits(maxPageBytes = 1)).extract(file, page, File(root, "limited")) }
            Thread.currentThread().interrupt()
            try { assertThrows(ComicArchiveException::class.java) { ComicArchive().extract(file, page, File(root, "cancelled")) } }
            finally { Thread.interrupted() }
            assertFalse(File(root, "cancelled").exists())
        }
    }

    @Test fun extractsSolidRarCollection() {
        fixture("collection-solid-rar5.rar") { file, root ->
            val reader = CollectionArchive()
            val entries = reader.inspect(file)!!
            assertEquals(listOf("2.cbr", "10.cbr"), entries.map { it.name })
            entries.forEachIndexed { i, entry ->
                val inner = reader.extract(file, entry, File(root, "inner-$i"))
                assertEquals(listOf("2.png", "10.png"), ComicArchive().index(inner).map { it.key })
            }
        }
    }
}

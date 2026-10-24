package com.pelican.comictv.storage

import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LibraryStorageTest {
    private fun file(root: File, path: String, size: Int) = File(root, path).apply { parentFile.mkdirs(); writeBytes(ByteArray(size)) }
    @Test fun reportsCategoriesAndOnlyDeletesSelectedCache() {
        val root = Files.createTempDirectory("library-storage").toFile()
        try {
            val original = file(root, "originals/a.cbz", 9)
            val cover = file(root, "covers/a.jpg", 7)
            val page = file(root, "pages/a", 5)
            val thumb = file(root, "thumbs/a", 3)
            val other = file(root, "other/keep", 11)
            val offline = file(root,"offline/edition/page",13)
            val storage = LibraryStorage(File(root,"originals"), File(root,"covers"), File(root,"pages"), File(root,"thumbs"),offlineDirectory = File(root,"offline"))
            val report = storage.report()
            assertEquals(9L, report.originals); assertEquals(7L, report.covers)
            assertEquals(5L, report.pages); assertEquals(3L, report.thumbnails)
            assertEquals(13L, report.offline)
            val clean = storage.clear(CacheSelection.Pages)
            assertEquals(5L, clean.freedBytes); assertEquals(0, clean.failures)
            assertFalse(page.exists()); assertTrue(thumb.isFile)
            assertTrue(original.isFile); assertTrue(cover.isFile); assertTrue(other.isFile)
            assertEquals(3L, storage.clear(CacheSelection.Both).freedBytes)
            assertTrue(offline.isFile)
        } finally { root.deleteRecursively() }
    }
    @Test fun missingCachesAreZeroAndFailedDeletionIsNotReportedAsSuccess() {
        val root = Files.createTempDirectory("storage-failure").toFile()
        try {
            val storage = LibraryStorage(File(root,"originals"), File(root,"covers"), File(root,"pages"), File(root,"thumbs"),
                remove = { it.name != "bad" && it.delete() })
            assertEquals(0L, storage.report().pages)
            assertEquals(0L, storage.clear(CacheSelection.Both).freedBytes)
            val bad = file(root, "pages/bad", 5); file(root, "pages/good", 3)
            val result = storage.clear(CacheSelection.Pages)
            assertEquals(3L, result.freedBytes); assertEquals(1, result.failures)
            assertTrue(bad.isFile); assertEquals(5L, storage.report().pages)
        } finally { root.deleteRecursively() }
    }
    @Test fun linksAreNotFollowedOrUsedAsACacheRoot() {
        val root = Files.createTempDirectory("storage-links").toFile()
        val outside = Files.createTempDirectory("outside-cache").toFile()
        val link = File(root, "pages/link").toPath()
        val rootLink = File(root, "linked-root").toPath()
        try {
            val keep = file(outside, "keep", 17); link.parent.toFile().mkdirs()
            try { Files.createSymbolicLink(link, outside.toPath()); Files.createSymbolicLink(rootLink, outside.toPath()) }
            catch (e: Exception) { assumeNoException("Platform cannot create symbolic links", e); return }
            val storage = LibraryStorage(File(root,"originals"), File(root,"covers"), File(root,"pages"), File(root,"thumbs"))
            assertEquals(0L, storage.report().pages)
            assertTrue(storage.clear(CacheSelection.Both).failures > 0)
            val linked = LibraryStorage(File(root,"originals"), File(root,"covers"), rootLink.toFile(), File(root,"thumbs"))
            assertTrue(linked.clear(CacheSelection.Pages).failures > 0)
            assertTrue(keep.isFile)
        } finally {
            Files.deleteIfExists(link); Files.deleteIfExists(rootLink)
            root.deleteRecursively(); outside.deleteRecursively()
        }
    }
}

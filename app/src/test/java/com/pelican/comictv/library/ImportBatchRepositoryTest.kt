package com.pelican.comictv.library

import android.content.ContextWrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportBatchRepositoryTest {
    private class Dao : ComicDao {
        val rows = mutableMapOf<String, ComicEntity>()
        override fun observe() = flowOf(rows.values.toList())
        override suspend fun get(id: String) = rows[id]
        override suspend fun ids() = rows.keys.toList()
        override suspend fun insert(comic: ComicEntity) { rows[comic.id] = comic }
        override suspend fun delete(id: String) { rows.remove(id) }
        override suspend fun setHistoryHidden(id: String, hidden: Boolean) = Unit
        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
        override suspend fun updatePageCount(id: String, count: Int) = Unit
        override suspend fun saveProgress(id: String, page: Int, time: Long) = Unit
        override suspend fun savePreferences(id: String, fit: Int, rtl: Boolean) = Unit
    }
    private fun zip(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { out -> entries.forEach { (name, content) ->
            out.putNextEntry(ZipEntry(name)); out.write(content); out.closeEntry()
        } }
    }.toByteArray()
    @Test fun batchRetryAndConcurrentImportDoNotDuplicateOrResetExistingProgress() = runBlocking {
        val root = Files.createTempDirectory("batch-repository").toFile()
        val context = object : ContextWrapper(null) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val dao = Dao()
        val repository = LibraryRepository(context, dao, CoverGenerator { _, file -> file.writeBytes(byteArrayOf(1)) })
        try {
            val inner = zip(listOf("1.png" to byteArrayOf(1), "2.png" to byteArrayOf(2)))
            val source = File(root, "first.cbz").apply { writeBytes(inner) }
            val old = repository.importComic(source, "previously-imported.cbz")
            dao.rows[old.id] = old.copy(currentPage = 1, favorite = true)
            val other = zip(listOf("1.png" to byteArrayOf(3)))
            val collection = File(root, "collection.zip").apply { writeBytes(zip(listOf(
                "old.cbz" to inner, "new.cbz" to other, "same.cbz" to other, "broken.cbz" to byteArrayOf(5)))) }
            val first = repository.importReceived(collection, "collection.zip") {}
            assertEquals(1, first.imported); assertEquals(2, first.duplicates); assertEquals(1, first.failures.size)
            val results = coroutineScope { listOf(async { repository.importReceived(collection, "one.zip") {} },
                async { repository.importReceived(collection, "two.zip") {} }).awaitAll() }
            assertTrue(results.all { it.imported == 0 && it.duplicates == 3 && it.failures.size == 1 })
            assertEquals(2, dao.rows.size)
            assertEquals(1, dao.rows[old.id]!!.currentPage); assertTrue(dao.rows[old.id]!!.favorite)
            assertTrue(source.isFile); assertTrue(collection.isFile)
            assertTrue(File(context.cacheDir, "collection-imports").listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
}

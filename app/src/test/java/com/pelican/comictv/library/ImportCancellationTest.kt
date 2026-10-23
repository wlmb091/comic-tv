package com.pelican.comictv.library

import android.content.ContextWrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportCancellationTest {
    @Test fun cancelledCoverGenerationDoesNotCommitOrKeepOriginal() = runBlocking {
        val root = Files.createTempDirectory("cancel-import").toFile()
        val context = object : ContextWrapper(null) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val dao = object : ComicDao {
            val rows = mutableMapOf<String, ComicEntity>()
            override fun observe(): Flow<List<ComicEntity>> = flowOf(emptyList())
            override suspend fun get(id: String) = rows[id]
            override suspend fun ids() = rows.keys.toList()
            override suspend fun insert(comic: ComicEntity) { rows[comic.id] = comic }
            override suspend fun updatePageCount(id: String, count: Int) = Unit
            override suspend fun saveProgress(id: String, page: Int, time: Long) = Unit
            override suspend fun savePreferences(id: String, fit: Int, rtl: Boolean) = Unit
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cover = CoverGenerator { _, output ->
            entered.countDown()
            var waiting = true
            while (waiting) try { release.await(); waiting = false } catch (_: InterruptedException) { }
            output.writeBytes(byteArrayOf(1))
        }
        val source = File(root, "source.cbz")
        ZipOutputStream(source.outputStream()).use { it.putNextEntry(ZipEntry("1.jpg")); it.write(byteArrayOf(1)); it.closeEntry() }
        try {
            val repository = LibraryRepository(context, dao, cover)
            val importing = launch(Dispatchers.Default) { repository.importComic(source, "source.cbz") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            importing.cancel()
            release.countDown()
            importing.join()
            assertTrue(dao.rows.isEmpty())
            assertTrue(File(context.filesDir, "originals").listFiles().orEmpty().isEmpty())
        } finally { release.countDown(); root.deleteRecursively() }
    }
}

package com.pelican.comictv.library

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CollectionImportRunnerTest {
    private fun packageFile(root: File): File = File(root, "collection.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { out -> listOf("10.cbz", "2.cbz", "3.cbz").forEach {
            out.putNextEntry(ZipEntry(it)); out.write(it.toByteArray()); out.closeEntry()
        } }
    }
    @Test fun partialFailureAndDuplicatesKeepNaturalOrderAndCleanScratch() = runBlocking {
        val root = Files.createTempDirectory("batch-test").toFile()
        try {
            val calls = mutableListOf<String>()
            val progress = mutableListOf<String>()
            val scratch = File(root, "scratch")
            val runner = CollectionImportRunner(scratch, checkSpace = {}) { file, name ->
                assertEquals(name, file.readText()); calls += name
                when (name) { "3.cbz" -> error("Inválido"); else -> ImportedEdition(name, name == "10.cbz") }
            }
            val result = runner.run(packageFile(root), "collection.zip") { progress += it }
            assertEquals(listOf("2.cbz", "3.cbz", "10.cbz"), calls)
            assertEquals(1, result.imported); assertEquals(1, result.duplicates); assertEquals(1, result.failures.size)
            assertTrue(result.collection); assertTrue(progress.any { it.contains("3/3") })
            assertTrue(scratch.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun cancellationIsNotAnIndividualFailureAndCleansScratch() = runBlocking {
        val root = Files.createTempDirectory("batch-cancel").toFile()
        try {
            var calls = 0
            val scratch = File(root, "scratch")
            val runner = CollectionImportRunner(scratch, checkSpace = {}) { _, _ ->
                if (++calls == 2) throw CancellationException("stop")
                ImportedEdition("first", false)
            }
            try { runner.run(packageFile(root), "collection.zip") {}; fail("expected cancellation") }
            catch (_: CancellationException) { }
            assertEquals(2, calls); assertTrue(scratch.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun singleFileDelegatesWithoutCollectionAndInsufficientSpaceDoesNotExtract() = runBlocking {
        val root = Files.createTempDirectory("batch-single").toFile()
        try {
            val file = File(root, "book.pdf").apply { writeText("%PDF-") }
            var calls = 0
            val runner = CollectionImportRunner(File(root, "scratch"), checkSpace = {}) { input, _ ->
                calls++; assertEquals(file, input); ImportedEdition("book", false)
            }
            val result = runner.run(file, "book.pdf") {}
            assertFalse(result.collection); assertEquals("book", result.singleId)
            val blocked = CollectionImportRunner(File(root, "blocked"), checkSpace = { error("Sem espaço") }) { _, _ ->
                calls++; ImportedEdition("unexpected", false)
            }
            val denied = blocked.run(packageFile(root), "collection.zip") {}
            assertEquals(3, denied.failures.size); assertEquals(1, calls)
            assertTrue(File(root, "blocked").listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
}

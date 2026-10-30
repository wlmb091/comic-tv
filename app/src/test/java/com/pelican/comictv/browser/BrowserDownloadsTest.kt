package com.pelican.comictv.browser

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserDownloadsTest {
    @Test fun downloadsRunSequentiallyAndRemainAvailableAfterCompletion() = runTest {
        val dir = Files.createTempDirectory("downloads").toFile(); var active = 0; var peak = 0
        val manager = BrowserDownloads(this, dir, transfer = { _, file, progress, _ ->
            active++; peak = maxOf(peak, active); delay(10); file.writeText("abc"); progress(3, 3); active--
            BrowserTransferResult("book.cbz", 3, "v1", "application/zip")
        })
        try {
            manager.enqueue(BrowserDownloadRequest("https://example.org/one", "one", "Agent"))
            manager.enqueue(BrowserDownloadRequest("https://example.org/two", "two", "Agent"))
            advanceUntilIdle()
            assertEquals(1, peak); assertTrue(manager.items.value.all { it.status == BrowserDownloadStatus.Complete })
            assertTrue(manager.items.value.all { manager.file(it.id).readText() == "abc" })
            manager.pauseAll(); assertTrue(manager.items.value.all { it.status == BrowserDownloadStatus.Complete })
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun pausePreservesPartialAndExplicitResumeReusesIt() = runTest {
        val dir = Files.createTempDirectory("downloads").toFile(); var first = true
        val manager = BrowserDownloads(this, dir, transfer = { _, file, progress, headers ->
            if (first) { first = false; headers(BrowserTransferResult("book.cbz", 6, "\"v1\"", null)); file.writeText("abc"); progress(3, 6); awaitCancellation() }
            assertEquals("abc", file.readText()); file.appendText("def")
            BrowserTransferResult("book.cbz", 6, "\"v1\"", null)
        })
        try {
            val id = manager.enqueue(BrowserDownloadRequest("https://example.org/book", "book.cbz", "Agent"))!!
            runCurrent(); manager.pause(id); advanceUntilIdle()
            assertEquals(BrowserDownloadStatus.Paused, manager.items.value.single().status)
            assertEquals("abc", manager.file(id).readText())
            manager.resume(id); advanceUntilIdle()
            assertEquals("abcdef", manager.file(id).readText()); assertEquals(BrowserDownloadStatus.Complete, manager.items.value.single().status)
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun foregroundLossPausesQueueAndDoesNotRestartUntilUserResumes() = runTest {
        val dir = Files.createTempDirectory("downloads").toFile(); var calls = 0
        val manager = BrowserDownloads(this, dir, transfer = { _, _, _, _ -> calls++; awaitCancellation() })
        try {
            manager.enqueue(BrowserDownloadRequest("https://example.org/one", "one", "A")); manager.enqueue(BrowserDownloadRequest("https://example.org/two", "two", "A"))
            runCurrent(); manager.setForeground(false); advanceUntilIdle(); manager.setForeground(true); runCurrent()
            assertEquals(1, calls); assertTrue(manager.items.value.all { it.status == BrowserDownloadStatus.Paused })
        } finally { manager.pauseAll(); advanceUntilIdle(); dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun restoredActiveRecordIsPausedAndDeletingOneNeverTouchesOtherFiles() = runTest {
        val dir = Files.createTempDirectory("downloads").toFile()
        val one = BrowserDownloadItem("00000000-0000-0000-0000-000000000001", "https://example.org/one", "one.cbz", "A", status = BrowserDownloadStatus.Running)
        val two = one.copy(id = "00000000-0000-0000-0000-000000000002", filename = "two.cbz", status = BrowserDownloadStatus.Complete)
        File(dir, "${one.id}.download").writeText("one"); File(dir, "${two.id}.download").writeText("two")
        val manager = BrowserDownloads(this, dir, listOf(one, two), transfer = { _, _, _, _ -> error("No automatic network") })
        try {
            assertEquals(BrowserDownloadStatus.Paused, manager.items.value.first().status)
            assertEquals(3L, manager.items.value.first().bytes)
            manager.remove(one.id); assertFalse(manager.file(one.id).exists()); assertEquals("two", manager.file(two.id).readText())
            assertTrue(manager.items.value.none { it.id == one.id })
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
}

package com.pelican.comictv.updates

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateDownloadStoreTest {
    private val bytes = "fake-signed-apk".toByteArray()
    private fun manifest() = UpdateManifest(57, "1.1.18", 26, bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        "${UpdateManifest.RELEASE_BASE}/v1.1.18/Comic-TV-1.1.18.apk", "Notas")
    @Test fun validatesBeforeReadyReusesFileAndRevalidatesBeforeOffering() = runTest {
        val dir = Files.createTempDirectory("update-store").toFile(); var calls = 0; var verified = 0
        val store = UpdateDownloadStore(dir, { 1_000_000_000 }, { _, _ -> verified++ }) { _, target, progress ->
            calls++; target.writeBytes(bytes); progress(bytes.size.toLong())
        }
        try {
            val first = store.download(manifest()) { }
            assertTrue(first.isFile); assertEquals(1, calls)
            assertEquals(first, store.download(manifest()) { }); assertEquals(1, calls)
            store.revalidate(first, manifest()); assertTrue(verified >= 3)
            store.markOffered(first); first.appendText("tamper")
            try { store.revalidate(first, manifest()); fail("Tampered APK accepted") } catch (_: Exception) { }
            assertTrue("Installer-owned file must be preserved", first.isFile)
            store.installationReturned(first)
        } finally { dir.deleteRecursively() }
    }
    @Test fun cancellationCleansPartialButNeverAnOfferedFile() = runTest {
        val dir = Files.createTempDirectory("update-cancel").toFile()
        var slow = false
        val store = UpdateDownloadStore(dir, { 1_000_000_000 }, { _, _ -> }) { _, target, _ ->
            target.writeBytes(bytes); if (slow) awaitCancellation()
        }
        try {
            val ready = store.download(manifest()) { }; store.markOffered(ready)
            assertTrue(ready.isFile)
            val newer = manifest().copy(versionCode = 58, versionName = "1.1.19", apkUrl = "${UpdateManifest.RELEASE_BASE}/v1.1.19/Comic-TV-1.1.19.apk")
            slow = true
            val job = launch { store.download(newer) { } }; runCurrent(); job.cancelAndJoin()
            assertTrue(ready.isFile)
            assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally { dir.deleteRecursively() }
    }
    @Test fun checksumMismatchAndInsufficientSpaceNeverProduceReadyApk() = runTest {
        val dir = Files.createTempDirectory("update-invalid").toFile()
        try {
            for (space in listOf(0L, 1_000_000_000L)) {
                val store = UpdateDownloadStore(dir, { space }, { _, _ -> }) { _, target, _ -> target.writeText("wrong") }
                try { store.download(manifest()) { }; fail("Expected rejection") } catch (_: Exception) { }
                assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".apk") || it.name.endsWith(".part") })
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun newDownloadDiscardsOnlySupersededUnofferedApks() = runTest {
        val dir=Files.createTempDirectory("update-replace").toFile()
        val store=UpdateDownloadStore(dir,{1_000_000_000},{_,_->}){_,target,_->target.writeBytes(bytes)}
        try {
            val old=store.download(manifest()){}
            val next=manifest().copy(versionCode=58,versionName="1.1.19",apkUrl="${UpdateManifest.RELEASE_BASE}/v1.1.19/Comic-TV-1.1.19.apk")
            val ready=store.download(next){}
            assertFalse(old.exists());assertTrue(ready.isFile)
        } finally {dir.deleteRecursively()}
    }
}

package com.pelican.comictv.library

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.storage.CacheSelection
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RemotePagePriorityTest {
    private suspend fun fixture(block: suspend (LibraryRepository, LibraryDatabase, android.content.Context, ByteArray, String) -> Unit) {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(real.cacheDir, "priority-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = real.getSharedPreferences("${root.name}-$name", mode)
        }
        val db = Room.inMemoryDatabaseBuilder(real, LibraryDatabase::class.java).build()
        val image = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        image.recycle()
        val id = "online-" + "a".repeat(64)
        val library = LibraryRepository(context, db.comics())
        try {
            db.comics().insert(ComicEntity(id, "Online", 4))
            block(library, db, context, bytes, id)
        } finally { library.closeBackgroundWork(); db.close(); root.deleteRecursively(); real.deleteSharedPreferences("${root.name}-reader-image") }
    }
    @Test fun cachedAndVisiblePagesPassSlowAnticipation() = runBlocking { fixture { library, _, _, bytes, id ->
        library.externalImage(id, 0, false) { it.writeBytes(bytes) }
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val background = async(Dispatchers.IO) { library.externalImage(id, 1, false, false) { started.complete(Unit); release.await(); it.writeBytes(bytes) } }
        try {
            started.await()
            withTimeout(2000) {
                assertTrue(library.externalImage(id, 0, false) { error("Cache was missed") }.isFile)
                assertTrue(library.externalImage(id, 2, false) { it.writeBytes(bytes) }.isFile)
            }
        } finally { release.complete(Unit); background.await() }
    } }
    @Test fun removalRejectsCommitFromDownloadAlreadyRunning() = runBlocking { fixture { library, db, context, bytes, id ->
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val load = async(Dispatchers.IO) { runCatching { library.externalImage(id, 0, false) {
            it.writeBytes(bytes); started.complete(Unit); release.await()
        } } }
        started.await()
        try { withTimeout(2000) { library.deleteOnline(id) } } finally { release.complete(Unit) }
        assertTrue(load.await().isFailure)
        assertNull(db.comics().get(id))
        assertFalse(File(context.cacheDir, "pages/$id-0").exists())
        assertTrue(File(context.cacheDir, "online-fetches").listFiles().orEmpty().isEmpty())
    } }
    @Test fun cacheClearRejectsOldCommitAndAllowsFutureRequest() = runBlocking { fixture { library, _, context, bytes, id ->
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val load = async(Dispatchers.IO) { runCatching { library.externalImage(id, 0, false) {
            started.complete(Unit); release.await(); it.writeBytes(bytes)
        } } }
        started.await()
        try { withTimeout(2000) { library.clearReaderCaches(CacheSelection.Both) } } finally { release.complete(Unit) }
        assertTrue(load.await().isFailure)
        assertTrue(library.externalImage(id, 0, false) { it.writeBytes(bytes) }.isFile)
        assertTrue(File(context.cacheDir, "online-fetches").listFiles().orEmpty().isEmpty())
    } }
    @Test fun cancelledFetchCleansStagingAndCanBeRetried() = runBlocking { fixture { library, _, context, bytes, id ->
        val started = CompletableDeferred<Unit>()
        val load = launch(Dispatchers.IO) { library.externalImage(id, 0, false) {
            it.writeBytes(bytes); started.complete(Unit); awaitCancellation()
        } }
        started.await(); load.cancelAndJoin()
        assertTrue(File(context.cacheDir, "online-fetches").listFiles().orEmpty().isEmpty())
        assertTrue(library.externalImage(id, 0, false) { it.writeBytes(bytes) }.isFile)
    } }
}

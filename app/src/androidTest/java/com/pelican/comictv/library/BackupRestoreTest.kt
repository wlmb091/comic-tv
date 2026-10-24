package com.pelican.comictv.library

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.backup.*
import com.pelican.comictv.mihon.MihonEngine
import com.pelican.comictv.reader.ReaderImagePreferences
import com.pelican.comictv.reader.*
import com.pelican.comictv.sources.*
import com.pelican.comictv.ui.AppearancePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

class BackupRestoreTest {
    @Test fun missingOriginalStaysPendingAndSameFileRestoresIdentityAndCollections() = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "backup-test-${UUID.randomUUID()}"
        val root = File(real.cacheDir, prefix).apply { mkdirs() }
        val prefs = mutableSetOf<String>()
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root,"files").apply { mkdirs() }
            override fun getCacheDir() = File(root,"cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val key = "$prefix-$name"; prefs += key; return real.getSharedPreferences(key, Context.MODE_PRIVATE)
            }
        }
        val db = Room.inMemoryDatabaseBuilder(real, LibraryDatabase::class.java).build()
        try {
            val source = File(root,"original.cbz")
            ZipOutputStream(source.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("1.png"))
                val bitmap = Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG,100,zip); bitmap.recycle(); zip.closeEntry()
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
            val pending = RestorePendingStore(File(context.filesDir,"backup"))
            val library = LibraryRepository(context, db.comics(), collectionDao = db.collections(), pendingRestores = pending)
            val images = ReaderImagePreferences(context); val appearance = AppearancePreferences(context)
            val online = OnlineReaderRepository(context, db.comics(), library, MihonEngine(real), SourcePreferences(context))
            val repository = BackupRepository(context,db,library,online,appearance,images,pending)
            val document = BackupDocument(createdAt = 123, comics = listOf(BackupComic("restored", "Título", 1, lastRead = 99, favorite = true, sha256 = hash, imagePreset = "Vintage", imageIntensity = 2)),
                collections = listOf(BackupCollection("c","Meus favoritos")), links = listOf(BackupLink("restored","c")))
            repository.restore(document, false); repository.restore(document, false)
            repository.restore(document.copy(comics = document.comics.map { it.copy(lastRead = 1, imagePreset = "Original", imageIntensity = 0) }), false)
            assertTrue(db.backups().comics().isEmpty()); assertEquals(1, repository.pendingCount())
            val imported = library.importComic(source,"original.cbz")
            assertEquals("restored", imported.id); assertTrue(imported.favorite); assertEquals(99L, imported.lastRead)
            assertEquals(setOf("c"), library.collectionsFor(imported.id)); assertEquals(0, repository.pendingCount())
            assertEquals(ReaderImageOptions(ImagePreset.Vintage, 2), images.read(imported.id))
            val exported = repository.preview(repository.prepare())
            assertEquals(hash, exported.comics.single().sha256)
            assertTrue(library.originalFile(imported.id).isFile)
            val failing = object : ReaderImageStore {
                override fun read(id: String) = images.read(id)
                override suspend fun save(id: String, options: ReaderImageOptions) { throw java.io.IOException("Injected interruption") }
            }
            val interrupted = BackupRepository(context,db,library,online,appearance,failing,pending)
            val latest = exported.copy(comics = exported.comics.map { it.copy(lastRead = 500, imagePreset = "Ink", imageIntensity = 0) })
            var failed = false
            try { interrupted.restore(latest, false) } catch (_: java.io.IOException) { failed = true }
            assertTrue(failed)
            repository.recover()
            assertEquals(500L, db.comics().get(imported.id)!!.lastRead)
            assertEquals(ReaderImageOptions(ImagePreset.Ink,0), images.read(imported.id))
        } finally { db.close(); root.deleteRecursively(); prefs.forEach { real.deleteSharedPreferences(it) } }
    }
}

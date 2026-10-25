package com.pelican.comictv.library

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImportedRemovalTest {
    @Test fun deletionRemovesOwnedCopyAndMembershipButPreservesExternalAndOtherBook() = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "delete-test-${System.nanoTime()}"
        val root = File(real.cacheDir,prefix).apply { mkdirs() }
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root,"files").apply { mkdirs() }
            override fun getCacheDir() = File(root,"cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = real.getSharedPreferences("$prefix-$name",mode)
        }
        val db = Room.inMemoryDatabaseBuilder(real,LibraryDatabase::class.java).build()
        try {
            db.comics().insert(ComicEntity("one","One",3,currentPage=2,lastRead=1,favorite=true))
            db.comics().insert(ComicEntity("other","Other",3))
            val library=LibraryRepository(context,db.comics(),collectionDao=db.collections())
            val collection=library.createCollection("Collection")
            library.setCollections("one",setOf(collection))
            val external=File(root,"external.pdf").apply {writeText("external")}
            library.originalFile("one").apply {parentFile?.mkdirs();writeText("owned")}
            library.originalFile("other").writeText("other")
            library.cover("one").writeText("cover")
            File(context.cacheDir,"pages/one-0").writeText("page")
            File(context.cacheDir,"pages/other-0").writeText("keep")
            assertTrue(library.deleteImported("one"))
            assertNull(db.comics().get("one"))
            assertTrue(library.collectionsFor("one").isEmpty())
            assertFalse(library.originalFile("one").exists())
            assertFalse(library.cover("one").exists())
            assertFalse(File(context.cacheDir,"pages/one-0").exists())
            assertEquals("keep",File(context.cacheDir,"pages/other-0").readText())
            assertEquals("other",library.originalFile("other").readText())
            assertEquals("external",external.readText())

            // Online deletion uses only private records/downloads and never consults the source.
            val source=com.pelican.comictv.sources.SourceHandle("pkg",1,"Source","en","https://example.com")
            val title=com.pelican.comictv.sources.OnlineTitle("series","Series")
            val chapter=com.pelican.comictv.sources.OnlineChapter("chapter","Chapter")
            val id=com.pelican.comictv.sources.OnlineIdentity.id(source,title,chapter)
            db.comics().insert(ComicEntity(id,"Online",1,lastRead=1,favorite=true))
            library.setCollections(id,setOf(collection))
            val online=com.pelican.comictv.sources.OnlineReaderRepository(context,db.comics(),library,
                com.pelican.comictv.mihon.MihonEngine(real),com.pelican.comictv.sources.SourcePreferences(context))
            online.restoreMetadata(id,com.pelican.comictv.backup.BackupOnline(source,title,chapter,"a".repeat(64)))
            val store=com.pelican.comictv.offline.OfflineStore(File(context.filesDir,"offline"),freeBytes={Long.MAX_VALUE})
            store.begin(id,1,"b".repeat(64))
            val downloads=com.pelican.comictv.offline.DownloadRepository(File(context.filesDir,"queue.json"),store,
                {error("Deletion must not contact source")},{_,_,_->error("Deletion must not download")})
            downloads.refresh();downloads.enqueue(id)
            assertTrue(downloads.removeLibraryEdition(id) {
                val cleaned=library.deleteOnline(id)
                online.forgetEdition(id) && cleaned
            })
            assertNull(db.comics().get(id))
            assertTrue(library.collectionsFor(id).isEmpty())
            assertFalse(File(context.filesDir,"online-records/$id.json").exists())
            assertFalse(id in store.ids())
            assertFalse(id in downloads.state.value.queue)
            assertNotNull(db.comics().get("other"))
        } finally {db.close();root.deleteRecursively();real.deleteSharedPreferences("$prefix-reader-image")}
    }
}

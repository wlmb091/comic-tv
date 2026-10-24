package com.pelican.comictv.offline

import com.pelican.comictv.library.*
import com.pelican.comictv.model.PageRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfflineFirstReaderTest {
    @Test fun completeEditionReadsAndRegeneratesThumbnailWithoutAnySourceCall() = runBlocking {
        val root = Files.createTempDirectory("offline-reader").toFile()
        val id = "online-" + "d".repeat(64)
        val inaccessible = object : ReaderRepository {
            override suspend fun getComic(id: String) = ComicEntity(id,"Offline",1)
            override suspend fun pages(comicId: String): List<PageRef> = error("Must not query source")
            override suspend fun materialize(comicId: String,page: PageRef): File = error("Must not download")
            override suspend fun thumbnail(comicId: String,page: PageRef): File = error("Must not query thumbnails online")
            override suspend fun saveProgress(id: String,page: Int) = Unit
            override suspend fun savePreferences(id: String,fit: Int,rtl: Boolean) = Unit
        }
        try {
            val store = OfflineStore(root, freeBytes = { Long.MAX_VALUE })
            store.begin(id,1,"e".repeat(64)); store.incoming(id,0).writeBytes(byteArrayOf(1,2,3)); store.commitPage(id,0,10,20); store.finish(id)
            var thumbs = 0
            val reader = OfflineFirstReaderRepository(store,inaccessible,inaccessible) { _, _, file -> thumbs++; file }
            val page = reader.pages(id).single()
            assertEquals("offline:0",page.key)
            assertArrayEquals(byteArrayOf(1,2,3),reader.materialize(id,page).readBytes())
            assertTrue(reader.thumbnail(id,page).isFile); assertEquals(1,thumbs)
        } finally { root.deleteRecursively() }
    }
}

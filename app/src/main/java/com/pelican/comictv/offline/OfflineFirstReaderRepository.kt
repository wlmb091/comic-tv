package com.pelican.comictv.offline

import com.pelican.comictv.library.ReaderRepository
import com.pelican.comictv.model.PageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

class OfflineFirstReaderRepository(private val store: OfflineStore, private val online: ReaderRepository,
    private val metadata: ReaderRepository, private val onOfflineOpened: (suspend (String,Int) -> Unit)? = null,
    private val makeThumbnail: suspend (String,Int,File) -> File) : ReaderRepository {
    override suspend fun getComic(id: String) = metadata.getComic(id)
    override suspend fun saveProgress(id: String,page: Int) = metadata.saveProgress(id,page)
    override suspend fun savePreferences(id: String,fit: Int,rtl: Boolean) = metadata.savePreferences(id,fit,rtl)
    override suspend fun pages(comicId: String): List<PageRef> = withContext(Dispatchers.IO) {
        if (store.hasPackage(comicId)) runInterruptible { store.verified(comicId) }.let { manifest ->
            onOfflineOpened?.invoke(comicId,manifest.count)
            (0 until manifest.count).map { PageRef(it,"offline:$it") }
        } else online.pages(comicId)
    }
    private fun local(id: String, page: PageRef): File {
        require(page.key == "offline:${page.index}" && store.hasPackage(id))
        return store.page(id,page.index).also { check(it.isFile) { "Página offline ausente. Remova e baixe novamente." } }
    }
    override suspend fun materialize(comicId: String,page: PageRef): File = withContext(Dispatchers.IO) {
        if (page.key.startsWith("offline:")) local(comicId,page) else online.materialize(comicId,page)
    }
    override suspend fun thumbnail(comicId: String,page: PageRef): File = withContext(Dispatchers.IO) {
        if (page.key.startsWith("offline:")) makeThumbnail(comicId,page.index,local(comicId,page)) else online.thumbnail(comicId,page)
    }
    override suspend fun prefetch(comicId: String,page: PageRef): File = withContext(Dispatchers.IO) {
        if (page.key.startsWith("offline:")) local(comicId,page) else online.prefetch(comicId,page)
    }
}

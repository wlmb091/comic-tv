package com.pelican.comictv.library

import com.pelican.comictv.model.ComicPageProvider
import com.pelican.comictv.model.PageRef
import java.io.File

interface ReaderRepository : ComicPageProvider {
    suspend fun thumbnail(comicId: String, page: PageRef): File
    suspend fun getComic(id: String): ComicEntity
    suspend fun saveProgress(id: String, page: Int)
    suspend fun savePreferences(id: String, fit: Int, rtl: Boolean)
}

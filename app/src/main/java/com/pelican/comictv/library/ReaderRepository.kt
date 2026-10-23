package com.pelican.comictv.library

import com.pelican.comictv.model.ComicPageProvider

interface ReaderRepository : ComicPageProvider {
    suspend fun getComic(id: String): ComicEntity
    suspend fun saveProgress(id: String, page: Int)
    suspend fun savePreferences(id: String, fit: Int, rtl: Boolean)
}

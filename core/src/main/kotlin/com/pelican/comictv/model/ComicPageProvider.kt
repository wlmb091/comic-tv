package com.pelican.comictv.model

import java.io.File

data class PageRef(val index: Int, val key: String)

interface ComicPageProvider {
    suspend fun pages(comicId: String): List<PageRef>
    suspend fun materialize(comicId: String, page: PageRef): File
}

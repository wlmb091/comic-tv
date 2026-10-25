package com.pelican.comictv.mihon

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.pelican.comictv.sources.OnlineChapter
import eu.kanade.tachiyomi.source.model.Page

class ChapterMetadataTest {
    @Test fun refreshSignalRetriesOnceWithExactChapterButOtherErrorsDoNotRefresh() = runBlocking {
        var refreshed=0
        var fetched=0
        val chapter=OnlineChapter("same","Chapter")
        val pages=readChapterPages(chapter, {refreshed++;listOf(chapter.copy(memo="{\"mangaId\":\"id\"}"))}) {
            fetched++
            if (it.memo["mangaId"] == null) throw Exception("Refresh Chapter List")
            listOf(Page(0,imageUrl="https://example.com/page.jpg"))
        }
        assertEquals(1,pages.size);assertEquals(1,refreshed);assertEquals(2,fetched)
        try {readChapterPages(chapter,{refreshed++;emptyList()}) {throw java.io.IOException("HTTP 403")};fail("Expected error")}
        catch(e:java.io.IOException){assertEquals("HTTP 403",e.message)}
        assertEquals(1,refreshed)
    }
    @Test fun allMangaMemoAndScanlatorSurviveChapterMapping() {
        val chapter = SChapter.create().apply {
            url = "/chapter/272"; name = "Chapter 272"; chapter_number = 272f
            scanlator = "Group"; date_upload = 123
            memo = buildJsonObject { put("mangaId", "series-id"); put("legacy", false) }
        }
        val online = chapter.toOnlineChapter()
        val restored = online.toSourceChapter()
        assertEquals(chapter.memo, restored.memo)
        assertEquals("series-id", restored.memo["mangaId"]?.jsonPrimitive?.content)
        assertEquals(chapter.scanlator, restored.scanlator)
        assertEquals(chapter.date_upload, restored.date_upload)
        assertEquals(chapter.url, restored.url)
    }
}

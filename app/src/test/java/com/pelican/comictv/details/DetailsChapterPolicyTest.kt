package com.pelican.comictv.details

import com.pelican.comictv.library.ComicEntity
import com.pelican.comictv.sources.*
import org.junit.Assert.*
import org.junit.Test

class DetailsChapterPolicyTest {
    private val source = SourceHandle("source.a", 7, "Fonte", "pt", "https://example.org")
    private val title = OnlineTitle("series", "Saga")
    private val one = OnlineChapter("one", "Capítulo 1", 1f)
    private val two = OnlineChapter("two", "Capítulo 2", 2f)
    private val context = OnlineEditions(source, title, one, listOf(two, one), "pin")
    private fun comic(chapter: OnlineChapter, last: Long = 0, page: Int = 0, count: Int = 10) =
        ComicEntity(OnlineIdentity.id(source, title, chapter), chapter.title, count, currentPage = page, lastRead = last)

    @Test fun statusRequiresRealReadAndKnownEnd() {
        assertEquals("Não lido", chapterReadStatus(null))
        assertEquals("Não lido", chapterReadStatus(comic(one, page = 9)))
        assertEquals("Em andamento", chapterReadStatus(comic(one, 1, 3)))
        assertEquals("Em andamento", chapterReadStatus(comic(one, 1, 0, 0)))
        assertEquals("Lido", chapterReadStatus(comic(one, 1, 9)))
    }
    @Test fun identicalNamesAndChapterKeysFromOtherSourceNeverShareProgress() {
        val other = comic(one, 500, 9).copy(id = OnlineIdentity.id(source.copy(packageName = "source.b"), title, one))
        val otherTitle = comic(one, 600, 9).copy(id = OnlineIdentity.id(source, title.copy(key = "another"), one))
        val rows = detailChapters(context, ChapterOrder.Ascending, listOf(other, otherTitle, comic(two, 10, 2)))
        assertNull(rows.first().progress)
        assertEquals("Em andamento", chapterReadStatus(rows.last().progress))
        assertEquals(comic(two).id, chapterResumeId(rows, comic(one).id))
    }
    @Test fun resumeUsesLastActualReadEvenWhenCompletedAndOrderChanges() {
        val rows = detailChapters(context, ChapterOrder.Descending, listOf(comic(one, 200, 9), comic(two, 100, 1)))
        assertEquals(listOf(two, one), rows.map { it.chapter })
        assertEquals(comic(one).id, chapterResumeId(rows, comic(two).id))
    }
    @Test fun sourceOrderingAndMissingCurrentFallbackAreExplicit() {
        val rows = detailChapters(context, ChapterOrder.Source, emptyList())
        assertEquals(listOf(two, one), rows.map { it.chapter })
        assertEquals(comic(one).id, chapterResumeId(rows, comic(one).id))
        assertEquals(comic(two).id, chapterResumeId(rows, "missing"))
        assertNull(chapterResumeId(emptyList(), "missing"))
    }
    @Test fun chapterReturnIsNotConsumableByOutgoingDetailsAnimation() {
        var state = ChapterFocusReturn().readerOpening("selected")
        assertNull(state.restoreId)
        state = state.resumed() // Already RESUMED before navigation is not a return.
        assertNull(state.restoreId)
        state = state.consumed() // Outgoing panel cannot consume the pending destination.
        assertEquals("selected", state.pendingId)
        state = state.paused()
        assertNull(state.restoreId)
        state = state.resumed()
        assertEquals("selected", state.restoreId)
        assertNull(state.pendingId)
        assertNull(state.consumed().resumed().restoreId)
    }
    @Test fun missingSourceOrOpenFailureDoesNotArmAnUnreachableReturn() {
        // readerOpening is called only after openChapter succeeds, directly before navigation.
        val state = ChapterFocusReturn()
        assertNull(state.pendingId)
        assertNull(state.paused().resumed().restoreId)
        val navigationFailed = state.readerOpening("selected").let { ChapterFocusReturn() }
        assertNull(navigationFailed.paused().resumed().restoreId)
    }
}

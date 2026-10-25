package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class ChapterOrderTest {
    @Test fun defaultZeroExtrasStayLastButExplicitChapterZeroIsPreserved() {
        val chapters=listOf(OnlineChapter("extra","Especial"),OnlineChapter("two","Chapter 2",2f),
            OnlineChapter("zero","Chapter 0"),OnlineChapter("one","Chapter 1",1f))
        assertEquals(listOf("zero","one","two","extra"),orderedChapters(chapters,ChapterOrder.Ascending).map{it.key})
        assertEquals(listOf("two","one","zero","extra"),orderedChapters(chapters,ChapterOrder.Descending).map{it.key})
    }
    @Test fun numericOrderHandlesFractionsUnknownExtrasAndDuplicateKeysWithoutChangingChapterData() {
        val extra=OnlineChapter("extra","Especial de 2026",-1f)
        val ten=OnlineChapter("ten","Chapter 10",10f,memo="{\"mangaId\":\"keep\"}")
        val two=OnlineChapter("two","Chapter 2",2f)
        val half=OnlineChapter("half","Capítulo 2,5",-1f)
        val one=OnlineChapter("one","Chapter 1",0f)
        val chapters=listOf(extra,ten,two,half,one,ten.copy(title="Duplicate"))
        assertEquals(listOf("one","two","half","ten","extra"),orderedChapters(chapters,ChapterOrder.Ascending).map{it.key})
        assertEquals(listOf("ten","half","two","one","extra"),orderedChapters(chapters,ChapterOrder.Descending).map{it.key})
        assertEquals(listOf(extra,ten,two,half,one),orderedChapters(chapters,ChapterOrder.Source))
        assertSame(ten,orderedChapters(chapters,ChapterOrder.Ascending)[3])
        assertEquals(6,chapters.size)
    }
    @Test fun equalNumbersKeepSourceOrderAndInvalidNumbersDoNotCrash() {
        val chapters=listOf(OnlineChapter("a","Chapter 4",4f),OnlineChapter("b","Chapter 4",4f),
            OnlineChapter("c","Extra",Float.NaN),OnlineChapter("d","Unknown",Float.POSITIVE_INFINITY))
        assertEquals(chapters,orderedChapters(chapters,ChapterOrder.Ascending))
        assertEquals(chapters,orderedChapters(chapters,ChapterOrder.Descending))
    }
}

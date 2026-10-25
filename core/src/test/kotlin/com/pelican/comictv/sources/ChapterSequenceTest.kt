package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class ChapterSequenceTest {
    private val first = OnlineChapter("a", "Edition 20", 20f)
    private val middle = OnlineChapter("b", "Edition 2", 2f)
    private val last = OnlineChapter("c", "Special", 0f)

    @Test fun preservesSourceOrderAndMatchesCurrentByKey() {
        val sequence = chapterSequence(listOf(first, middle, last), middle.copy(title = "Renamed").key)
        assertEquals(listOf(first, middle, last), sequence.chapters)
        assertEquals(1, sequence.index)
        assertEquals(first, sequence.previous)
        assertEquals(last, sequence.next)
    }

    @Test fun reversesOnlyWhenExplicitlyRequested() {
        val sequence = chapterSequence(listOf(first, middle, last), "b", reversed = true)
        assertEquals(listOf(last, middle, first), sequence.chapters)
        assertEquals(last, sequence.previous)
        assertEquals(first, sequence.next)
    }

    @Test fun keepsFirstDuplicateBeforeReversing() {
        val sequence = chapterSequence(listOf(first, middle, first.copy(title = "Duplicate"), last), "a", true)
        assertEquals(listOf(last, middle, first), sequence.chapters)
        assertEquals(2, sequence.index)
        assertEquals(middle, sequence.previous)
        assertNull(sequence.next)
    }

    @Test fun doesNotGuessNeighborsForMissingOrEmptyCurrent() {
        for (chapters in listOf(emptyList(), listOf(first, middle, last))) {
            val sequence = chapterSequence(chapters, "missing")
            assertEquals(-1, sequence.index)
            assertNull(sequence.previous)
            assertNull(sequence.next)
        }
    }

    @Test fun endsHaveOnlyTheirExistingNeighbor() {
        assertNull(chapterSequence(listOf(first, middle), "a").previous)
        assertEquals(middle, chapterSequence(listOf(first, middle), "a").next)
        assertNull(chapterSequence(listOf(first, middle), "b").next)
        assertEquals(first, chapterSequence(listOf(first, middle), "b").previous)
        assertNull(chapterSequence(listOf(first), "a").next)
        assertNull(chapterSequence(listOf(first), "a").previous)
    }

    @Test fun rejectsExcessiveSourceListsBeforeDeduplication() {
        assertEquals(1, chapterSequence(List(10_000) { first }, "a").chapters.size)
        assertThrows(IllegalArgumentException::class.java) {
            chapterSequence(List(10_001) { first }, "a")
        }
    }
    @Test fun numericOrdersDriveNeighborsAndSourceOptionPreservesOriginalOrder() {
        val one = OnlineChapter("one", "Chapter 01", 1f)
        val two = OnlineChapter("two", "Chapter 02", 2f, memo = "keep")
        val half = OnlineChapter("half", "Capítulo 2,5", -1f)
        val ten = OnlineChapter("ten", "Chapter 10", 10f)
        val raw = listOf(ten, half, two, one)
        val ascending = chapterSequence(raw, one.key, ChapterOrder.Ascending)
        assertNull(ascending.previous); assertSame(two, ascending.next)
        assertEquals(half, chapterSequence(raw, two.key, ChapterOrder.Ascending).next)
        assertEquals(two, chapterSequence(raw, half.key, ChapterOrder.Descending).next)
        assertNull(chapterSequence(raw, one.key, ChapterOrder.Source).next)
        assertEquals(raw, chapterSequence(raw, one.key, ChapterOrder.Source).chapters)
    }
    @Test fun absentOrInvalidNamedPreferenceDefaultsToAscending() {
        assertEquals(ChapterOrder.Ascending, restoreChapterOrder(null))
        assertEquals(ChapterOrder.Ascending, restoreChapterOrder("invalid"))
        assertEquals(ChapterOrder.Source, restoreChapterOrder("Source"))
        assertEquals(ChapterOrder.Descending, restoreChapterOrder("Descending"))
    }
}

package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class NarrationTextTest {
    @Test fun segmentsKeepSourceOffsetsAndRespectUnicodeAndLimits() {
        val text = "  Olá, mundo!\n\n" + "Uma frase 😀 com acentos. ".repeat(40)
        val segments = speechSegments(text, 31)
        assertEquals("Olá, mundo!", text.substring(segments.first().start, segments.first().end))
        segments.forEach {
            val chunk = text.substring(it.start, it.end)
            assertTrue(chunk.isNotBlank())
            assertTrue(chunk.length <= 31)
            assertFalse(Character.isLowSurrogate(chunk.first()))
            assertFalse(Character.isHighSurrogate(chunk.last()))
        }
        assertEquals(text.filterNot(Char::isWhitespace), segments.joinToString("") { text.substring(it.start, it.end) }.filterNot(Char::isWhitespace))
        assertTrue(speechSegments(" \n\t").isEmpty())
    }
    @Test fun oldCallbacksCannotAdvanceOrHighlightAfterPauseAndNavigation() {
        val cursor = SpeechCursor("book-page", speechSegments("Primeiro.\nSegundo."))
        val first = cursor.begin()!!
        assertEquals(SpeechSegment(0, 4), cursor.range(first, 0, 4))
        cursor.pause()
        assertEquals(SpeechCompletion.Stale, cursor.done(first))
        assertNull(cursor.range(first, 0, 3))
        val replay = cursor.begin()!!
        assertNotEquals(first, replay)
        assertEquals(SpeechCompletion.Next, cursor.done(replay))
        val second = cursor.begin()!!
        assertEquals(SpeechCompletion.Finished, cursor.done(second))
        assertEquals(SpeechCompletion.Stale, cursor.done(second))
        cursor.move(-1)
        assertNotNull(cursor.current)
    }
    @Test fun resumeReturnsToSegmentStartAndDigestDetectsChangedText() {
        val text = "Primeiro.\nSegundo."
        val cursor = SpeechCursor("page", speechSegments(text), 13)
        assertEquals(10, cursor.current!!.start)
        val id = cursor.begin()!!
        assertNull(cursor.range(id, -1, 4))
        assertNull(cursor.range(id, 3, 2))
        assertEquals(narrationDigest(text), narrationDigest(text))
        assertNotEquals(narrationDigest(text), narrationDigest(text + "!"))
    }
}

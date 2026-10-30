package com.pelican.comictv.library

import org.junit.Assert.*
import org.junit.Test

class SagaEditionPageTest {
    private val editions = listOf(
        ComicEntity("a", "Primeira", 20),
        ComicEntity("b", "Segunda", 20, currentPage = 8, lastRead = 40),
        ComicEntity("c", "Terceira", 20, currentPage = 19, lastRead = 30),
    )

    @Test fun returnRestoresExplicitEditionBeforeLatestReading() {
        assertEquals("a", sagaEditionTarget(editions, "a"))
        assertEquals("b", sagaEditionTarget(editions, "removed"))
        assertNull(sagaEditionTarget(emptyList(), null))
    }

    @Test fun unreadSinglePageIsNotMarkedRead() {
        assertEquals("Não iniciado", sagaEditionStatus(ComicEntity("one", "Uma", 1)))
        assertTrue(sagaEditionStatus(editions[1]).startsWith("Em leitura"))
        assertTrue(sagaEditionStatus(editions[2]).startsWith("Lido"))
    }

    @Test fun largeTextReducesColumnsAndNarrowScreenStillHasOne() {
        assertTrue(sagaEditionColumns(900f, 1.3f) < sagaEditionColumns(900f, 1f))
        assertEquals(1, sagaEditionColumns(120f, 1.3f))
    }
}

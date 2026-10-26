package com.pelican.comictv.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderToolNavigationTest {
    @Test fun allOptionalToolCountsRemainReachableWithoutLeavingDock() {
        for (count in 1..12) {
            val visited = mutableSetOf(0)
            val pending = ArrayDeque<Int>().apply { add(0) }
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                for (direction in ToolDirection.entries) {
                    val next = readerToolNeighbor(current, count, direction) ?: continue
                    assertTrue(next in 0 until count)
                    if (visited.add(next)) pending.add(next)
                }
            }
            assertEquals((0 until count).toSet(), visited)
        }
    }
    @Test fun verticalRailMovesOneToolAtATimeAndHorizontalArrowsStayInPlace() {
        assertEquals(1, readerToolNeighbor(0, 9, ToolDirection.Down))
        assertEquals(8, readerToolNeighbor(7, 9, ToolDirection.Down))
        assertEquals(7, readerToolNeighbor(8, 9, ToolDirection.Up))
        assertNull(readerToolNeighbor(0, 9, ToolDirection.Right))
        assertNull(readerToolNeighbor(8, 9, ToolDirection.Right))
        assertNull(readerToolNeighbor(0, 9, ToolDirection.Left))
        assertNull(readerToolNeighbor(0, 9, ToolDirection.Up))
        assertNull(readerToolNeighbor(8, 9, ToolDirection.Down))
    }
}

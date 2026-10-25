package com.pelican.comictv.reader

internal enum class ToolDirection { Up, Down, Left, Right }

internal fun readerToolNeighbor(index: Int, count: Int, direction: ToolDirection): Int? {
    if (index !in 0 until count) return null
    return when (direction) {
        ToolDirection.Left -> if (index % 2 == 1) index - 1 else null
        ToolDirection.Right -> if (index % 2 == 0 && index + 1 < count) index + 1 else null
        ToolDirection.Up -> (index - 2).takeIf { it >= 0 }
        ToolDirection.Down -> if ((index / 2 + 1) * 2 < count) minOf(index + 2, count - 1) else null
    }
}

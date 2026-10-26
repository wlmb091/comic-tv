package com.pelican.comictv.reader

internal enum class ToolDirection { Up, Down, Left, Right }

internal fun readerToolNeighbor(index: Int, count: Int, direction: ToolDirection): Int? {
    if (index !in 0 until count) return null
    return when (direction) {
        ToolDirection.Left, ToolDirection.Right -> null
        ToolDirection.Up -> (index - 1).takeIf { it >= 0 }
        ToolDirection.Down -> (index + 1).takeIf { it < count }
    }
}

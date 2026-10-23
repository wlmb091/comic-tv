package com.pelican.comictv.reader

enum class ReaderMode { Reading, Tools, Pan }
enum class FitMode { Page, Width, Height }
enum class ReaderAction { Left, Right, Up, Down, Select, Back, StartPan, ToggleFit, ToggleRtl }

data class ReaderState(
    val page: Int = 0,
    val pageCount: Int = 0,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val mode: ReaderMode = ReaderMode.Reading,
    val fit: FitMode = FitMode.Page,
    val rtl: Boolean = false,
) {
    fun reduce(action: ReaderAction): ReaderState {
        if (action == ReaderAction.Back) return copy(mode = if (mode == ReaderMode.Pan) ReaderMode.Tools else ReaderMode.Reading)
        if (action == ReaderAction.Select) return copy(mode = ReaderMode.Tools)
        if (action == ReaderAction.StartPan) return copy(mode = ReaderMode.Pan)
        if (action == ReaderAction.ToggleFit) return copy(fit = FitMode.entries[(fit.ordinal + 1) % FitMode.entries.size], panX = 0f, panY = 0f)
        if (action == ReaderAction.ToggleRtl) return copy(rtl = !rtl)
        if (mode == ReaderMode.Tools) return this
        if (mode == ReaderMode.Pan) return when (action) {
            ReaderAction.Left -> copy(panX = (panX - .15f).coerceAtLeast(-1f))
            ReaderAction.Right -> copy(panX = (panX + .15f).coerceAtMost(1f))
            ReaderAction.Up -> copy(panY = (panY - .15f).coerceAtLeast(-1f))
            ReaderAction.Down -> copy(panY = (panY + .15f).coerceAtMost(1f))
            else -> this
        }
        return when (action) {
            ReaderAction.Left, ReaderAction.Right -> {
                val delta = (if (action == ReaderAction.Right) 1 else -1) * (if (rtl) -1 else 1)
                val next = (page + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                if (next == page) this else copy(page = next, zoom = 1f, panX = 0f, panY = 0f)
            }
            ReaderAction.Up -> copy(zoom = (zoom + .25f).coerceAtMost(3f))
            ReaderAction.Down -> copy(zoom = (zoom - .25f).coerceAtLeast(1f), panX = 0f, panY = 0f)
            else -> this
        }
    }
    companion object {
        fun restore(page: Int, count: Int) = ReaderState(page.coerceIn(0, (count - 1).coerceAtLeast(0)), count.coerceAtLeast(0))
    }
}

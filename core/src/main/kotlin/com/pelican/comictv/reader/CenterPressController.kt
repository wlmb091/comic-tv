package com.pelican.comictv.reader

/** Center gestures: tap < 1s; hold >= 2s. Releases between those thresholds do nothing. */
class CenterPressController {
    private var startedAt: Long? = null
    private var held = false
    val isPressed: Boolean get() = startedAt != null

    fun down(now: Long): Boolean {
        if (isPressed) return false
        startedAt = now
        held = false
        return true
    }

    fun hold(now: Long): ReaderAction? {
        val start = startedAt ?: return null
        if (held || now - start < 2000) return null
        held = true
        return ReaderAction.OpenTools
    }

    fun up(now: Long, mode: ReaderMode): ReaderAction? {
        val start = startedAt ?: return null
        val action = if (!held && now - start < 1000) {
            when (mode) {
                ReaderMode.Reading, ReaderMode.Pan -> ReaderAction.TogglePan
                ReaderMode.Timeline -> ReaderAction.Select
                ReaderMode.Tools -> null
            }
        } else if (!held && now - start >= 2000) ReaderAction.OpenTools else null
        cancel()
        return action
    }

    fun cancel() { startedAt = null; held = false }
}

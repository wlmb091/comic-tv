package com.pelican.comictv.browser

/** The open TV browser lends its pointer options to the phone panel; closed, the panel hides those controls. Main thread only. */
internal object BrowserCursorBridge {
    class Handle(val read: () -> Pair<Int, Int>, val apply: (size: Int, style: Int) -> Unit)
    var handle: Handle? = null
}

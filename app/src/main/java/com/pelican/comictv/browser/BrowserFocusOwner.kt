package com.pelican.comictv.browser

/** The native Home is content, not an empty WebView. Dialogs own their own focus. */
internal enum class BrowserFocusOwner { None, Controls, Home, Page }
internal fun browserFocusOwner(ready: Boolean, url: String, controlsVisible: Boolean, blocked: Boolean): BrowserFocusOwner = when {
    !ready || blocked -> BrowserFocusOwner.None
    controlsVisible -> BrowserFocusOwner.Controls
    url.isBlank() -> BrowserFocusOwner.Home
    else -> BrowserFocusOwner.Page
}

/** Home is the browser's first level: with its controls closed there is nothing left for Back to dismiss. */
internal fun browserBackLeaves(url: String, controlsVisible: Boolean): Boolean = url.isBlank() && !controlsVisible

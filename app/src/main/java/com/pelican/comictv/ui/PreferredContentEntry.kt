package com.pelican.comictv.ui

import androidx.compose.runtime.*

internal class ContentEntryTarget {
    private var owner: Any? = null
    private var request: (() -> Boolean)? = null
    fun register(owner: Any, request: () -> Boolean) { this.owner = owner; this.request = request }
    fun unregister(owner: Any) { if (this.owner === owner) { this.owner = null; request = null } }
    fun requestFocus(): Boolean = request?.invoke() == true
}

internal val LocalPreferredContentEntry = staticCompositionLocalOf<ContentEntryTarget?> { null }

/** Register only real, visible controls; a loading/offscreen control keeps the stable fallback. */
@Composable internal fun PreferredContentEntry(request: () -> Boolean) {
    val target = LocalPreferredContentEntry.current ?: return
    val owner = remember { Any() }
    val current by rememberUpdatedState(request)
    DisposableEffect(target, owner) {
        target.register(owner) { current() }
        onDispose { target.unregister(owner) }
    }
}

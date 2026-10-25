package com.pelican.comictv.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch

internal class ContentEntryTarget {
    private var owner: Any? = null
    private var request: (() -> Boolean)? = null
    private var prepare: (() -> Unit)? = null
    private var pending = false
    private var resetPending = false
    private var preparedOwner: Any? = null
    fun register(owner: Any, request: () -> Boolean, prepare: () -> Unit = {}) {
        this.owner = owner; this.request = request; this.prepare = prepare
    }
    fun unregister(owner: Any) {
        if (this.owner === owner) { this.owner = null; request = null; prepare = null; preparedOwner = null }
    }
    fun requestFocus(resetToFirst: Boolean = false): Boolean {
        pending = true
        resetPending = resetToFirst
        preparedOwner = null
        return retry()
    }
    fun retry(): Boolean {
        if (!pending || request == null) return false
        if (resetPending && preparedOwner !== owner) { preparedOwner = owner; prepare?.invoke() }
        val focused = request?.invoke() == true
        if (focused) cancel()
        return focused
    }
    fun cancel() { pending = false; resetPending = false; preparedOwner = null }
}

internal val LocalPreferredContentEntry = staticCompositionLocalOf<ContentEntryTarget?> { null }
internal val LocalSectionContentEntry = staticCompositionLocalOf { false }

/** Register only real, visible controls; a loading/offscreen control keeps the stable fallback. */
@Composable internal fun PreferredContentEntry(prepare: suspend () -> Unit = {}, request: () -> Boolean) {
    val target = LocalPreferredContentEntry.current ?: return
    val owner = remember { Any() }
    val current by rememberUpdatedState(request)
    val preparation by rememberUpdatedState(prepare)
    val scope = rememberCoroutineScope()
    DisposableEffect(target, owner) {
        target.register(owner, { current() }, { scope.launch { preparation(); target.retry() } })
        onDispose { target.unregister(owner) }
    }
    SideEffect { target.retry() }
}

/** Register outside lazy items, attach only to the first real control, and wait for its layout. */
@Composable internal fun preferredContentEntryModifier(enabled: Boolean = true,
    prepare: suspend () -> Unit = {}): Modifier {
    val focus = remember { FocusRequester() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val target = LocalPreferredContentEntry.current
    PreferredContentEntry(prepare) {
        val position = coordinates
        if (!enabled || position?.isAttached != true) false else {
            val bounds = position.boundsInWindow()
            if (bounds.width < 1 || bounds.height < 1 || bounds.width + 1 < position.size.width ||
                bounds.height + 1 < position.size.height) false
            else focus.requestFocus()
        }
    }
    return Modifier.focusRequester(focus).onGloballyPositioned { coordinates = it; target?.retry() }
}

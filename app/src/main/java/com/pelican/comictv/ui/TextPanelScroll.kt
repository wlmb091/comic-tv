package com.pelican.comictv.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.launch

/** Text keeps scrolling even while a button inside the panel owns the focus. */
@Composable internal fun Modifier.textPanelScroll(state: LazyListState): Modifier {
    var focusedWithin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return onFocusChanged { focusedWithin = it.hasFocus }.onPreviewKeyEvent { event ->
        val delta = when {
            !focusedWithin || event.type != KeyEventType.KeyDown -> 0f
            event.key == Key.DirectionDown && state.canScrollForward -> 120f
            event.key == Key.DirectionUp && state.canScrollBackward -> -120f
            else -> 0f
        }
        if (delta == 0f) false else { scope.launch { state.scrollBy(delta) }; true }
    }.focusable()
}

package com.pelican.comictv.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

/** Stable focus destination outside scrollable/lazy content, including while data is replaced. */
@Composable internal fun TvContentEntry(entry: FocusRequester, sidebar: FocusRequester,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val children = remember { FocusRequester() }
    var atEntry by remember { mutableStateOf(false) }
    var enteringKey by remember { mutableStateOf<Key?>(null) }
    Box(modifier.focusRequester(entry)
        .onFocusChanged { atEntry = it.isFocused; if (!it.hasFocus) enteringKey = null }
        .onPreviewKeyEvent { event ->
            if (enteringKey == event.key) {
                if (event.type == KeyEventType.KeyUp) enteringKey = null
                true // Do not deliver OK-up to a newly focused button/text field.
            } else if (!atEntry) false
            else when (event.key) {
                Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyDown) {
                        enteringKey = event.key
                        children.requestFocus()
                    }
                    true
                }
                Key.DirectionLeft -> { if (event.type == KeyEventType.KeyDown) sidebar.requestFocus(); true }
                Key.DirectionUp, Key.DirectionRight -> true
                else -> false
            }
        }.testTag("content-entry").semantics {
            contentDescription = "Conteúdo"
            onClick("Entrar no conteúdo") { children.requestFocus(); true }
        }.focusable()) {
        Box(Modifier.fillMaxSize().focusRequester(children).focusGroup()) { content() }
    }
}

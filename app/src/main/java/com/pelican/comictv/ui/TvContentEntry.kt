package com.pelican.comictv.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

/** Stable focus destination outside scrollable/lazy content, including while data is replaced. */
@Composable internal fun TvContentEntry(entry: FocusRequester, sidebar: FocusRequester,
    modifier: Modifier = Modifier, onEntryLeft: () -> Unit = {},
    navigationPolicy: ThemeNavigationPolicy = ThemeNavigationPolicy.Sidebar,
    children: FocusRequester = remember { FocusRequester() },
    onNavigateToMenu: () -> Unit = { sidebar.requestFocus() }, content: @Composable () -> Unit) {
    var atEntry by remember { mutableStateOf(false) }
    var enteringKey by remember { mutableStateOf<Key?>(null) }
    Box(modifier.focusRequester(entry)
        .onFocusChanged {
            if (atEntry && !it.isFocused) onEntryLeft()
            atEntry = it.isFocused
            if (!it.hasFocus) enteringKey = null
        }
        .onPreviewKeyEvent { event ->
            if (enteringKey == event.key) {
                if (event.type == KeyEventType.KeyUp) enteringKey = null
                true // Do not deliver OK-up to a newly focused button/text field.
            } else if (!atEntry) false
            else if (event.key == when (navigationPolicy) {
                ThemeNavigationPolicy.TopBar -> Key.DirectionUp
                ThemeNavigationPolicy.BottomDock -> Key.DirectionDown
                ThemeNavigationPolicy.ImmersiveMenu -> Key.DirectionRight
                else -> Key.DirectionLeft
            }) {
                if (event.type == KeyEventType.KeyDown) onNavigateToMenu()
                true
            } else if (event.key == (if (navigationPolicy == ThemeNavigationPolicy.BottomDock) Key.DirectionUp else Key.DirectionDown) ||
                event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                    if (event.type == KeyEventType.KeyDown) {
                        enteringKey = event.key
                        children.requestFocus()
                    }
                    true
            } else event.key == Key.DirectionLeft || event.key == Key.DirectionUp || event.key == Key.DirectionRight
        }.testTag("content-entry").semantics {
            contentDescription = "Conteúdo"
            onClick("Entrar no conteúdo") { children.requestFocus(); true }
        }.focusable()) {
        Box(Modifier.fillMaxSize().focusRequester(children).focusProperties {
            onExit = {
                val menuDirection = when (navigationPolicy) {
                    ThemeNavigationPolicy.TopBar -> FocusDirection.Up
                    ThemeNavigationPolicy.BottomDock -> FocusDirection.Down
                    ThemeNavigationPolicy.ImmersiveMenu -> FocusDirection.Right
                    else -> FocusDirection.Left
                }
                if (requestedFocusDirection == menuDirection) onNavigateToMenu()
            }
        }.focusGroup()) { content() }
    }
}

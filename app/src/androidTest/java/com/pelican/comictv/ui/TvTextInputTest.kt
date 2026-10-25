package com.pelican.comictv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvTextInputTest {
    @get:Rule val compose = createComposeRule()
    @Test fun focusOnlyHighlightsAndOkExplicitlyRequestsKeyboard() {
        val focus = FocusRequester()
        var requests = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { requests++ }
            override fun hide() = Unit
        }
        compose.setContent {
            ComicTvTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    TvTextInput("", {}, Modifier.focusRequester(focus).testTag("input"))
                }
            }
        }
        compose.runOnIdle { focus.requestFocus() }
        compose.onNodeWithTag("input").assertIsFocused()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("input").performKeyInput { keyDown(Key.DirectionCenter); keyUp(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, requests) }
    }
    @Test fun editingAndExternalClearStaySynchronizedAndFocusCanLeave() {
        val focus = FocusRequester()
        val next = FocusRequester()
        var value by mutableStateOf("old")
        compose.setContent {
            ComicTvTheme {
                Column {
                    TvTextInput(value, { value = it }, Modifier.focusRequester(focus).testTag("input"))
                    TvButton("Next", {}, Modifier.focusRequester(next))
                }
            }
        }
        compose.onNodeWithTag("input").performClick().performTextReplacement("new")
        compose.waitUntil { value == "new" }
        compose.runOnIdle { value = "" }
        compose.onNodeWithTag("input").assertTextEquals("")
        compose.runOnIdle { next.requestFocus() }
        compose.onNodeWithText("Next").assertIsFocused()
    }
    @Test fun editAtLimitDoesNotDisplayTextThatParentCannotSave() {
        var value by mutableStateOf("ab")
        compose.setContent {
            ComicTvTheme {
                TvTextInput(value, { value = it.take(2) }, Modifier.testTag("input"), maxLength = 2)
            }
        }
        compose.onNodeWithTag("input").performClick().performTextInput("c")
        compose.onNodeWithTag("input").assertTextEquals("ab")
        compose.runOnIdle { assertEquals("ab", value) }
    }
}

package com.pelican.comictv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class TvChoiceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusDoesNotChangeTheSelectedChoice() {
        val other = FocusRequester()
        compose.setContent {
            var selected by remember { mutableStateOf("Todas") }
            ComicTvTheme {
                Row {
                    TvChoice("Todas", selected == "Todas", { selected = "Todas" })
                    TvChoice("Favoritos", selected == "Favoritos", { selected = "Favoritos" }, Modifier.focusRequester(other))
                }
            }
        }
        compose.runOnIdle { other.requestFocus() }
        compose.onNodeWithText("Favoritos").assertIsFocused().assertIsNotSelected()
        compose.onNodeWithText("Todas").assertIsSelected()
        compose.onNodeWithText("Favoritos").performClick().assertIsSelected()
        compose.onNodeWithText("Todas").assertIsNotSelected()
    }
}

package com.pelican.comictv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CinemaChoiceFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun persistentSelectionsNeverCreateMultipleRemoteFocusTargets() {
        val first = FocusRequester()
        compose.setContent {
            ComicTvTheme(AppearanceOptions(preset = ColorPreset.Cinema, textPercent = 130)) {
                Row {
                    TvChoice("Biblioteca", true, {}, Modifier.focusRequester(first))
                    TvChoice("Todas", true, {})
                    TvButton("Buscar", {})
                }
                LaunchedEffect(Unit) { first.requestFocus() }
            }
        }
        compose.onNodeWithText("Biblioteca").assertIsSelected().assertIsFocused()
        compose.onNodeWithText("Todas").assertIsSelected().assertIsNotFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
        compose.onNodeWithText("Biblioteca").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Todas").assertIsFocused()
        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }
}

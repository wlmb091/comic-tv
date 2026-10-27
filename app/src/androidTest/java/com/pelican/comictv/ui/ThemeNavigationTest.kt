package com.pelican.comictv.ui

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ThemeNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun setShell(preset: ColorPreset) {
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = preset, textPercent = 130, highContrast = true)) {
            var pending by remember { mutableStateOf(true) }
            TvShell("library", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                TvButton("Primeiro controle", {}, preferredContentEntryModifier())
            }
        } }
    }

    private fun assertWordmarkFits() {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("COMIC TV").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Marca inteira com texto em130%", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
    }

    @Test fun primeMovesUpToCurrentSectionAndDownToContent() {
        setShell(ColorPreset.Prime)
        assertWordmarkFits()
        compose.onNodeWithTag("navigation-top").assertExists()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
    }

    @Test fun maxMovesDownToDockAndUpToContent() {
        setShell(ColorPreset.Max)
        compose.onNodeWithTag("navigation-bottom").assertExists()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
    }

    @Test fun cinemaLeftOpensFloatingMenuAndRightDismissesIt() {
        setShell(ColorPreset.Cinema)
        assertWordmarkFits()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
    }
}

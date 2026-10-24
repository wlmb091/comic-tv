package com.pelican.comictv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LibraryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun coldStartKeepsSidebarCompactUntilAnIconReceivesFocus() {
        compose.onNodeWithText("Biblioteca ·", substring = true).assertIsFocused()
        compose.onNodeWithText("COMIC TV").assertDoesNotExist()
        compose.onNodeWithText("Biblioteca ·", substring = true).performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("nav-library").assertIsFocused()
        compose.onNodeWithText("COMIC TV").assertIsDisplayed()
        compose.onNodeWithTag("nav-library").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("COMIC TV").assertDoesNotExist()
    }

    @Test fun emptyLibraryExplainsImport() {
        compose.onNodeWithText("Sua coleção começa aqui").assertIsDisplayed()
        compose.onNodeWithText("Importar quadrinhos").performClick()
        compose.onNodeWithText("Arquivo local").assertIsDisplayed()
        compose.onNodeWithText("Pela web").assertIsDisplayed()
    }
}

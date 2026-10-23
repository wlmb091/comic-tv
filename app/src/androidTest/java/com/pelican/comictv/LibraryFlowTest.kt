package com.pelican.comictv

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test

class LibraryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun emptyLibraryExplainsImport() {
        compose.onNodeWithText("Sua coleção começa aqui").assertIsDisplayed()
        compose.onNodeWithText("Importar quadrinhos").performClick()
        compose.onNodeWithText("Arquivo local").assertIsDisplayed()
        compose.onNodeWithText("Pela web").assertIsDisplayed()
    }
}

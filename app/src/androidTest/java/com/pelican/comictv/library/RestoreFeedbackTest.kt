package com.pelican.comictv.library

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.backup.RestoreFeedbackDialog
import com.pelican.comictv.backup.RestoreResult
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Rule
import org.junit.Test

class RestoreFeedbackTest {
    @get:Rule val compose = createComposeRule()
    @Test fun resultIsVisibleAndFocusedUntilAcknowledged() {
        compose.setContent {
            var open by remember {mutableStateOf(true)}
            ComicTvTheme {
                if (open) RestoreFeedbackDialog(RestoreResult(0,6,0),false) {open = false}
            }
        }
        compose.onNodeWithText("Restauração concluída").assertIsDisplayed()
        compose.onNodeWithText("0 adicionadas · 6 mescladas").assertIsDisplayed()
        compose.onNodeWithText("Entendi").assertIsFocused().performClick()
        compose.onNodeWithText("Restauração concluída").assertDoesNotExist()
    }
    @Test fun failureDoesNotDisplaySuccess() {
        compose.setContent {ComicTvTheme {RestoreFeedbackDialog(null,true) {}}}
        compose.onNodeWithText("Restauração não concluída").assertIsDisplayed()
        compose.onNodeWithText("Restauração concluída").assertDoesNotExist()
        compose.onNodeWithText("Entendi").assertIsFocused()
    }
}

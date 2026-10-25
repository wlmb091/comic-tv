package com.pelican.comictv.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ImportedRemovalDialogTest {
    @Test fun onlineConfirmationExplainsOfflineAndCatalogAndStartsOnCancel() {
        compose.setContent {ComicTvTheme {ConfirmImportedRemoval("Online",{}, {true},{},online=true)}}
        compose.onNodeWithText("Cancelar").assertIsFocused()
        compose.onNodeWithText("download offline",substring=true).assertExists()
        compose.onNodeWithText("Excluir edição da biblioteca").assertExists()
        compose.onNodeWithText("Excluir arquivo importado").assertDoesNotExist()
    }
    @get:Rule val compose=createComposeRule()
    @Test fun confirmationStartsOnCancelAndDoesNotDeleteUntilConfirmed() {
        var calls=0
        compose.setContent {ComicTvTheme {ConfirmImportedRemoval("Book",{}, {calls++;true},{})}}
        compose.onNodeWithText("Cancelar").assertIsFocused().performClick()
        compose.runOnIdle {assertEquals(0,calls)}
        compose.onNodeWithText("Excluir arquivo importado").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(1,calls)}
    }
}

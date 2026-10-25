package com.pelican.comictv.library

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Rule
import org.junit.Test

class LiveLibrarySearchTest {
    @get:Rule val compose = createComposeRule()
    @Test fun matchingTitlesUpdateInsideDialogWithoutConfirmingSearch() {
        val entries = listOf(LibrarySearchEntry("a", "Spawn", false), LibrarySearchEntry("b", "The Boys", false))
        compose.setContent {
            var query by remember { mutableStateOf("") }
            val matches = filterLibrary(entries, query, "all", emptyMap()).toSet()
            ComicTvTheme {
                LibrarySearchDialog(query, { query = it }, entries.filter { it.id in matches }) {}
            }
        }
        compose.onNodeWithText("The Boys").assertExists()
        compose.onNodeWithContentDescription("Título do quadrinho").performTextReplacement("spawn")
        compose.onNodeWithText("Spawn").assertExists()
        compose.onNodeWithText("The Boys").assertDoesNotExist()
        compose.onNodeWithContentDescription("Título do quadrinho").performTextReplacement("")
        compose.onNodeWithText("The Boys").assertExists()
    }
}

package com.pelican.comictv.library

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryOrganizationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchOnlyOpensOnExplicitActionAndReturnsItsQuery() {
        var query = ""
        compose.setContent {
            var open by remember { mutableStateOf(false) }
            var value by remember { mutableStateOf("") }
            ComicTvTheme {
                TvButton("Buscar", { open = true })
                if (open) LibrarySearchDialog(value, { value = it; query = it }) { open = false }
            }
        }
        compose.onNodeWithContentDescription("Título do quadrinho").assertDoesNotExist()
        compose.onNodeWithText("Buscar").performClick()
        compose.onNodeWithContentDescription("Título do quadrinho").performTextInput("Órbita")
        compose.onNodeWithText("Ver resultados").performClick()
        compose.onNodeWithContentDescription("Título do quadrinho").assertDoesNotExist()
        compose.runOnIdle { assertEquals("Órbita", query) }
    }
}

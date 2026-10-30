package com.pelican.comictv.browser

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.ComicTvTheme
import com.pelican.comictv.ui.TvButton
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BrowserViewportTest {
    @get:Rule val compose = createComposeRule()
    @Test fun chromeOverlaysTheFullPageWithoutChangingItsBounds() {
        var chrome by mutableStateOf(false)
        compose.setContent { ComicTvTheme {
            Box(Modifier.size(640.dp, 360.dp)) {
                BrowserViewport(chrome, controls = { TvButton("Barra do navegador", {}) },
                    page = { Box(Modifier.fillMaxSize().testTag("site")) })
            }
        } }
        val before = compose.onNodeWithTag("site").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { chrome = true }
        compose.onNodeWithText("Barra do navegador").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("site").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { chrome = false }
        compose.onNodeWithText("Barra do navegador").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("site").fetchSemanticsNode().boundsInRoot)
    }
    @Test fun downloadsShowRealProgressAndSwitchActionsWithoutLeavingThePanel() {
        var row by mutableStateOf(BrowserDownloadItem("00000000-0000-0000-0000-000000000001", "https://example.org/book", "book.cbz", "A", bytes = 25, total = 100, status = BrowserDownloadStatus.Running))
        var command = ""
        compose.setContent { ComicTvTheme { BrowserDownloadsPanel(listOf(row), false, {}, action = { action, _ -> command = action }) } }
        compose.onNodeWithText("Baixando · 25%", substring = true).assertExists()
        compose.onNodeWithText("Pausar").performClick()
        compose.runOnIdle { assertEquals("pause", command); row = row.copy(bytes = 100, status = BrowserDownloadStatus.Complete) }
        compose.onNodeWithText("Concluído · 100%", substring = true).assertExists()
        compose.onNodeWithText("Importar para biblioteca").assertExists()
        compose.onNodeWithText("Salvar em…").assertExists()
    }
}

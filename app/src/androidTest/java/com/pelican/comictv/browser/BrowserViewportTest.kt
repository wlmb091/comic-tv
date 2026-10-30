package com.pelican.comictv.browser

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.ComicTvTheme
import com.pelican.comictv.ui.TvButton
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
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
    @Test fun importKeepsTheSameFocusedControlAndReadWorksWithDpad() {
        val id = "00000000-0000-0000-0000-000000000001"
        var row by mutableStateOf(BrowserDownloadItem(id, "https://example.org/book", "book.epub", "A", status = BrowserDownloadStatus.Complete))
        var command = ""
        compose.setContent { ComicTvTheme { BrowserDownloadsPanel(listOf(row), false, {}, action = { key, _ ->
            command = key
            if (key == "import") row = row.copy(status = BrowserDownloadStatus.Importing)
        }) } }
        val primary = compose.onNodeWithTag("download-action-$id/primary")
        primary.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        primary.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        primary.assertIsFocused().assertTextEquals("Importando…")
        compose.runOnIdle { assertEquals("import", command); row = row.copy(status = BrowserDownloadStatus.Imported, importedId = "library-book") }
        primary.assertIsFocused().assertTextEquals("Ler agora")
        primary.performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Ver detalhes").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        primary.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("read", command) }
    }
    @Test fun failedImportKeepsFocusAndUpdatesFromAnotherRowDoNotStealIt() {
        val id = "00000000-0000-0000-0000-000000000001"
        var row by mutableStateOf(BrowserDownloadItem(id, "https://example.org/book", "book.epub", "A", status = BrowserDownloadStatus.Complete))
        var other by mutableStateOf(row.copy(id = "00000000-0000-0000-0000-000000000002", filename = "other.epub", status = BrowserDownloadStatus.Running))
        compose.setContent { ComicTvTheme { BrowserDownloadsPanel(listOf(other, row), false, {}, action = { _, _ -> row = row.copy(status = BrowserDownloadStatus.Importing) }) } }
        val primary = compose.onNodeWithTag("download-action-$id/primary")
        primary.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        primary.performClick().assertIsFocused()
        compose.runOnIdle {
            row = row.copy(status = BrowserDownloadStatus.Complete, error = "Importação interrompida")
            other = other.copy(status = BrowserDownloadStatus.Complete)
        }
        primary.assertIsFocused().assertTextEquals("Importar para biblioteca")
    }
}

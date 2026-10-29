package com.pelican.comictv.details

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.library.ComicEntity
import com.pelican.comictv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DetailsChaptersTest {
    @get:Rule val compose = createComposeRule()
    private fun rows() = (1..40).map { index -> DetailChapter("id-$index", "Capítulo $index",
        progress = ComicEntity("id-$index", "Capítulo $index", 20, currentPage = if (index == 30) 4 else 0,
            lastRead = if (index == 30) 100 else 0)) }

    @Test fun resumeChapterOutsideFixedViewportReceivesFocusAndDpadScrollsNext() {
        compose.setContent { ComicTvTheme {
            val state = remember { DetailsChapterController() }
            DetailsChapterPanel("id-1", rows(), state, false, false, null, {}, {}, {}, {},
                Modifier.size(350.dp, 300.dp))
        } }
        compose.onNodeWithText("30. Capítulo 30 · Em andamento").assertIsDisplayed().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("31. Capítulo 31 · Não lido").assertIsDisplayed().assertIsFocused()
    }
    @Test fun lateChaptersNeverTakeFocusAfterUserInteraction() {
        var data by mutableStateOf(emptyList<DetailChapter>())
        compose.setContent { ComicTvTheme {
            val action = remember { FocusRequester() }
            val state = remember { DetailsChapterController() }
            Row {
                TvButton("Continuar leitura", {}, Modifier.focusRequester(action))
                DetailsChapterPanel("id-1", data, state, false, true, null, {}, {}, {}, {},
                    Modifier.size(350.dp, 300.dp))
            }
            LaunchedEffect(Unit) { action.requestFocus() }
        } }
        compose.onNodeWithText("Continuar leitura").assertIsFocused()
        compose.runOnIdle { data = rows() }
        compose.onNodeWithText("Continuar leitura").assertIsFocused()
    }
    @Test fun pointerOpeningSummarySuppressesLateChapterFocusBehindDialog() {
        var data by mutableStateOf(emptyList<DetailChapter>())
        compose.setContent { ComicTvTheme {
            var summaryOpen by remember { mutableStateOf(false) }
            var interacted by remember { mutableStateOf(false) }
            val state = remember { DetailsChapterController() }
            Row {
                TvButton("Resumo completo", { interacted = true; summaryOpen = true })
                DetailsChapterPanel("id-1", data, state, false, interacted, null, {}, {}, {}, {},
                    Modifier.size(350.dp, 300.dp))
            }
            if (summaryOpen) FullSummaryDialog("Sinopse integral da edição.") { summaryOpen = false }
        } }
        compose.onNodeWithText("Resumo completo").performClick()
        compose.onNodeWithText("Voltar aos detalhes").assertIsFocused()
        compose.runOnIdle { data = rows() }
        compose.onNodeWithText("Voltar aos detalhes").assertIsFocused()
    }
    @Test fun outgoingPanelCannotConsumeReaderReturnUntilPauseAndResume() {
        var returning by mutableStateOf(ChapterFocusReturn())
        var consumed = 0
        compose.setContent { ComicTvTheme {
            val state = remember { DetailsChapterController() }
            DetailsChapterPanel("id-1", rows(), state, false, true, returning.restoreId,
                { consumed++; returning = returning.consumed() }, {}, {},
                { row -> returning = returning.readerOpening(row.id) }, Modifier.size(350.dp, 300.dp))
        } }
        // Opening starts while Details is still on screen during navigation animation.
        compose.runOnIdle { returning = returning.readerOpening("id-30") }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("id-30", returning.pendingId)
            assertNull(returning.restoreId)
            assertEquals(0, consumed)
            returning = returning.paused().resumed()
        }
        compose.onNodeWithText("30. Capítulo 30 · Em andamento").assertIsFocused()
        compose.runOnIdle { assertEquals(1, consumed); assertNull(returning.restoreId) }
    }
    @Test fun fullSummaryIsScrollableAndCloseRemainsAvailable() {
        compose.setContent { ComicTvTheme {
            FullSummaryDialog((1..30).joinToString("\n\n") { "Parágrafo $it: texto integral do resumo para leitura." }) {}
        } }
        compose.onNodeWithText("Voltar aos detalhes").assertIsDisplayed().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        repeat(35) { compose.onNodeWithText("Voltar aos detalhes").performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onNodeWithText("Parágrafo 30: texto integral do resumo para leitura.").assertIsDisplayed()
        compose.onNodeWithText("Voltar aos detalhes").assertIsDisplayed()
    }
}

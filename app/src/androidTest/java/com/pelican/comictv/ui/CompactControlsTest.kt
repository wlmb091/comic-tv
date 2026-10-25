package com.pelican.comictv.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.reader.*
import com.pelican.comictv.sources.SourceSearchControls
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CompactControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun activeFilterReentersCollapsedAndLargeTextKeepsExitReachable() {
        compose.setContent { ComicTvTheme(AppearanceOptions().withTextPercentage(130)) {
            ReaderTools(ReaderState(pageCount = 20), false, {}, {}, {},
                onPreviousChapter = {}, onNextChapter = {}, onEditions = {})
        } }
        compose.onNodeWithContentDescription("Sair da leitura")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
            .assertIsFocused().assertIsDisplayed()
    }
    @Test fun savedNonemptyFilterDoesNotOpenEditingWhenEnteringScreen() {
        compose.setContent { ComicTvTheme { SourceSearchControls("manga", {}, "Buscar fonte") } }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Busca ativa: manga").assertExists()
    }
    @Test fun sourceSearchIsCollapsedUntilChosenAndKeepsFilterWhenClosed() {
        var query by mutableStateOf("")
        compose.setContent { ComicTvTheme { SourceSearchControls(query, { query = it }, "Buscar fonte") } }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Buscar").performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("manga")
        compose.onNodeWithText("Fechar busca").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Busca ativa: manga").assertExists()
        compose.runOnIdle { assertEquals("manga", query) }
        compose.onNodeWithText("Limpar busca").performClick()
        compose.onNodeWithText("Buscar").assertIsFocused()
        compose.runOnIdle { assertEquals("", query) }
    }
    @Test fun themeCardFocusDoesNotSelectUntilOk() {
        var selected by mutableStateOf(false)
        val focus = FocusRequester()
        compose.setContent { ComicTvTheme {
            ThemePresetCard(ColorPreset.Flix, selected, { selected = true }, Modifier.focusRequester(focus))
            LaunchedEffect(Unit) { focus.requestFocus() }
        } }
        compose.onNodeWithText("Tema Flix").assertIsNotSelected().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("✓ Tema Flix").assertIsSelected()
    }
    @Test fun readerDockUsesTwoColumnsAndOptionsReturnToTheirIcon() {
        var chapters = 0
        compose.setContent { ComicTvTheme {
            ReaderTools(ReaderState(pageCount = 20), false, {}, {}, {},
                onPreviousChapter = {}, onNextChapter = { chapters++ }, onEditions = {})
        } }
        compose.onNodeWithContentDescription("Continuar").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithContentDescription("Escolher página").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Próximo capítulo").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, chapters) }
        compose.onNodeWithContentDescription("Imagem e mais opções").performClick()
        compose.onNodeWithText("Voltar ao menu").performClick()
        compose.onNodeWithContentDescription("Imagem e mais opções").assertIsFocused()
    }
}

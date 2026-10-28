package com.pelican.comictv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.library.CollectionEntity
import com.pelican.comictv.library.LibraryControlsRow
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LibraryControlsRowTest {
    @get:Rule val compose = createComposeRule()

    private fun controlsStayOnOneLine(preset: ColorPreset) {
        val first = FocusRequester()
        val search = FocusRequester()
        var restoreManage by mutableIntStateOf(0)
        var focusSearch by mutableStateOf(false)
        var openedHistory = false
        val collections = (1..6).map {
            CollectionEntity("$it", "Coleção com nome longo para leitura $it", "colecao-$it")
        }
        compose.setContent {
            ComicTvTheme(AppearanceOptions(preset = preset, textPercent = 130, highContrast = true)) {
                Box(Modifier.width(800.dp)) {
                    LibraryControlsRow(collections, "all", {}, {}, 12345, 12345, false, false,
                        {}, {}, { openedHistory = true }, restoreManage = restoreManage,
                        entryModifier = Modifier.focusRequester(first), searchModifier = Modifier.focusRequester(search))
                }
                LaunchedEffect(Unit) { first.requestFocus() }
                LaunchedEffect(focusSearch) { if (focusSearch) search.requestFocus() }
            }
        }
        compose.onNodeWithText("Todas").assertIsDisplayed().assertIsFocused()
        val all = compose.onNodeWithText("Todas").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("library-header-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("Filtros e ações devem ocupar a mesma linha", abs(all.center.y - actions.center.y) < 2f)
        compose.onNodeWithTag("library-brand").assertDoesNotExist()

        compose.runOnIdle { restoreManage++ }
        compose.onNodeWithText("Gerenciar coleções").assertIsDisplayed().assertIsFocused()
        compose.runOnIdle { focusSearch = true }
        compose.onNodeWithText("Buscar").assertIsDisplayed().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Biblioteca · 12345").assertIsDisplayed().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Histórico · 12345").assertIsDisplayed().assertIsFocused().performClick()
        compose.runOnIdle { assertTrue(openedHistory) }
    }

    @Test fun primeKeepsOneControlsRowWithLargeTextAndLongCollections() = controlsStayOnOneLine(ColorPreset.Prime)

    @Test fun maxKeepsOneControlsRowWithLargeTextAndLongCollections() = controlsStayOnOneLine(ColorPreset.Max)
}

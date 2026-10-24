package com.pelican.comictv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryHeader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LargeTextNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowHeaderSeparatesBrandAndKeepsAllActionsAvailable() {
        val library = FocusRequester()
        var openedHistory = false
        compose.setContent {
            ComicTvTheme(AppearanceOptions(textSize = 2)) {
                Box(Modifier.width(560.dp)) {
                    LibraryHeader(12345, 12345, false, false, {}, {}, { openedHistory = true },
                        Modifier.focusRequester(library), Modifier)
                }
                LaunchedEffect(Unit) { library.requestFocus() }
            }
        }
        val brand = compose.onNodeWithTag("library-brand").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("library-header-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(actions.top >= brand.bottom)
        compose.onNodeWithText("Biblioteca · 12345").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("Buscar").assertIsDisplayed()
        compose.onNodeWithText("Histórico · 12345").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(openedHistory) }
    }

    @Test fun shortSidebarKeepsEveryRouteReachableWithLargeText() {
        compose.setContent {
            ComicTvTheme(AppearanceOptions(textSize = 2)) {
                Box(Modifier.width(600.dp).height(280.dp)) {
                    TvShell("settings", {}, focusSectionOnEntry = true) { Text("Conteúdo") }
                }
            }
        }
        compose.onNodeWithTag("nav-settings").assertIsFocused().assertIsDisplayed()
        val routes = listOf("settings", "import", "extensions", "sources", "recent", "library")
        routes.zipWithNext().forEach { (current, previous) ->
            compose.onNodeWithTag("nav-$current").performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithTag("nav-$previous").assertIsFocused().assertIsDisplayed()
        }
    }
}

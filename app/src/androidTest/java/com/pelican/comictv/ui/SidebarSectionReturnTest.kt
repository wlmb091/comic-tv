package com.pelican.comictv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SidebarSectionReturnTest {
    @get:Rule val compose = createComposeRule()
    @Test fun leftFromEachSectionsContentReturnsToItsOwnIconWithoutBreakingInternalLeft() {
        val route = mutableStateOf("extensions")
        var navigation = 0
        compose.setContent { ComicTvTheme { key(route.value) {
            TvShell(route.value, { navigation++ }) {
                Row {
                    TvButton("Primeiro", {}, Modifier.testTag("first-control"))
                    TvButton("Segundo", {}, Modifier.testTag("second-control"))
                }
            }
        } } }
        val sections = listOf("library", "recent", "sources", "extensions", "import", "settings")
        for ((index, section) in sections.withIndex()) {
            compose.runOnIdle { route.value = section }
            compose.onNodeWithTag("second-control").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                .performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithTag("first-control").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.onNodeWithTag("nav-$section").assertIsFocused().assertIsSelected()
            if (index > 0) {
                compose.onNodeWithTag("nav-$section").performKeyInput { pressKey(Key.DirectionUp) }
                compose.onNodeWithTag("nav-${sections[index - 1]}").assertIsFocused()
            }
        }
        compose.runOnIdle { assertEquals(0, navigation) }
    }
}

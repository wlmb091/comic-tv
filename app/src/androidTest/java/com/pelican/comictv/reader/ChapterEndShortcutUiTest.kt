package com.pelican.comictv.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ChapterEndShortcutUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun appearingDoesNotStealFocusAndOnlySelectionOpensChapter() {
        var visible by mutableStateOf(false)
        var opened = 0
        val page = FocusRequester()
        val shortcut = FocusRequester()
        compose.setContent { ComicTvTheme { Box {
            TvButton("Page", {}, Modifier.focusRequester(page).testTag("page"))
            if (visible) ChapterEndShortcut(shortcut, {}, { opened++ }, { page.requestFocus() })
            LaunchedEffect(Unit) { page.requestFocus() }
        } } }
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("page").assertIsFocused()
        compose.onNodeWithText("Próximo capítulo →").assertIsNotFocused()
        compose.runOnIdle { assertEquals(0, opened); shortcut.requestFocus() }
        compose.onNodeWithText("Próximo capítulo →").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, opened) }
    }
}

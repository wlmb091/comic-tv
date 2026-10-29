package com.pelican.comictv.reader

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ReaderRotationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rotatedRailKeepsNormalRemoteButtonOrder() {
        compose.setContent { ComicTvTheme {
            ReaderSurface(true) {
                ReaderTools(ReaderState(pageCount = 12, mode = ReaderMode.Tools), false, {}, {}, {})
            }
        } }
        compose.onNodeWithContentDescription("Continuar").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Escolher página").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithContentDescription("Continuar").assertIsFocused()
    }

    @Test fun toolsRotateWithPageAndKeepTheirMenuOpen() {
        var logicalSize = IntSize.Zero
        var physicalSize = IntSize.Zero
        compose.setContent { ComicTvTheme {
            var rotated by remember { mutableStateOf(false) }
            Box(Modifier.size(600.dp, 360.dp).testTag("viewport").onSizeChanged { physicalSize = it }) {
                ReaderSurface(rotated) {
                    Box(Modifier.fillMaxSize().testTag("page").onSizeChanged { logicalSize = it }) {
                        ReaderTools(ReaderState(mode = ReaderMode.Tools), false, {}, {}, {},
                            onRotate = { rotated = !rotated })
                    }
                }
            }
        } }
        compose.onNodeWithContentDescription("Imagem e mais opções").performClick()
        compose.onNodeWithText("Tela: 0° · girar 90°").performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(physicalSize.height, logicalSize.width)
            assertEquals(physicalSize.width, logicalSize.height)
        }
        val page = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.left, page.left, 1f)
        assertEquals(viewport.top, page.top, 1f)
        assertEquals(viewport.right, page.right, 1f)
        assertEquals(viewport.bottom, page.bottom, 1f)
        compose.onNodeWithText("Tela: 90° · voltar a 0°").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(physicalSize, logicalSize) }
        compose.onNodeWithText("Voltar ao menu").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Imagem e mais opções").assertIsFocused()
    }
}

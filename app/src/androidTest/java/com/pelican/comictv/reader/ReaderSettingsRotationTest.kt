package com.pelican.comictv.reader

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ReaderSettingsRotationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun rotatedMoreMenuRetainsPortraitDownAndUpOrder() {
        compose.setContent { ComicTvTheme {
            ReaderSurface(true) {
                ReaderTools(ReaderState(pageCount = 20, mode = ReaderMode.Tools), false, {}, {}, {}, onNarrate = {})
            }
        } }
        compose.onNodeWithContentDescription("Imagem e mais opções").performClick()
        compose.onNodeWithText("Voltar ao menu").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Ouvir livro e traduzir").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Direção: esquerda → direita").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Ouvir livro e traduzir").assertIsFocused()
    }
}

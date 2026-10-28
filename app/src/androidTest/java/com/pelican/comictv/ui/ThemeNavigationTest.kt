package com.pelican.comictv.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ThemeNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun setShell(preset: ColorPreset, selected: String = "library", highContrast: Boolean = true) {
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = preset, textPercent = 130, highContrast = highContrast)) {
            var pending by remember { mutableStateOf(true) }
            TvShell(selected, {}, focusContentOnEntry = pending, onSectionFocused = { pending = false },
                backdrop = { Box(Modifier.fillMaxSize().testTag("test-shell-backdrop")) }) {
                TvButton("Primeiro controle", {}, preferredContentEntryModifier())
            }
        } }
    }

    @Test fun cinemaReservesMenuSpaceOutsideTheImmersiveLibrary() {
        setShell(ColorPreset.Cinema, "settings")
        val menu = compose.onNodeWithTag("cinema-menu").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithText("Primeiro controle").fetchSemanticsNode().boundsInRoot
        assertTrue("Menu não deve cobrir controles/título das outras seções", content.top >= menu.bottom)
        val shell = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
        assertTrue("Menu do Cinema fica no canto superior direito", menu.center.x > shell.center.x)
        compose.onNodeWithText("COMIC TV").assertDoesNotExist()
    }

    @Test fun cinemaMenuBackPreservesUnderlyingNestedPage() {
        var nestedBack = false
        compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Cinema)) {
            var pending by remember { mutableStateOf(true) }
            TvShell("sources", {}, focusContentOnEntry = pending, onSectionFocused = { pending = false }) {
                androidx.activity.compose.BackHandler { nestedBack = true }
                TvButton("Página interna", {}, preferredContentEntryModifier())
            }
        } }
        compose.onNodeWithText("Página interna").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        assertTrue("Voltar do menu preserva a página interna", !nestedBack)
    }

    private fun assertWordmarkFits() {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("COMIC TV").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Marca inteira com texto em130%", layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
    }

    @Test fun primeMovesUpToCurrentSectionAndDownToContent() {
        setShell(ColorPreset.Prime)
        assertWordmarkFits()
        compose.onNodeWithTag("navigation-top").assertExists()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
    }

    @Test fun maxMovesDownToDockAndUpToContent() {
        setShell(ColorPreset.Max)
        compose.onNodeWithTag("navigation-bottom").assertExists()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
    }

    @Test fun cinemaLeftOpensFloatingMenuAndRightDismissesIt() {
        setShell(ColorPreset.Cinema)
        compose.onNodeWithText("COMIC TV").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        compose.onNodeWithText("COMIC TV").assertDoesNotExist()
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
        compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
    }

    @Test fun flixIconsOverlayFullShellBackdropAndExpandedMenuDimsContent() {
        setShell(ColorPreset.Flix, highContrast = false)
        val shell = compose.onNodeWithTag("theme-shell").fetchSemanticsNode().boundsInRoot
        val backdrop = compose.onNodeWithTag("test-shell-backdrop").fetchSemanticsNode().boundsInRoot
        assertTrue("Arte deve alcançar a área dos ícones e as bordas da tela", backdrop == shell)
        compose.onNodeWithTag("sidebar-collapsed").assertExists()
        compose.onNodeWithTag("sidebar-panel").assertDoesNotExist()
        compose.onNodeWithTag("nav-library").assertIsSelected()
        compose.onNodeWithTag("flix-navigation-scrim").assertDoesNotExist()
        compose.onNodeWithText("Primeiro controle").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("sidebar-expanded").assertExists()
        compose.onNodeWithTag("sidebar-panel").assertExists()
        val scrim = compose.onNodeWithTag("flix-navigation-scrim").fetchSemanticsNode().boundsInRoot
        assertTrue("Escurecimento cobre o conteúdo completo", scrim == shell)
        compose.onNodeWithTag("nav-library").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Primeiro controle").assertIsFocused()
        compose.onNodeWithTag("sidebar-panel").assertDoesNotExist()
        compose.onNodeWithTag("flix-navigation-scrim").assertDoesNotExist()
    }

    @Test fun flixHighContrastRetainsOpaqueSidebar() {
        setShell(ColorPreset.Flix)
        compose.onNodeWithTag("sidebar-collapsed").assertExists()
        compose.onNodeWithTag("sidebar-panel").assertExists()
    }
}

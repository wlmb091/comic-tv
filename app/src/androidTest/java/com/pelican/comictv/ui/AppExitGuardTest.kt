package com.pelican.comictv.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AppExitGuardTest {
    @get:Rule val compose = createComposeRule()

    private fun pressBack() {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    @Test fun childBackAndNavigationWinThenExitRequiresExplicitConfirmation() {
        lateinit var nav: NavHostController
        var exits = 0
        var readerBack = 0
        compose.setContent { ComicTvTheme(AppearanceOptions().withTextPercentage(130)) {
            AppExitGuard(onExit = { exits++ }) {
                val controller = rememberNavController()
                SideEffect { nav = controller }
                NavHost(controller, startDestination = "library") {
                    composable("library") { Column {
                        TvButton("Abrir leitor", { controller.navigate("reader") })
                        TvButton("Abrir opções", { controller.navigate("options") })
                    } }
                    composable("options") { Text("Opções") }
                    composable("reader") {
                        BackHandler { readerBack++; controller.popBackStack() }
                        Text("Leitor")
                    }
                }
            }
        } }
        compose.onNodeWithText("Abrir opções").performClick()
        pressBack()
        compose.onNodeWithText("Abrir leitor").assertIsDisplayed()
        compose.onNodeWithTag("exit-confirmation").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, exits) }
        compose.onNodeWithText("Abrir leitor").performClick()
        pressBack()
        compose.onNodeWithText("Abrir leitor").assertIsDisplayed()
        compose.onNodeWithTag("exit-confirmation").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, readerBack); assertEquals("library", nav.currentDestination?.route) }
        pressBack()
        compose.onNodeWithTag("exit-continue").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("exit-confirmation").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, exits) }
        pressBack()
        compose.onNodeWithTag("exit-continue").assertIsFocused()
        pressBack()
        compose.onNodeWithTag("exit-confirmation").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, exits) }
        pressBack()
        compose.onNodeWithTag("exit-confirm").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun exitConfirmationAcceptsPhoneControlAndPhoneBackOnlyClosesIt() {
        val dialogs = com.pelican.comictv.companion.CompanionDialogs()
        var exits = 0
        compose.setContent { CompositionLocalProvider(com.pelican.comictv.companion.LocalCompanionDialogs provides dialogs) {
            ComicTvTheme(AppearanceOptions()) { AppExitGuard(onExit = { exits++ }) { Text("Início") } }
        } }
        pressBack()
        compose.onNodeWithTag("exit-continue").assertIsFocused()
        compose.runOnIdle { checkNotNull(dialogs.focused()).dismiss() }
        compose.onNodeWithTag("exit-confirmation").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, exits) }
    }
}

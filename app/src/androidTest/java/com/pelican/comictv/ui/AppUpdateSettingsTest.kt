package com.pelican.comictv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pelican.comictv.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class AppUpdateSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val offered = UpdateManifest(59,"1.1.20",26,25 * 1024 * 1024,"a".repeat(64),
        "${UpdateManifest.RELEASE_BASE}/v1.1.20/Comic-TV-1.1.20.apk","Novidades legíveis. ".repeat(200))

    @Test fun longFailureKeepsActionsAndScrollablePanelAt130Percent() {
        val error = "Falha de rede. ".repeat(24).take(300)
        compose.setContent { ComicTvTheme(AppearanceOptions(highContrast = true).withTextPercentage(130)) {
            // Remaining TV content area after the settings heading and tabs.
            Box(Modifier.requiredSize(800.dp, 320.dp)) {
                AppUpdateContent(AppUpdateState(AppUpdatePhase.Failed, offered, message = error), {}, {})
            }
        } }
        compose.onNodeWithTag("update-primary").assertIsDisplayed()
        compose.onNodeWithTag("update-notes").assertHeightIsAtLeast(64.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        compose.onNodeWithText(error).assertIsDisplayed()
    }

    @Test fun busyTransitionMovesSecondaryFocusToStablePrimary() {
        val state = mutableStateOf(AppUpdateState(AppUpdatePhase.Available, offered))
        compose.setContent { ComicTvTheme(AppearanceOptions(highContrast = true).withTextPercentage(130)) {
            AppUpdateContent(state.value, {}, {})
        } }
        compose.onNodeWithTag("update-check").performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
        compose.runOnIdle { state.value = AppUpdateState(AppUpdatePhase.Downloading, offered, received = 12 * 1024 * 1024) }
        compose.onNodeWithTag("update-primary").assertIsFocused().assertTextContains("Cancelar")
        compose.onNodeWithTag("update-check").assertDoesNotExist()
        compose.onNodeWithText("12,0 / 25,0 MiB").assertIsDisplayed()
        compose.runOnIdle { state.value = AppUpdateState(AppUpdatePhase.Ready, offered) }
        compose.onNodeWithTag("update-primary").assertIsFocused().assertTextContains("Instalar atualização")
        compose.onNodeWithTag("update-check").assertExists()
        compose.runOnIdle { state.value = AppUpdateState(AppUpdatePhase.Verifying, offered) }
        compose.onNodeWithTag("update-primary").assertIsFocused()
        compose.onNodeWithTag("update-check").assertDoesNotExist()
        compose.onNodeWithText("Conferindo atualização…").assertIsDisplayed()
        compose.onNodeWithTag("update-progress").assertDoesNotExist()
        compose.runOnIdle { state.value = AppUpdateState(AppUpdatePhase.Installing, offered) }
        compose.onNodeWithText("Aguardando confirmação do Android").assertIsDisplayed()
        compose.onNodeWithTag("update-primary").assertIsFocused()
        compose.onNodeWithText("Atualização instalada.").assertDoesNotExist()
    }

    @Test fun longNotesScrollBelowActionsAndProgressIsBounded() {
        val state = mutableStateOf(AppUpdateState(AppUpdatePhase.Downloading, offered, received = Long.MAX_VALUE))
        compose.setContent { ComicTvTheme(AppearanceOptions().withTextPercentage(130)) {
            AppUpdateContent(state.value, {}, {})
        } }
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("25,0 / 25,0 MiB").assertIsDisplayed()
        compose.onNodeWithTag("update-primary").assertIsDisplayed()
        val notes = compose.onNodeWithTag("update-notes")
        notes.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        notes.performKeyInput { pressKey(Key.DirectionDown) }
        notes.assertIsFocused()
        compose.runOnIdle { state.value = AppUpdateState(AppUpdatePhase.Downloading, offered, received = -1) }
        compose.onNodeWithText("0%").assertIsDisplayed()
        compose.onNodeWithText("0,0 / 25,0 MiB").assertIsDisplayed()
    }

    @Test fun primaryKeepsFocusAcrossCheckDownloadAndInstallAt130Percent() {
        var installs=0
        val source=object:AppUpdateSource {
            val update=UpdateManifest(58,"1.1.19",26,4,"a".repeat(64),"${UpdateManifest.RELEASE_BASE}/v1.1.19/Comic-TV-1.1.19.apk","Notas. ".repeat(200))
            override suspend fun check()=update
            override suspend fun download(update:UpdateManifest,progress:(Long)->Unit)=File("ready.apk").also{progress(4)}
            override suspend fun revalidate(file:File,update:UpdateManifest)=Unit
            override suspend fun markOffered(file:File)=Unit
            override suspend fun installationReturned(file:File)=Unit
            override suspend fun installedVersionCode()=57L
        }
        compose.setContent { ComicTvTheme(AppearanceOptions().withTextPercentage(130)) {
            val vm=remember { AppUpdateViewModel(source) }
            AppUpdateSettings(vm,{installs++})
        } }
        val primary=compose.onNodeWithTag("update-primary")
        primary.performSemanticsAction(SemanticsActions.RequestFocus){it()}.performKeyInput{pressKey(Key.DirectionCenter)}
        primary.assertIsFocused().assertTextContains("Baixar atualização").performKeyInput{pressKey(Key.DirectionCenter)}
        primary.assertIsFocused().assertTextContains("Instalar atualização").performKeyInput{pressKey(Key.DirectionCenter)}
        compose.runOnIdle {assertEquals(1,installs)}
    }
}

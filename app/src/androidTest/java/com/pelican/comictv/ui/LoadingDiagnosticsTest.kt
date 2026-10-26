package com.pelican.comictv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.performance.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LoadingDiagnosticsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun longReportAt130PercentCanReachTopAndBottomByDpad() {
        val metrics = LoadMetrics().apply { LoadStage.entries.forEach(::hit) }
        compose.setContent { ComicTvTheme(AppearanceOptions().withTextPercentage(130)) {
            LoadingDiagnosticsDialog({}, metrics, heapSnapshot = { 10L to 100L })
        } }
        val panel = compose.onNodeWithContentDescription("Informações do diagnóstico")
        panel.assertIsFocused()
        compose.onNodeWithText("Página atual", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Atualizar medições").assertIsDisplayed()
        panel.performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Atualizar medições").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        panel.assertIsFocused()
        repeat(60) { panel.performKeyInput { pressKey(Key.DirectionDown) }; compose.waitForIdle() }
        compose.onNodeWithText("Atualizar medições").assertIsDisplayed()
        compose.onNodeWithText("Medições desta execução.", substring = true).assertIsDisplayed()
        panel.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        panel.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Atualizar medições").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Zerar medições").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Fechar").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        panel.assertIsFocused()
        repeat(60) { panel.performKeyInput { pressKey(Key.DirectionUp) }; compose.waitForIdle() }
        panel.assertIsFocused()
        compose.onNodeWithText("Página atual", substring = false).assertIsDisplayed()
    }
    @Test fun clearingMeasurementsKeepsControlsFocusedAndUnmeasuredRowsAvailable() {
        val metrics = LoadMetrics().apply { hit(LoadStage.PageMaterialize) }
        compose.setContent { ComicTvTheme {
            LoadingDiagnosticsDialog({}, metrics, heapSnapshot = { 10L to 100L })
        } }
        compose.onNodeWithText("Zerar medições").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .performKeyInput { pressKey(Key.DirectionCenter) }.assertIsFocused()
        compose.onNodeWithContentDescription("Informações do diagnóstico").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Página atual", substring = false).assertIsDisplayed()
        compose.onAllNodesWithText("Sem medições nesta sessão.").onFirst().assertIsDisplayed()
    }
}

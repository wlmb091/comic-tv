package com.pelican.comictv.reader.narration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.ComicTvTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class TranslationDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun startsAtAddressAndRejectsPublicServerWithoutSaving() {
        var applied = false
        compose.setContent { ComicTvTheme {
            TranslationSettingsDialog(LocalTranslationSettings(address = "https://example.com"), { applied = true }, {})
        } }
        compose.onNodeWithContentDescription("Endereço do tradutor local").assertIsFocused()
        compose.onNodeWithText("Salvar servidor").performScrollTo().performClick()
        compose.onNodeWithText("Use o IPv4 do computador na rede local. Serviços públicos de tradução não são usados por esta função.")
            .assertExists()
        compose.runOnIdle { assertFalse(applied) }
    }
}

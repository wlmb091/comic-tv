package com.pelican.comictv.library

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pelican.comictv.ui.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoFooterAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun footerRemainsOneLineAt130PercentAndKeepsEveryControlReachable() {
        compose.setContent {
            ComicTvTheme(AppearanceOptions(preset = ColorPreset.Prime, textPercent = 130)) {
                LibraryFooter((0..7).map { CollectionEntity("c$it", "Coleção $it", "colecao $it") },
                    "all", {}, {}, 24, 21, false, false, {}, {}, {}, showSearch = false)
            }
        }
        val first = compose.onNodeWithText("Biblioteca · 24").getUnclippedBoundsInRoot()
        val expectedY = (first.top.value + first.bottom.value) / 2
        listOf("Biblioteca · 24", "Histórico · 21", "Todas", "★ Favoritos", "Coleção 7", "Gerenciar coleções").forEach { label ->
            val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("Controle fica na mesma linha: $label", kotlin.math.abs((bounds.top.value + bounds.bottom.value) / 2 - expectedY) < 1f)
            assertTrue("Alvo com pelo menos48dp: $label", bounds.bottom.value - bounds.top.value >= 48f)
        }
    }
}

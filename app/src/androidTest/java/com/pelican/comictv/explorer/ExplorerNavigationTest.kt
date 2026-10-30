package com.pelican.comictv.explorer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.ui.ComicTvTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExplorerNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun typingFilterKeepsEditorFocusedAndParentRestoresScroll() {
        val root = File(context.cacheDir, "explorer-fixture-${UUID.randomUUID()}").apply { mkdirs() }
        val child = File(root, "z-child").apply { mkdir() }
        val model = ExplorerViewModel(context)
        try {
            repeat(30) { File(root, "book-$it.cbz").writeText("fixture") }
            model.open(ExplorerFolder("fixture", "Pasta de teste", root))
            compose.setContent { ComicTvTheme {
                ExplorerScreen(model, false, null, emptyMap(), {}, {}, {}, {})
            } }
            compose.waitUntil(5_000) { !model.state.value.loading }
            compose.onNode(hasSetTextAction()).performTextInput("book")
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("-2")
            compose.onNode(hasSetTextAction()).assertTextEquals("book-2")
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNodeWithText("▸ z-child\nPasta").performScrollTo().performClick()
            compose.waitUntil(5_000) { model.state.value.folder?.file == child && !model.state.value.loading }
            compose.onNodeWithText("Pasta acima").performScrollTo().performClick()
            compose.waitUntil(5_000) { model.state.value.folder?.file == root && !model.state.value.loading }
            compose.onNodeWithText("▸ z-child\nPasta").assertIsFocused()
        } finally { root.deleteRecursively() }
    }
}

package com.pelican.comictv.sources

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.pelican.comictv.ui.ComicTvTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DirectChapterStepTest {
    @get:Rule val compose = createComposeRule()
    private val first = OnlineChapter("1", "Primeiro", 1f)
    private val second = OnlineChapter("2", "Segundo", 2f)
    private val context = OnlineEditions(SourceHandle("source", 1, "Fonte", "pt", "https://example.com"),
        OnlineTitle("title", "Saga"), first, listOf(second, first), "a".repeat(64))

    @Test fun nextOpensDirectlyWithoutDisplayingChapterMenuDuringLoading() {
        val loaded = CompletableDeferred<OnlineEditions>()
        var destination: String? = null
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { loaded.await() }, { _, chapter ->
            assertEquals("2", chapter.key); "reader-2"
        })
        compose.setContent { ComicTvTheme {
            OnlineEditionsScreen(vm, { destination = it }, {}, initialStep = 1)
        } }
        compose.onNodeWithText("CAPÍTULOS").assertDoesNotExist()
        compose.runOnIdle { loaded.complete(context) }
        compose.waitUntil { destination == "reader-2" }
        compose.onNodeWithText("CAPÍTULOS").assertDoesNotExist()
    }

    @Test fun noNeighborShowsExplanationWithoutOpeningListOrOtherChapter() {
        val vm = OnlineEditionsViewModel("id", SavedStateHandle(), { context }, { _, _ -> error("No previous chapter") })
        compose.setContent { ComicTvTheme {
            OnlineEditionsScreen(vm, { error("Unexpected reader") }, {}, initialStep = -1)
        } }
        compose.waitUntil { vm.state.value.message != null }
        compose.onNodeWithText("Você está na primeira edição desta sequência.").assertExists()
        compose.onNodeWithText("CAPÍTULOS").assertDoesNotExist()
        compose.onNodeWithText("Voltar à leitura").assertExists()
        compose.runOnIdle { vm.leave() }
    }
}

package com.pelican.comictv.library

import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises a newly composed library, rather than relying on an event counter's old baseline. */
@OptIn(ExperimentalTestApi::class)
class MaxSearchHandoffTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchFromAnotherSectionIsConsumedOnceAndDoesNotReplayOnReturn() = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(real.cacheDir, "max-search-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val db = Room.inMemoryDatabaseBuilder(real, LibraryDatabase::class.java).build()
        val repository = LibraryRepository(context, db.comics(), collectionDao = db.collections())
        var shown by mutableStateOf(true)
        var route by mutableStateOf("settings")
        var consumed = 0
        try {
            compose.setContent { ComicTvTheme(AppearanceOptions(preset = ColorPreset.Max, textPercent = 130)) {
                var searchPending by remember { mutableStateOf(false) }
                var query by remember { mutableStateOf("") }
                CompositionLocalProvider(LocalShellLibrarySearch provides {
                    searchPending = true
                    route = "library"
                }) {
                    if (shown) key(route) {
                        TvShell(route, { route = it }) {
                            if (route == "library") LibraryScreen(emptyList(), repository, null, {}, {}, {},
                                query = query, onQuery = { query = it }, searchRequested = searchPending,
                                onSearchHandled = { searchPending = false; consumed++ })
                            else TvButton("Conteúdo", {}, preferredContentEntryModifier())
                        }
                    }
                }
            } }
            compose.onNodeWithTag("nav-search").assertIsDisplayed().performClick()
            compose.onNodeWithTag("nav-library").assertIsSelected()
            compose.onNodeWithContentDescription("Título do quadrinho").assertIsDisplayed()
            compose.onNodeWithText("Ver resultados").performClick()
            compose.onNodeWithContentDescription("Título do quadrinho").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, consumed); route = "settings" }
            compose.onNodeWithTag("nav-library").performClick()
            compose.onNodeWithContentDescription("Título do quadrinho").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, consumed) }
            compose.onNodeWithTag("nav-search").performClick()
            compose.onNodeWithContentDescription("Título do quadrinho").assertIsDisplayed()
            compose.runOnIdle { assertEquals(2, consumed) }
        } finally {
            compose.runOnIdle { shown = false }
            compose.waitForIdle()
            repository.closeBackgroundWork()
            db.close()
            root.deleteRecursively()
        }
    }
}

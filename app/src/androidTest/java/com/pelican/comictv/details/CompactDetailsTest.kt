package com.pelican.comictv.details

import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.library.*
import com.pelican.comictv.ui.ComicTvTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CompactDetailsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun secondaryActionsStayInMenuAndDeletionStillRequiresConfirmation() = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(real.cacheDir, "compact-details-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = real.getSharedPreferences("${root.name}-$name", mode)
        }
        val db = Room.inMemoryDatabaseBuilder(real, LibraryDatabase::class.java).build()
        val repository = LibraryRepository(context, db.comics(), collectionDao = db.collections())
        var visible by mutableStateOf(true)
        try {
            db.comics().insert(ComicEntity("compact", "Book", 25, currentPage = 3, lastRead = 1))
            compose.setContent { ComicTvTheme { if (visible) DetailsScreen("compact", repository, {}, {}) } }
            compose.waitUntil { compose.onAllNodesWithText("Continuar leitura").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Excluir da biblioteca").assertDoesNotExist()
            compose.onNodeWithText("Mais opções").performClick()
            compose.onNodeWithText("Voltar aos detalhes").assertIsFocused()
            compose.onNodeWithText("Excluir da biblioteca").performClick()
            compose.onNodeWithText("Cancelar").assertIsFocused().performClick()
            assertNotNull(db.comics().get("compact"))
            compose.onNodeWithText("Excluir da biblioteca").assertIsFocused()
            compose.onNodeWithText("Voltar aos detalhes").performClick()
            compose.onNodeWithText("Mais opções").assertIsFocused()
        } finally {
            compose.runOnIdle { visible = false }; compose.waitForIdle()
            repository.closeBackgroundWork(); db.close(); root.deleteRecursively()
        }
    }
}

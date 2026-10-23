package com.pelican.comictv.reader

import androidx.lifecycle.SavedStateHandle
import com.pelican.comictv.library.ComicEntity
import com.pelican.comictv.library.ReaderRepository
import com.pelican.comictv.model.PageRef
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private class FakeRepository : ReaderRepository {
        val saved = mutableListOf<Int>()
        var initDelay = 0L
        var saveDelay = 0L
        var failIndex = false
        var textFile: File? = null
        override suspend fun getComic(id: String): ComicEntity {
            delay(initDelay)
            return ComicEntity(id, "Teste", 3, currentPage = 1)
        }
        override suspend fun pages(comicId: String): List<PageRef> {
            if (failIndex) { failIndex = false; throw java.io.IOException("Index unavailable") }
            return (0..2).map { PageRef(it, if (textFile != null) "epub:text:$it" else "$it.jpg") }
        }
        override suspend fun materialize(comicId: String, page: PageRef): File {
            delay(if (page.index == 1) 100 else 1)
            return textFile ?: File("page-${page.index}")
        }
        override suspend fun saveProgress(id: String, page: Int) { delay(saveDelay); saved += page }
        override suspend fun savePreferences(id: String, fit: Int, rtl: Boolean) = Unit
    }

    @Test fun epubTextDoesNotGoThroughImageDecoder() = runTest(dispatcher) {
        val file = File.createTempFile("epub-text", ".txt").apply { writeText("Texto do livro.") }
        try {
            val vm = ReaderViewModel(FakeRepository().apply { textFile = file }, "text", SavedStateHandle(),
                ioDispatcher = dispatcher, pageDimensions = { error("Text must not be decoded as a bitmap") })
            advanceUntilIdle()
            assertNull(vm.error.value)
            assertEquals(file, vm.image.value!!.file)
        } finally { file.delete() }
    }

    @Test fun stalePageCannotReplaceLatestPageAndProgressIsSaved() = runTest(dispatcher) {
        val repository = FakeRepository()
        val handle = SavedStateHandle()
        val vm = ReaderViewModel(repository, "stable", handle, ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        runCurrent()
        assertEquals(1, vm.state.value.page)
        vm.action(ReaderAction.Right)
        advanceUntilIdle()
        assertEquals("page-2", vm.image.value!!.file.name)
        assertEquals(2, handle.get<Int>("page"))
        assertEquals(2, repository.saved.last())
        var closed = false
        vm.close { closed = true }
        advanceUntilIdle()
        assertTrue(closed)
        assertEquals(2, repository.saved.last())
    }

    @Test fun restoresSavedPageInsteadOfOlderDatabaseProgress() = runTest(dispatcher) {
        val vm = ReaderViewModel(FakeRepository(), "stable", SavedStateHandle(mapOf("page" to 0)), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        assertEquals(0, vm.state.value.page)
        assertEquals("page-0", vm.image.value!!.file.name)
    }

    @Test fun closingDuringInitializationPreservesExistingProgress() = runTest(dispatcher) {
        val repository = FakeRepository().apply { initDelay = 1_000 }
        val vm = ReaderViewModel(repository, "stable", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        runCurrent()
        var exits = 0
        vm.close { exits++ }
        runCurrent()
        assertEquals(1, exits)
        assertTrue(repository.saved.isEmpty())
    }

    @Test fun repeatedExitOnlyInvokesNavigationOnce() = runTest(dispatcher) {
        val repository = FakeRepository().apply { saveDelay = 100 }
        val vm = ReaderViewModel(repository, "stable", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        var exits = 0
        repeat(5) { vm.close { exits++ } }
        advanceUntilIdle()
        assertEquals(1, exits)
    }

    @Test fun retryRecoversAnIndexInitializationFailure() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failIndex = true }
        val vm = ReaderViewModel(repository, "stable", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        assertNotNull(vm.error.value)
        vm.retry()
        advanceUntilIdle()
        assertEquals(3, vm.state.value.pageCount)
        assertNotNull(vm.image.value)
    }
}

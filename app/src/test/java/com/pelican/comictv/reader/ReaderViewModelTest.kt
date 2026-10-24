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
        val thumbnailCalls = mutableListOf<Int>()
        var thumbnailDelay = 0L
        var thumbnailFailure = false
        var ignoreThumbnailCancellation = false
        var pageDelay = 0L
        var activeMaterializations = 0
        var pageFailure = false
        var count = 3
        var rtl = false
        override suspend fun thumbnail(comicId: String, page: PageRef): File {
            thumbnailCalls += page.index
            if (ignoreThumbnailCancellation) withContext(NonCancellable) { delay(thumbnailDelay) } else delay(thumbnailDelay)
            if (thumbnailFailure) throw java.io.IOException("Preview unavailable")
            return File("thumbnail-${page.index}")
        }
        override suspend fun getComic(id: String): ComicEntity {
            delay(initDelay)
            return ComicEntity(id, "Teste", count, currentPage = 1, rtl = rtl)
        }
        override suspend fun pages(comicId: String): List<PageRef> {
            if (failIndex) { failIndex = false; throw java.io.IOException("Index unavailable") }
            return (0 until count).map { PageRef(it, if (textFile != null) "epub:text:$it" else "$it.jpg") }
        }
        override suspend fun materialize(comicId: String, page: PageRef): File {
            activeMaterializations++
            try {
                delay(pageDelay)
                if (pageFailure) throw java.io.IOException("Page unavailable")
                delay(if (page.index == 1) 100 else 1)
                return textFile ?: File("page-${page.index}")
            } finally { activeMaterializations-- }
        }
        override suspend fun saveProgress(id: String, page: Int) { delay(saveDelay); saved += page }
        override suspend fun savePreferences(id: String, fit: Int, rtl: Boolean) = Unit
    }

    @Test fun galleryLoadsNeighborsWithoutChangingProgressAndStopsWhenDisabled() = runTest(dispatcher) {
        val repository = FakeRepository()
        val saved = SavedStateHandle()
        val vm = ReaderViewModel(repository, "gallery", saved, ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        vm.toggleGallery()
        advanceUntilIdle()
        assertTrue(vm.gallery.value)
        assertEquals(true, saved.get<Boolean>("gallery"))
        assertEquals(listOf(0, 2), vm.previews.value.map { it.page })
        assertEquals(1, vm.state.value.page)
        assertEquals(listOf(1), repository.saved)
        vm.action(ReaderAction.Right)
        advanceUntilIdle()
        assertEquals(listOf(1), vm.previews.value.map { it.page })
        assertEquals(2, repository.saved.last())
        vm.toggleGallery()
        advanceUntilIdle()
        assertFalse(vm.gallery.value)
        assertTrue(vm.previews.value.isEmpty())
    }

    @Test fun restoredGalleryKeepsOnlyValidNeighborsAtFirstPage() = runTest(dispatcher) {
        val vm = ReaderViewModel(FakeRepository(), "gallery", SavedStateHandle(mapOf("gallery" to true, "page" to 0)),
            ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        assertTrue(vm.gallery.value)
        assertEquals(listOf(1), vm.previews.value.map { it.page })
        vm.action(ReaderAction.StartTimeline)
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2), vm.previews.value.map { it.page })
        vm.action(ReaderAction.Back)
        advanceUntilIdle()
        assertEquals(listOf(1), vm.previews.value.map { it.page })
    }

    @Test fun closingStopsPageWorkBeforeNavigatingToAnotherSourceOperation() = runTest(dispatcher) {
        val repository = FakeRepository().apply { pageDelay = 10_000 }
        val vm = ReaderViewModel(repository, "online", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        runCurrent()
        assertEquals(1, repository.activeMaterializations)
        var activeAtNavigation = -1
        vm.close { activeAtNavigation = repository.activeMaterializations }
        advanceUntilIdle()
        assertEquals(0, activeAtNavigation)
        vm.retry()
        runCurrent()
        val restartedAfterNavigation = repository.activeMaterializations
        advanceUntilIdle()
        assertEquals(0, restartedAfterNavigation)
    }

    @Test fun previewsLoadAtMostFivePagesAroundCursor() = runTest(dispatcher) {
        val repository = FakeRepository().apply { count = 100 }
        val vm = ReaderViewModel(repository, "preview", SavedStateHandle(mapOf("page" to 50)), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        vm.action(ReaderAction.StartTimeline)
        advanceUntilIdle()
        assertEquals(listOf(48, 49, 50, 51, 52), vm.previews.value.map { it.page })
        assertEquals(5, repository.thumbnailCalls.size)
        assertEquals(50, repository.thumbnailCalls.first())
    }

    @Test fun automaticIntervalIsAdjustableAndRTLStillAdvancesReadingOrder() = runTest(dispatcher) {
        val vm = ReaderViewModel(FakeRepository().apply { rtl = true }, "auto", SavedStateHandle(mapOf("page" to 0)), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        vm.imageDisplayed(vm.image.value!!.file)
        vm.cycleAutomaticInterval()
        assertEquals(20, vm.automatic.value.secondsPerScreen)
        vm.startAutomaticReading()
        advanceTimeBy(19_999); runCurrent()
        assertEquals(0, vm.state.value.page)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, vm.state.value.page)
        vm.pauseAutomaticReading()
    }

    @Test fun automaticReadingDoesNotSkipAnImageThatFailsToLoad() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = ReaderViewModel(repository, "auto", SavedStateHandle(mapOf("page" to 0)), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        vm.imageDisplayed(vm.image.value!!.file)
        repository.pageFailure = true
        vm.startAutomaticReading()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        assertNotNull(vm.error.value)
        assertFalse(vm.automatic.value.enabled)
    }

    @Test fun automaticReadingWaitsTenSecondsAndStopsAtEnd() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = ReaderViewModel(repository, "auto", SavedStateHandle(mapOf("page" to 0)), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        vm.imageDisplayed(vm.image.value!!.file)
        vm.startAutomaticReading()
        advanceTimeBy(9_999); runCurrent()
        assertEquals(0, vm.state.value.page)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, vm.state.value.page)
        advanceTimeBy(100); runCurrent()
        vm.imageDisplayed(vm.image.value!!.file)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.page)
        assertFalse(vm.automatic.value.enabled)
    }

    @Test fun automaticReadingWaitsForDisplayAndIgnoresStaleRenderCallbacks() = runTest(dispatcher) {
        val vm = ReaderViewModel(FakeRepository(), "render", SavedStateHandle(mapOf("page" to 0)), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        val first = vm.image.value!!.file
        vm.startAutomaticReading()
        advanceTimeBy(20_000); runCurrent()
        assertEquals(0, vm.state.value.page)
        vm.imageDisplayed(File("stale"))
        advanceTimeBy(20_000); runCurrent()
        assertEquals(0, vm.state.value.page)
        vm.imageDisplayed(first)
        advanceTimeBy(10_000); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertEquals(1, vm.state.value.page)
        vm.imageDisplayFailed(first)
        assertNull(vm.error.value)
        assertTrue(vm.automatic.value.enabled)
        vm.imageDisplayFailed(vm.image.value!!.file)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        assertFalse(vm.automatic.value.enabled)
        assertNotNull(vm.error.value)
    }

    @Test fun automaticReadingPausesForManualToolsAndBackground() = runTest(dispatcher) {
        val vm = ReaderViewModel(FakeRepository(), "auto", SavedStateHandle(mapOf("page" to 0)), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        vm.startAutomaticReading()
        vm.action(ReaderAction.Select)
        advanceUntilIdle()
        assertEquals(ReaderMode.Tools, vm.state.value.mode)
        assertEquals(0, vm.state.value.page)
        assertFalse(vm.automatic.value.enabled)
        vm.startAutomaticReading()
        vm.pauseAutomaticReading()
        advanceUntilIdle()
        assertEquals(0, vm.state.value.page)
    }

    @Test fun automaticReadingWaitsForPageLoadAndPausesOnError() = runTest(dispatcher) {
        val repository = FakeRepository().apply { pageDelay = 20_000 }
        val vm = ReaderViewModel(repository, "auto", SavedStateHandle(mapOf("page" to 0)), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        runCurrent()
        vm.startAutomaticReading()
        advanceTimeBy(15_000); runCurrent()
        assertEquals(0, vm.state.value.page)
        repository.pageFailure = true
        advanceUntilIdle()
        assertEquals(0, vm.state.value.page)
        assertNotNull(vm.error.value)
        assertFalse(vm.automatic.value.enabled)
    }

    @Test fun automaticTextWaitsForScrollCompletionInsteadOfPageTimer() = runTest(dispatcher) {
        val file = File.createTempFile("auto-epub", ".txt").apply { writeText("Um capítulo.") }
        try {
            val vm = ReaderViewModel(FakeRepository().apply { textFile = file }, "auto-text", SavedStateHandle(mapOf("page" to 0)),
                ioDispatcher = dispatcher, pageDimensions = { error("Text must not decode") })
            advanceUntilIdle()
            vm.startAutomaticReading()
            advanceTimeBy(60_000); runCurrent()
            assertEquals(0, vm.state.value.page)
            vm.automaticSectionFinished(0)
            advanceTimeBy(200); runCurrent()
            assertEquals(1, vm.state.value.page)
            vm.automaticSectionFinished(0) // A stale callback must not skip the next section.
            assertEquals(1, vm.state.value.page)
            vm.pauseAutomaticReading()
        } finally { file.delete() }
    }

    @Test fun previewsAreDebouncedBoundedAndDoNotChangeReading() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = ReaderViewModel(repository, "preview", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        repository.saved.clear()
        val image = vm.image.value
        vm.action(ReaderAction.StartTimeline)
        advanceTimeBy(100)
        vm.action(ReaderAction.Right)
        advanceTimeBy(179)
        assertTrue(repository.thumbnailCalls.isEmpty())
        advanceUntilIdle()
        assertEquals(2, repository.thumbnailCalls.first())
        assertTrue(repository.thumbnailCalls.all { it in 0..2 })
        assertEquals(3, vm.previews.value.size)
        assertTrue(vm.previews.value.all { it.file != null && !it.loading })
        assertEquals(image, vm.image.value)
        assertEquals(1, vm.state.value.page)
        assertTrue(repository.saved.isEmpty())
    }

    @Test fun closingTimelineDiscardsLatePreviewsAndPreviewErrorsAreIsolated() = runTest(dispatcher) {
        val repository = FakeRepository().apply { thumbnailDelay = 1_000; ignoreThumbnailCancellation = true; thumbnailFailure = true }
        val vm = ReaderViewModel(repository, "preview", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        vm.action(ReaderAction.StartTimeline)
        advanceTimeBy(200)
        vm.action(ReaderAction.Back)
        advanceUntilIdle()
        assertTrue(vm.previews.value.isEmpty())
        assertNull(vm.error.value)
        repository.thumbnailDelay = 0
        vm.action(ReaderAction.StartTimeline)
        advanceUntilIdle()
        assertTrue(vm.previews.value.all { it.failed && !it.loading })
        assertNull(vm.error.value)
        assertEquals(1, vm.state.value.page)
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

    @Test fun timelineDoesNotWriteOrLoadUntilConfirmed() = runTest(dispatcher) {
        val repository = FakeRepository()
        val handle = SavedStateHandle()
        val vm = ReaderViewModel(repository, "timeline", handle, ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        advanceUntilIdle()
        repository.saved.clear()
        val previousImage = vm.image.value
        vm.action(ReaderAction.StartTimeline)
        vm.action(ReaderAction.Right)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        assertEquals(previousImage, vm.image.value)
        assertTrue(repository.saved.isEmpty())
        vm.action(ReaderAction.Select)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.page)
        assertEquals("page-2", vm.image.value!!.file.name)
        assertEquals(listOf(2), repository.saved)
        assertEquals(2, handle.get<Int>("page"))
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

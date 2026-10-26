package com.pelican.comictv.reader

import androidx.lifecycle.SavedStateHandle
import com.pelican.comictv.library.*
import com.pelican.comictv.model.PageRef
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchHandoffTest {
    private class Repository : ReaderRepository {
        val scheduler = PageTransferScheduler()
        val cache = mutableMapOf(0 to File("page-0"))
        val release = CompletableDeferred<Unit>()
        var calls = 0; var cancelled = 0
        override suspend fun getComic(id: String) = ComicEntity(id, "Handoff", 4)
        override suspend fun pages(comicId: String) = (0..3).map { PageRef(it, "online:$it") }
        private suspend fun image(page: PageRef, foreground: Boolean): File = scheduler.load("${page.index}", foreground, { cache[page.index] }) {
            if (page.index == 1) {
                calls++
                try { release.await() } catch (e: CancellationException) { cancelled++; throw e }
            }
            File("page-${page.index}").also { cache[page.index] = it }
        }
        override suspend fun materialize(comicId: String, page: PageRef) = image(page, true)
        override suspend fun prefetch(comicId: String, page: PageRef) = image(page, false)
        override suspend fun thumbnail(comicId: String, page: PageRef) = File("thumb-${page.index}")
        override suspend fun saveProgress(id: String, page: Int) = Unit
        override suspend fun savePreferences(id: String, fit: Int, rtl: Boolean) = Unit
    }
    @Test fun advancingToDownloadingPrefetchUsesOneTransfer() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        val repo = Repository()
        val vm = ReaderViewModel(repo, "handoff", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        try {
            advanceUntilIdle(); vm.imageDisplayed(vm.image.value!!.file)
            advanceTimeBy(151); runCurrent(); assertEquals(1, repo.calls)
            vm.action(ReaderAction.Right); runCurrent()
            assertEquals("Anticipation was restarted instead of reused", 1, repo.calls)
            assertEquals(0, repo.cancelled)
            repo.release.complete(Unit); advanceUntilIdle()
            assertEquals(File("page-1"), vm.image.value?.file)
            assertEquals(1, repo.calls)
        } finally { vm.close { }; advanceUntilIdle(); Dispatchers.resetMain() }
    }
    @Test fun skippingAgainOrClosingCancelsRetainedWorkAndNeverPublishesOldPage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler); Dispatchers.setMain(dispatcher)
        try {
            for (close in listOf(false, true)) {
                val repo = Repository()
                val vm = ReaderViewModel(repo, "cancel-$close", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
                advanceUntilIdle(); vm.imageDisplayed(vm.image.value!!.file)
                advanceTimeBy(151); runCurrent()
                vm.action(ReaderAction.Right); runCurrent()
                if (close) vm.close { } else vm.action(ReaderAction.Right)
                advanceUntilIdle()
                assertEquals(1, repo.cancelled)
                repo.release.complete(Unit); advanceUntilIdle()
                if (!close) assertEquals(File("page-2"), vm.image.value?.file)
                vm.close { }; advanceUntilIdle()
            }
        } finally { Dispatchers.resetMain() }
    }
}

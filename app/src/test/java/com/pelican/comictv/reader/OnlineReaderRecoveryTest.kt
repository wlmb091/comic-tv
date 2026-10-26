package com.pelican.comictv.reader

import androidx.lifecycle.SavedStateHandle
import com.pelican.comictv.library.*
import com.pelican.comictv.model.PageRef
import com.pelican.comictv.sources.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineReaderRecoveryTest {
    @Test fun timedOutImageStopsLoadingAndManualRetryRestoresReading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var attempts = 0
        val repository = object : ReaderRepository {
            override suspend fun getComic(id: String) = ComicEntity(id, "Online", 1)
            override suspend fun pages(comicId: String) = listOf(PageRef(0, "online:0"))
            override suspend fun materialize(comicId: String, page: PageRef): File =
                sourceRequest(SourceRequest.Image, timeoutMillis = 20) {
                    if (++attempts == 1) awaitCancellation() else File("online-ready")
                }
            override suspend fun thumbnail(comicId: String, page: PageRef) = File("thumb")
            override suspend fun saveProgress(id: String, page: Int) = Unit
            override suspend fun savePreferences(id: String, fit: Int, rtl: Boolean) = Unit
        }
        val vm = ReaderViewModel(repository, "online", SavedStateHandle(), ioDispatcher = dispatcher, pageDimensions = { 100 to 200 })
        try {
            advanceUntilIdle()
            assertFalse(vm.loading.value); assertNull(vm.image.value)
            assertTrue(vm.error.value.orEmpty().contains("demorou"))
            vm.retry(); advanceUntilIdle()
            assertFalse(vm.loading.value); assertNull(vm.error.value)
            assertEquals(File("online-ready"), vm.image.value?.file)
            val metrics = com.pelican.comictv.performance.AppTimings
            val stage = com.pelican.comictv.performance.LoadStage.PageDisplay
            val before = metrics.snapshot()[stage]?.count ?: 0L
            vm.imageDisplayed(vm.image.value!!.file)
            vm.imageDisplayed(vm.image.value!!.file)
            assertEquals(before + 1, metrics.snapshot().getValue(stage).count)
        } finally { vm.close { }; advanceUntilIdle(); Dispatchers.resetMain() }
    }
}

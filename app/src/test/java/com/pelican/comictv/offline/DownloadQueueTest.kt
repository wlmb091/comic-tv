package com.pelican.comictv.offline

import com.pelican.comictv.sources.OnlinePage
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueTest {
    @Test fun deletingBeforeStartupRefreshPreservesOtherPersistedQueueEntries() = runTest(dispatcher) {
        val root=Files.createTempDirectory("delete-before-refresh").toFile()
        try {
            val store=OfflineStore(File(root,"downloads"),freeBytes={Long.MAX_VALUE})
            fun repo()=DownloadRepository(File(root,"queue.json"),store,{emptyList()},{_,_,_->},{10 to 20},dispatcher)
            val first=repo();first.refresh();first.enqueue(id)
            val other="online-"+"e".repeat(64);first.enqueue(other)
            val fresh=repo()
            assertTrue(fresh.removeLibraryEdition(id) {true})
            val reopened=repo();reopened.refresh()
            assertEquals(listOf(other),reopened.state.value.queue)
        } finally {root.deleteRecursively()}
    }
    @Test fun deletingLibraryEditionRemovesQueueAndOfflineCopyWithoutTouchingOtherEdition() = runTest(dispatcher) {
        val root=Files.createTempDirectory("delete-online").toFile()
        try {
            val store=OfflineStore(File(root,"downloads"),freeBytes={Long.MAX_VALUE})
            val repo=DownloadRepository(File(root,"queue.json"),store,{listOf(OnlinePage(0,"a"))},
                {_,_,file->file.writeBytes(byteArrayOf(1,2,3))},{10 to 20},dispatcher)
            repo.refresh();repo.enqueue(id);repo.runQueue()
            val other="online-"+"e".repeat(64)
            repo.enqueue(other)
            var deleted=false
            assertTrue(repo.removeLibraryEdition(id) {deleted=true;true})
            assertTrue(deleted);assertFalse(store.hasPackage(id))
            assertEquals(listOf(other),repo.state.value.queue)
            assertFalse(id in repo.state.value.saved)
            val reopened=DownloadRepository(File(root,"queue.json"),store,{emptyList()},{_,_,_->},{10 to 20},dispatcher)
            reopened.refresh();assertEquals(listOf(other),reopened.state.value.queue)
        } finally {root.deleteRecursively()}
    }
    private val dispatcher = StandardTestDispatcher()
    private val id = "online-" + "f".repeat(64)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    @Test fun queuePersistsWithoutDuplicatesAndPauseCanResumeToComplete() = runTest(dispatcher) {
        val root = Files.createTempDirectory("queue").toFile()
        try {
            val store = OfflineStore(File(root,"downloads"),freeBytes = { Long.MAX_VALUE })
            val queueFile = File(root,"queue.json")
            fun repository() = DownloadRepository(queueFile,store, { listOf(OnlinePage(0,"a"),OnlinePage(1,"b")) },
                { _, _, file -> delay(1000); file.writeBytes(byteArrayOf(1,2,3)) }, { 10 to 20 }, dispatcher)
            val first = repository(); first.refresh(); first.enqueue(id); first.enqueue(id)
            assertEquals(listOf(id),first.state.value.queue)
            val repo = repository(); val vm = DownloadsViewModel(repo)
            vm.enter(null); advanceUntilIdle(); assertEquals(listOf(id),vm.state.value.queue)
            vm.start(); advanceTimeBy(500); runCurrent(); vm.pause(); advanceUntilIdle()
            assertFalse(vm.state.value.running); assertFalse(store.isComplete(id))
            vm.start(); advanceUntilIdle(); assertTrue(store.isComplete(id)); assertTrue(vm.state.value.queue.isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun leavingImmediatelyCancelsEntryBeforeItCanStartADownload() = runTest(dispatcher) {
        val root = Files.createTempDirectory("queue-leave").toFile()
        try {
            var calls = 0; var left = false
            val store = OfflineStore(File(root,"downloads"),freeBytes = { Long.MAX_VALUE })
            val repo = DownloadRepository(File(root,"queue.json"),store,{ listOf(OnlinePage(0,"a")) },
                { _,_,file -> calls++; file.writeBytes(byteArrayOf(1)) },{10 to 20},dispatcher)
            val vm = DownloadsViewModel(repo)
            vm.enter(id); vm.leave { left = true }; advanceUntilIdle()
            assertTrue(left); assertEquals(0,calls); assertFalse(vm.state.value.running)
        } finally { root.deleteRecursively() }
    }
    @Test fun laterExplicitResumeCanEnqueueAnotherEdition() = runTest(dispatcher) {
        val root = Files.createTempDirectory("queue-two").toFile()
        try {
            val store = OfflineStore(File(root,"downloads"),freeBytes = { Long.MAX_VALUE })
            val seen = mutableListOf<String>()
            val repo = DownloadRepository(File(root,"queue.json"),store,{seen += it;error("Stop before network")}, {_,_,_->},{10 to 20},dispatcher)
            val vm = DownloadsViewModel(repo)
            vm.enter(id);advanceUntilIdle()
            vm.remove(id);advanceUntilIdle()
            val other = "online-" + "e".repeat(64)
            vm.enter(other);advanceUntilIdle()
            assertEquals(listOf(id,other),seen)
            assertEquals(listOf(other),vm.state.value.queue)
        } finally {root.deleteRecursively()}
    }
}

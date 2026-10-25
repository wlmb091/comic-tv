package com.pelican.comictv.sources

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionDownloadQueueTest {
    @Test fun departingOwnerCannotPauseAnotherVisibleOwner()=runTest {
        val root=Files.createTempDirectory("extension-owners").toFile()
        try {
            val queue=ExtensionDownloadQueue(backgroundScope,{item,_->File(root,item.entry.packageName).apply{writeText("apk")}})
            val old=Any();val current=Any()
            queue.setVisible(old,true);queue.setVisible(current,true);queue.setVisible(old,false)
            queue.toggle(candidate("a"));runCurrent()
            assertEquals(ExtensionTransferStatus.Ready,queue.state.value.single().status)
            queue.setVisible(current,false);queue.toggle(candidate("b"));runCurrent()
            assertEquals(ExtensionTransferStatus.Queued,queue.state.value.last().status)
            queue.close()
        } finally {root.deleteRecursively()}
    }
    @Test fun clearOrLeaveDuringValidationCannotLaunchOrClaimReplacementDownload()=runTest {
        val root=Files.createTempDirectory("extension-validation").toFile()
        try {
            var copies=0;var launched=0
            val queue=ExtensionDownloadQueue(backgroundScope,{_,_->File(root,"copy${copies++}").apply{writeText("apk")}})
            queue.setVisible(true);queue.toggle(candidate("a"));runCurrent()
            val gate=CompletableDeferred<Unit>()
            queue.requestInstall(candidate("a"),{withContext(NonCancellable){gate.await()}},{launched++},{fail(it)})
            runCurrent();queue.clear();queue.toggle(candidate("a"));runCurrent()
            gate.complete(Unit);runCurrent();assertEquals(0,launched)
            val second=CompletableDeferred<Unit>()
            queue.requestInstall(candidate("a"),{withContext(NonCancellable){second.await()}},{launched++},{fail(it)})
            runCurrent();queue.setVisible(false);second.complete(Unit);runCurrent();assertEquals(0,launched)
            queue.setVisible(true)
            queue.requestInstall(candidate("a"),{},{launched++},{fail(it)})
            runCurrent();assertEquals(1,launched)
            val file=queue.state.value.single().file!!
            queue.setVisible(false);queue.setVisible(Any(),true)
            queue.installationReturned(file,true);assertTrue(queue.state.value.isEmpty())
            queue.close()
        } finally {root.deleteRecursively()}
    }
    @Test fun togglingQueuedItemCancelsAndQueueIsBounded()=runTest {
        val queue=ExtensionDownloadQueue(backgroundScope,{_,_->error("Not visible")})
        queue.toggle(candidate("a"));queue.toggle(candidate("a"));assertTrue(queue.state.value.isEmpty())
        repeat(12){queue.toggle(candidate("item$it"))}
        assertThrows(IllegalArgumentException::class.java){queue.toggle(candidate("overflow"))}
        assertEquals(12,queue.state.value.size)
        queue.clear();assertTrue(queue.state.value.isEmpty());queue.close()
    }
    @Test fun cancelledNonCooperativeWriterFinishesCleanupBeforeNextDownload()=runTest {
        val root=Files.createTempDirectory("extension-stale").toFile()
        try {
            val gate=CompletableDeferred<Unit>()
            val queue=ExtensionDownloadQueue(backgroundScope,{item,_->
                if(item.entry.packageName=="pkg.a") withContext(NonCancellable){gate.await()}
                File(root,item.entry.packageName).apply{writeText("apk")}
            })
            queue.setVisible(true);queue.toggle(candidate("a"));runCurrent()
            queue.toggle(candidate("a"));queue.toggle(candidate("b"));runCurrent()
            assertFalse(File(root,"pkg.b").exists())
            gate.complete(Unit);runCurrent()
            assertFalse(File(root,"pkg.a").exists())
            assertEquals(ExtensionTransferStatus.Ready,queue.state.value.single().status)
            assertEquals("pkg.b",queue.state.value.single().candidate.entry.packageName)
            queue.close()
        } finally {root.deleteRecursively()}
    }
    private fun candidate(name:String)=AvailableExtension(SavedRepository("https://repo","Repo","a".repeat(64)),
        ExtensionEntry("pkg.$name",name,"1",1,"1.6","https://repo/$name.apk"))
    @Test fun serialQueueToggleCancelPauseAndReadyInstallProtectOtherFiles()=runTest {
        val root=Files.createTempDirectory("extension-queue").toFile()
        try {
            val seen=mutableListOf<String>();var active=0;var maximum=0
            val queue=ExtensionDownloadQueue(backgroundScope,{item,progress->
                active++;maximum=maxOf(maximum,active);seen+=item.entry.packageName
                try {progress(10,20);delay(1000);File(root,item.entry.packageName).apply {writeText("apk")}} finally {active--}
            })
            queue.setVisible(true)
            queue.toggle(candidate("a"));queue.toggle(candidate("b"));runCurrent()
            assertEquals(ExtensionTransferStatus.Downloading,queue.state.value.first().status)
            queue.toggle(candidate("a"));runCurrent()
            advanceTimeBy(1001);runCurrent()
            assertEquals(1,maximum)
            assertEquals(listOf("pkg.b"),queue.state.value.map{it.candidate.entry.packageName})
            assertEquals(ExtensionTransferStatus.Ready,queue.state.value.single().status)
            val file=queue.claimInstall(candidate("b"))!!
            queue.toggle(candidate("b"));assertTrue(file.exists())
            queue.installationReturned(file,true);assertFalse(file.exists());assertTrue(queue.state.value.isEmpty())
            queue.toggle(candidate("c"));runCurrent();queue.setVisible(false);runCurrent()
            assertEquals(ExtensionTransferStatus.Queued,queue.state.value.single().status)
            queue.setVisible(true);advanceTimeBy(1001);runCurrent()
            assertEquals(ExtensionTransferStatus.Ready,queue.state.value.single().status)
            queue.close()
        } finally {root.deleteRecursively()}
    }
    @Test fun failedDownloadDoesNotBlockNextAndDuplicatePackagesAreNotQueuedTwice()=runTest {
        val root=Files.createTempDirectory("extension-errors").toFile()
        try {
            val queue=ExtensionDownloadQueue(backgroundScope,{item,_->
                if(item.entry.packageName=="pkg.bad") error("Download falhou")
                File(root,item.entry.packageName).apply{writeText("apk")}
            })
            queue.setVisible(true);queue.toggle(candidate("bad"));queue.toggle(candidate("good"));runCurrent()
            assertEquals(ExtensionTransferStatus.Failed,queue.state.value.first().status)
            assertEquals(ExtensionTransferStatus.Ready,queue.state.value.last().status)
            queue.close()
        } finally {root.deleteRecursively()}
    }
}

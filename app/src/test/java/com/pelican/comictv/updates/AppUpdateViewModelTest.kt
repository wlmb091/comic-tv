package com.pelican.comictv.updates

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private class Source : AppUpdateSource {
        var candidate: UpdateManifest? = UpdateManifest(57, "1.1.18", 26, 4, "a".repeat(64), "${UpdateManifest.RELEASE_BASE}/v1.1.18/Comic-TV-1.1.18.apk", "Notas")
        var checks = 0; var installed = 56L; var slow = false; var tampered = false; var offers = 0; var returned = 0
        override suspend fun check(): UpdateManifest? { checks++; if (slow) withContext(NonCancellable) { delay(100) }; return candidate }
        override suspend fun download(update: UpdateManifest, progress: (Long) -> Unit): File { progress(2); return File("ready.apk") }
        override suspend fun revalidate(file: File, update: UpdateManifest) { if (tampered) error("Changed") }
        override suspend fun markOffered(file: File) { offers++ }
        override suspend fun installationReturned(file: File) { returned++ }
        override suspend fun installedVersionCode() = installed
    }
    @Test fun automaticCheckOnceAndInstallAlwaysRevalidates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = Source(); val vm = AppUpdateViewModel(source)
            vm.automaticCheck(); vm.automaticCheck(); advanceUntilIdle()
            assertEquals(1, source.checks); assertEquals(AppUpdatePhase.Available, vm.state.value.phase)
            vm.download(); advanceUntilIdle(); assertEquals(AppUpdatePhase.Ready, vm.state.value.phase)
            source.tampered = true; var launched = false
            vm.install { launched = true }; advanceUntilIdle()
            assertFalse(launched); assertEquals(0, source.offers)
            source.tampered = false; vm.download(); advanceUntilIdle(); vm.install { launched = true }; advanceUntilIdle()
            assertTrue(launched); assertEquals(1, source.offers)
            vm.backgrounded(); assertEquals(AppUpdatePhase.Installing, vm.state.value.phase)
            source.installed = 57; vm.installationReturned(); advanceUntilIdle()
            assertEquals(AppUpdatePhase.Current, vm.state.value.phase)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun cancelledOldCheckCannotPublishOverNewCheck() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = Source().apply { slow = true }; val vm = AppUpdateViewModel(source)
            vm.check(); runCurrent(); vm.cancel()
            source.slow = false; source.candidate = null
            vm.check(); advanceUntilIdle()
            assertEquals(AppUpdatePhase.Current, vm.state.value.phase)
            assertNull(vm.state.value.update)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun permissionReturnRejectsChangedApkAndReleasesItsOfferedMarker() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source=Source();val vm=AppUpdateViewModel(source)
            vm.check();advanceUntilIdle();vm.download();advanceUntilIdle();vm.install{};advanceUntilIdle()
            source.tampered=true;var launched=false
            vm.continueInstall(File("ready.apk")){launched=true};advanceUntilIdle()
            assertFalse(launched);assertEquals(1,source.returned)
            assertEquals(AppUpdatePhase.Failed,vm.state.value.phase)
        } finally {Dispatchers.resetMain()}
    }
}

package com.pelican.comictv.sources

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class SourceRequestTest {
    @Test fun linkageFailureRetainsCompatibilityGuidance() = runTest {
        try {
            sourceRequest(SourceRequest.PageList) { throw NoSuchMethodError("private-class") }
            fail("Expected compatibility failure")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("API ainda não compatível"))
            assertFalse(e.message.orEmpty().contains("private-class"))
        }
    }
    @Test fun deadlineIsRecoverableAndRetryDoesNotInheritCancellation() = runTest {
        var cleaned = false
        val error = try {
            sourceRequest(SourceRequest.Image, timeoutMillis = 50) {
                try { awaitCancellation() } finally { cleaned = true }
            }
            error("Expected timeout")
        } catch (e: IOException) { e }
        assertTrue(cleaned)
        assertTrue(error.message.orEmpty().contains("demorou"))
        assertTrue(currentCoroutineContext().isActive)
        assertEquals("ready", sourceRequest(SourceRequest.Image) { "ready" })
    }
    @Test fun sourcesOwnTimeoutIsReportedWhileCallerRemainsActive() = runTest {
        val failure = try {
            sourceRequest(SourceRequest.Catalog) { withTimeout(10) { delay(20) }; "never" }
            error("Expected timeout")
        } catch (e: IOException) { e }
        assertTrue(failure.cause is TimeoutCancellationException)
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun externalCancellationWinsOverSocketFailureAndLateSuccess() = runTest {
        for (socketFailure in listOf(false, true)) {
            var published = false
            var reported = false
            val entered = CompletableDeferred<Unit>()
            val job = launch {
                try {
                    sourceRequest(SourceRequest.Image) {
                        entered.complete(Unit)
                        if (socketFailure) {
                            try { awaitCancellation() } catch (_: CancellationException) { throw SocketException("closed") }
                        } else withContext(NonCancellable) { delay(100); "late" }
                    }
                    published = true
                } catch (_: CancellationException) { }
                catch (_: Exception) { reported = true }
            }
            entered.await(); job.cancel(); advanceUntilIdle(); job.join()
            assertFalse(published); assertFalse(reported)
        }
    }
    @Test fun externalDeadlineRemainsCancellation() = runTest {
        var converted = false
        val result = withTimeoutOrNull(20) {
            try { sourceRequest(SourceRequest.PageList) { awaitCancellation() } }
            catch (_: IOException) { converted = true }
        }
        assertNull(result); assertFalse(converted)
    }
    @Test fun httpGuidanceDoesNotExposeResponsePayloadOrAddress() = runTest {
        for (code in listOf(401, 403, 404, 410, 429, 503)) {
            val failure = try {
                sourceRequest(SourceRequest.Image) { throw IOException("https://private/?token=secret", HttpException(code)) }
                error("Expected failure")
            } catch (e: IOException) { e }
            assertTrue(failure.message.orEmpty().contains("HTTP $code"))
            assertFalse(failure.message.orEmpty().contains("secret"))
        }
    }
    @Test fun networkFailureIsActionableButValidationFailureKeepsItsMeaning() = runTest {
        try {
            sourceRequest(SourceRequest.Catalog) { throw UnknownHostException("private-host") }
            fail("Expected network failure")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("conexão"))
            assertFalse(e.message.orEmpty().contains("private-host"))
        }
        val invalid = IllegalArgumentException("Limite de imagem excedido")
        try { sourceRequest(SourceRequest.Image) { throw invalid }; fail("Expected validation failure") }
        catch (e: IllegalArgumentException) { assertEquals(invalid.message, e.message) }
    }
}

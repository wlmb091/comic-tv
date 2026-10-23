package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.CancellationException

class ExtensionOperationTest {
    @Test fun missingOptionalApiBecomesRecoverableReaderError() {
        val missing = AbstractMethodError("getPageList")
        try {
            extensionOperation<Unit> { throw missing }
            fail("Expected recoverable error")
        } catch (error: IOException) { assertSame(missing, error.cause) }
    }
    @Test fun cancellationIsPreserved() {
        val cancellation = CancellationException("leave reader")
        try {
            extensionOperation<Unit> { throw cancellation }
            fail("Expected cancellation")
        } catch (error: CancellationException) { assertSame(cancellation, error) }
    }
}

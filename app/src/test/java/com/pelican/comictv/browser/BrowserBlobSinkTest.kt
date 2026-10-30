package com.pelican.comictv.browser

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class BrowserBlobSinkTest {
    @Test fun chunksAreBoundedOrderedAndRequireAnExactCompleteLength() {
        val dir = Files.createTempDirectory("blob-test").toFile(); val file = File(dir, "file")
        val sink = BrowserBlobSink(file, 100)
        try {
            sink.start(6)
            sink.chunk(0, Base64.getEncoder().encodeToString("abc".toByteArray()))
            rejected { sink.chunk(2, "ZGVm") }
            rejected { sink.finish(1) }
            sink.chunk(1, "ZGVm"); assertEquals(6L, sink.finish(2)); assertEquals("abcdef", file.readText())
        } finally { sink.close(); file.delete(); dir.delete() }
    }
    @Test fun arbitraryOrCrossOriginBlobIsNotTreatedAsHttpAndBodyCannotExceedLimit() {
        assertNull(browserBlobOrigin("blob:null/id")); assertEquals("https://example.org", browserBlobOrigin("blob:https://example.org/id"))
        rejected { BrowserPolicy.url("blob:https://example.org/id") }
        val dir = Files.createTempDirectory("blob-test").toFile(); val file = File(dir, "file"); val sink = BrowserBlobSink(file, 3)
        try { rejected { sink.start(4) }; assertFalse(file.exists()) }
        finally { sink.close(); file.delete(); dir.delete() }
    }
    private fun rejected(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IOException) { } }
}

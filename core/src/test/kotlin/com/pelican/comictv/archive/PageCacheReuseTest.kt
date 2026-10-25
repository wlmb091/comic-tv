package com.pelican.comictv.archive

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PageCacheReuseTest {
    @Test fun repeatedHitsAvoidDirectoryScansButNewWritesAndExplicitTrimEnforceQuota() {
        val root=Files.createTempDirectory("cache-reuse").toFile()
        try {
            var scans=0
            val cache=PageCache(root,10,scanFiles={scans++;root.listFiles()})
            cache.materialize("one",emptySet(),{it.writeBytes(ByteArray(6))},{})
            repeat(20){cache.materialize("one",emptySet(),{fail("Cache hit rewrote file")},{});cache.trimIfNeeded()}
            assertEquals(1,scans)
            cache.materialize("two",emptySet(),{it.writeBytes(ByteArray(6))},{})
            assertEquals(2,scans);assertFalse(File(root,"one").exists());assertTrue(File(root,"two").exists())
            File(root,"external").writeBytes(ByteArray(8))
            cache.trim(setOf("two"));assertEquals(3,scans);assertFalse(File(root,"external").exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun switchingProtectionRechecksPreviouslyUnavoidableOverage() {
        val root=Files.createTempDirectory("cache-protected").toFile()
        try {
            val cache=PageCache(root,5)
            cache.materialize("one",emptySet(),{it.writeBytes(ByteArray(8))},{})
            cache.materialize("two",setOf("one"),{it.writeBytes(ByteArray(3))},{})
            assertTrue(File(root,"one").exists())
            cache.trimIfNeeded(setOf("two"))
            assertFalse(File(root,"one").exists());assertTrue(File(root,"two").exists())
        } finally {root.deleteRecursively()}
    }
}

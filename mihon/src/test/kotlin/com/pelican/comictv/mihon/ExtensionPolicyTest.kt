package com.pelican.comictv.mihon

import org.junit.Assert.*
import org.junit.Test

class ExtensionPolicyTest {
    @Test fun relativeClassesExpandAndEmptyEntriesAreIgnored() {
        assertEquals(listOf("pkg.Main", "other.Factory"), ExtensionPolicy.classNames("pkg", " ; .Main;other.Factory; "))
    }
    @Test fun pinAcceptsColonSeparatedUppercase() {
        val pin = "ab".repeat(32)
        ExtensionPolicy.requireTrusted(setOf(pin), pin.uppercase().chunked(2).joinToString(":"))
    }
    @Test fun mismatchedCertificateIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ExtensionPolicy.requireTrusted(setOf("aa".repeat(32)), "bb".repeat(32)) }
    }
    @Test fun unsignedAndAdditionalSignerAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ExtensionPolicy.requireTrusted(emptySet(), "ab".repeat(32)) }
        assertThrows(IllegalArgumentException::class.java) { ExtensionPolicy.requireTrusted(setOf("ab".repeat(32), "cd".repeat(32)), "ab".repeat(32)) }
    }
    @Test fun onlyKnownLibraryRangeIsCompatible() {
        assertTrue(ExtensionPolicy.supported("1.6")); assertTrue(ExtensionPolicy.supported("1.2"))
        assertFalse(ExtensionPolicy.supported("1.7")); assertFalse(ExtensionPolicy.supported(null))
    }
    @Test fun certificateHashUsesSha256() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ExtensionPolicy.fingerprint(byteArrayOf()))
    }
}

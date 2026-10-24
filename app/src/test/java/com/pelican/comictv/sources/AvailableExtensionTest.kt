package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class AvailableExtensionTest {
    private val repository = SavedRepository("https://example.test", "Repository", "a".repeat(64))
    private val candidate = AvailableExtension(repository,
        ExtensionEntry("extension.example", "Example", "1", 1L, "1.4", "https://example.test/example.apk"))

    @Test fun rejectsRemovedOrRepinnedRepository() {
        assertFalse(candidate.isCurrentTrustedCandidate(listOf(candidate), emptyList()))
        assertFalse(candidate.isCurrentTrustedCandidate(listOf(candidate), listOf(repository.copy(fingerprint = "b".repeat(64)))))
    }

    @Test fun rejectsStaleOrUnlistedEntry() {
        assertFalse(candidate.isCurrentTrustedCandidate(emptyList(), listOf(repository)))
        val modified = candidate.copy(entry = candidate.entry.copy(apkUrl = "https://example.test/other.apk"))
        assertFalse(modified.isCurrentTrustedCandidate(listOf(candidate), listOf(repository)))
    }

    @Test fun permitsCurrentEntryWithSamePinnedOriginAfterDisplayNameChange() {
        assertTrue(candidate.isCurrentTrustedCandidate(listOf(candidate), listOf(repository.copy(name = "New name"))))
    }
}

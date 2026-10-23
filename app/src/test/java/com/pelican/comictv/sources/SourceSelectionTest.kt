package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class SourceSelectionTest {
    private val saved = SourceSelection("trusted.package", 93L)
    private fun source(pkg: String = "trusted.package", id: Long = 93L, name: String = "HQNow") =
        SourceHandle(pkg, id, name, "pt-BR", "https://example.test")

    @Test fun restoresOnlyExactPackageAndSourceIdFromLoadedSources() {
        val expected = source(name = "Updated name")
        assertSame(expected, saved.resolve(listOf(source(pkg = "other.package"), source(id = 94L), expected)))
    }

    @Test fun sourceWithSameNameOrIdInAnotherPackageIsUnavailable() {
        assertNull(saved.resolve(listOf(source(pkg = "untrusted.package"), source(id = 94L))))
    }

    @Test fun removedOrFailedTrustedExtensionFallsBackWithoutSelectingAnAlternative() {
        assertNull(saved.resolve(emptyList()))
        assertNull(saved.resolve(listOf(source(pkg = "replacement.package"))))
    }
}

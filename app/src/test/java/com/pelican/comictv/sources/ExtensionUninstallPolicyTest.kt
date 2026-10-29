package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class ExtensionUninstallPolicyTest {
    private val source = SourceHandle("eu.example.extension", 1, "Fonte", "pt", "https://example.org")
    private val installed = InstalledExtension(source.packageName, "Extensão", 1, "1", emptySet(), true)
    @Test fun onlyInstalledListedSourceCanBeOfferedForRemoval() {
        assertTrue(canUninstallSource(source, listOf(source), listOf(installed), "com.pelican.comictv"))
        assertFalse(canUninstallSource(source, listOf(source), emptyList(), "com.pelican.comictv"))
        assertFalse(canUninstallSource(source, emptyList(), listOf(installed), "com.pelican.comictv"))
        assertFalse(canUninstallSource(source, listOf(source), listOf(installed), source.packageName))
    }
    @Test fun removedExtensionStaysAbsentUntilInstalledAgain() {
        val sibling = source.copy(id = 2, name = "Outra fonte")
        for (row in listOf(source, sibling)) {
            assertFalse(canUninstallSource(row, emptyList(), emptyList(), "com.pelican.comictv"))
            assertTrue(canUninstallSource(row, listOf(source, sibling), listOf(installed), "com.pelican.comictv"))
        }
    }
}

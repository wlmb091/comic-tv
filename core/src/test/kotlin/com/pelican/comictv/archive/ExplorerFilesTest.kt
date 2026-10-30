package com.pelican.comictv.archive

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ExplorerFilesTest {
    @Test fun supportedFilesKeepApkSeparateFromReadingFormats() {
        assertEquals(ExplorerFileKind.Install, ExplorerFiles.kind("app.APK"))
        listOf("a.cbz", "a.cbr", "a.cb7", "a.7z", "a.pdf", "a.epub").forEach {
            assertEquals(ExplorerFileKind.Import, ExplorerFiles.kind(it))
        }
        assertEquals(ExplorerFileKind.Other, ExplorerFiles.kind("app.apk.part"))
    }
    @Test fun confinementRejectsSiblingAndSymlinkEscape() {
        val root = Files.createTempDirectory("explorer-test").toFile()
        try {
            val child = File(root, "books").apply { mkdir() }
            assertTrue(ExplorerFiles.inside(root, child))
            assertTrue(ExplorerFiles.inside(root, root))
            assertFalse(ExplorerFiles.inside(child, root))
            assertFalse(ExplorerFiles.inside(root, File(root.parentFile, root.name + "-outside")))
            assertFalse(ExplorerFiles.inside(root, File(root, "../escape")))
        } finally { root.deleteRecursively() }
    }
    @Test fun listingSortsDirectoriesFirstAndKeepsUnknownFilesVisible() {
        val root = Files.createTempDirectory("explorer-list").toFile()
        try {
            File(root, "z-books").mkdir()
            File(root, "B.cbz").writeText("b")
            File(root, "a.txt").writeText("a")
            val rows = ExplorerFiles.list(root, root)
            assertEquals(listOf("z-books", "a.txt", "B.cbz"), rows.map { it.name })
            assertTrue(rows.first().isDirectory)
            assertEquals(3, rows.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun listingIsBoundedAndDoesNotReadParent() {
        val root = Files.createTempDirectory("explorer-limit").toFile()
        try {
            repeat(8) { File(root, "$it.cbz").writeText("x") }
            assertEquals(3, ExplorerFiles.list(root, root, limit = 3).size)
            assertThrows(IllegalArgumentException::class.java) { ExplorerFiles.list(root, root.parentFile) }
        } finally { root.deleteRecursively() }
    }
}

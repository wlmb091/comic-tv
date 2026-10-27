package com.pelican.comictv.cinema

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CinemaArtworkStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun store(root: File = temporary.newFolder(), maxFile: Long = 32,
        maxTotal: Long = 128, reserve: Long = 8, free: () -> Long = { 1024 },
        validator: (File) -> Unit = {}): CinemaArtworkStore =
        CinemaArtworkStore(root, validator, maxFile, maxTotal, reserve, free)

    private fun put(store: CinemaArtworkStore, id: String, bytes: ByteArray): File =
        store.stage(ByteArrayInputStream(bytes)).use { store.commit(id, it) }

    private inline fun <reified T : Throwable> fails(block: () -> Unit): T {
        try { block() } catch (failure: Throwable) {
            if (failure is T) return failure
            throw AssertionError("Expected ${T::class.java.name}, got $failure", failure)
        }
        throw AssertionError("Expected ${T::class.java.name}")
    }

    @Test fun rejectsInvalidIdentifiersInEveryOperation() {
        val store = store()
        listOf("", "../x", "a/b", "a\\b", ".", "a.ref", "a_1", "a".repeat(129)).forEach { id ->
            fails<IllegalArgumentException> { store.file(id) }
            fails<IllegalArgumentException> { store.remove(id) }
            store.stage(ByteArrayInputStream(byteArrayOf(1))).use { staged ->
                fails<IllegalArgumentException> { store.commit(id, staged) }
            }
        }
        assertNull(store.file("A-z09"))
    }

    @Test fun boundedCopyDoesNotCloseInputOrLeaveParts() {
        val root = temporary.newFolder()
        var closed = false
        val input = object : ByteArrayInputStream(ByteArray(33)) {
            override fun close() { closed = true; super.close() }
        }
        fails<IOException> { store(root).stage(input) }
        assertFalse(closed)
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test fun validatorReadsCompleteFileAndFailureCleansOnlyOwnPart() {
        val root = temporary.newFolder()
        val marker = File(root, "unknown.part").apply { writeText("keep") }
        val store = store(root, validator = {
            assertArrayEquals(byteArrayOf(1, 2, 3), it.readBytes())
            throw IOException("invalid image")
        })
        fails<IOException> { store.stage(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
        assertEquals(listOf(marker), root.listFiles()!!.toList())
    }

    @Test fun cancellationDuringCopyAndBeforeValidationCleansPreparation() {
        val root = temporary.newFolder()
        var validations = 0
        val store = store(root, maxFile = 100_000, free = { 200_000 }, validator = { validations++ })
        var calls = 0
        fails<CancellationException> {
            store.stage(ByteArrayInputStream(ByteArray(70_000))) {
                if (++calls == 3) throw CancellationException()
            }
        }
        assertTrue(root.listFiles()!!.isEmpty())
        calls = 0
        fails<CancellationException> {
            store.stage(ByteArrayInputStream(byteArrayOf(1))) {
                if (++calls == 4) throw CancellationException()
            }
        }
        assertEquals(0, validations)
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test fun successfulReplacementUsesNewPathAndSurvivesReopen() {
        val root = temporary.newFolder()
        val store = store(root)
        val old = put(store, "book", byteArrayOf(1))
        val replacement = put(store, "book", byteArrayOf(2, 3))
        assertNotEquals(old, replacement)
        assertFalse(old.exists())
        assertArrayEquals(byteArrayOf(2, 3), replacement.readBytes())
        assertEquals(replacement, store.file("book"))
        assertEquals(replacement, store(root).file("book"))
        assertEquals(2L, store.totalBytes())
    }

    @Test fun closedConsumedAndForeignPreparationsAreRejected() {
        val store = store()
        val staged = store.stage(ByteArrayInputStream(byteArrayOf(1)))
        staged.close()
        staged.close()
        fails<IllegalStateException> { store.commit("book", staged) }
        val foreign = store().stage(ByteArrayInputStream(byteArrayOf(2)))
        foreign.use { fails<IllegalArgumentException> { store.commit("book", it) } }
        store.stage(ByteArrayInputStream(byteArrayOf(3))).use {
            store.commit("book", it)
            fails<IllegalStateException> { store.commit("other", it) }
        }
    }

    @Test fun quotaAccountsForReplacementWithoutEvictingOtherArtwork() {
        val store = store(maxTotal = 5)
        val first = put(store, "first", byteArrayOf(1, 2))
        val second = put(store, "second", byteArrayOf(3, 4, 5))
        store.stage(ByteArrayInputStream(byteArrayOf(6, 7, 8))).use {
            fails<IOException> { store.commit("first", it) }
        }
        assertEquals(first, store.file("first"))
        assertEquals(second, store.file("second"))
        assertArrayEquals(byteArrayOf(1, 2), first.readBytes())
        put(store, "first", byteArrayOf(9, 10))
        assertEquals(5L, store.totalBytes())
    }

    @Test fun reserveIsCheckedWhileCopyingAndAgainBeforeCommit() {
        val root = temporary.newFolder()
        var free = 9L
        val store = store(root, free = { free })
        fails<IOException> { store.stage(ByteArrayInputStream(byteArrayOf(1, 2))) }
        assertTrue(root.listFiles()!!.isEmpty())
        free = 1024
        val old = put(store, "book", byteArrayOf(1))
        store.stage(ByteArrayInputStream(byteArrayOf(2))).use {
            free = 8
            fails<IOException> { store.commit("book", it) }
        }
        assertEquals(old, store.file("book"))
        assertArrayEquals(byteArrayOf(1), old.readBytes())
    }

    @Test fun cancelledCommitKeepsPreviousVersionAndRemovesMovedPreparation() {
        val root = temporary.newFolder()
        val store = store(root)
        val old = put(store, "book", byteArrayOf(1))
        var checks = 0
        store.stage(ByteArrayInputStream(byteArrayOf(2))).use {
            fails<CancellationException> {
                store.commit("book", it) { if (++checks == 2) throw CancellationException() }
            }
        }
        assertEquals(old, store.file("book"))
        assertArrayEquals(byteArrayOf(1), old.readBytes())
        assertEquals(2, root.listFiles()!!.size)
    }

    @Test fun failedReferencePublicationKeepsPreviousVersion() {
        val root = temporary.newFolder()
        val store = store(root)
        val old = put(store, "book", byteArrayOf(1))
        // Simulate a filesystem obstruction at publication, after target bytes were prepared.
        val ref = File(root, "book.ref")
        var checks = 0
        val savedRef = ref.readBytes()
        store.stage(ByteArrayInputStream(byteArrayOf(2))).use {
            fails<IOException> {
                store.commit("book", it) {
                    if (++checks == 2) {
                        assertTrue(ref.delete())
                        assertTrue(ref.mkdir())
                    }
                }
            }
        }
        assertArrayEquals(byteArrayOf(1), old.readBytes())
        assertTrue(ref.delete())
        ref.writeBytes(savedRef)
        assertEquals(old, store.file("book"))
        assertEquals(1L, store.totalBytes())
    }

    @Test fun removalAndStartupCleanupDoNotDeleteUnknownFiles() {
        val root = temporary.newFolder()
        val store = store(root)
        put(store, "book", byteArrayOf(1, 2))
        val unknown = File(root, "user-image.jpg").apply { writeText("keep") }
        val unknownPart = File(root, "other.part").apply { writeText("keep") }
        val stale = File(root, "${UUID.randomUUID()}.part").apply { writeText("stale") }
        val orphan = File(root, "${UUID.randomUUID()}.art").apply { writeText("orphan") }
        val reopened = store(root)
        assertFalse(stale.exists())
        assertFalse(orphan.exists())
        assertTrue(reopened.remove("book"))
        assertFalse(reopened.remove("book"))
        assertEquals(0L, reopened.totalBytes())
        assertEquals("keep", unknown.readText())
        assertEquals("keep", unknownPart.readText())
    }

    @Test fun referenceTraversalCannotReadOrDeleteOutsideFile() {
        val root = temporary.newFolder()
        val outside = temporary.newFile().apply { writeText("private") }
        val store = store(root)
        File(root, "book.ref").writeText("../${outside.name}")
        fails<IOException> { store.file("book") }
        fails<IOException> { store.remove("book") }
        assertEquals("private", outside.readText())
    }

    @Test fun symbolicLinksAreRejectedAndNeverCleanedOrFollowed() {
        val root = temporary.newFolder()
        val outside = temporary.newFile().apply { writeText("private") }
        val linked = File(root, "${UUID.randomUUID()}.art")
        try { Files.createSymbolicLink(linked.toPath(), outside.toPath()) }
        catch (failure: Exception) { assumeNoException("Symbolic links unavailable on this host", failure); return }
        val store = store(root)
        assertTrue(Files.isSymbolicLink(linked.toPath()))
        assertEquals(0L, store.totalBytes())
        File(root, "book.ref").writeText(linked.name)
        fails<IOException> { store.file("book") }
        fails<IOException> { store.remove("book") }
        assertEquals("private", outside.readText())
        val linkedRoot = File(root, "root-link")
        Files.createSymbolicLink(linkedRoot.toPath(), temporary.newFolder().toPath())
        fails<IOException> { store(linkedRoot) }
    }
}

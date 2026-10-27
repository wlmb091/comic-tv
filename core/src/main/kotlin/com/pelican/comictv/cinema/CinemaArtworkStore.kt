package com.pelican.comictv.cinema

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

/** A single-use, store-owned preparation. Closing never closes the caller's input stream. */
class StagedArtwork internal constructor(
    internal val owner: Any,
    internal val path: File,
    internal val size: Long,
    private val cleanup: (File) -> Unit,
) : Closeable {
    internal var active = true

    override fun close() = synchronized(this) {
        if (active) {
            cleanup(path)
            active = false
        }
    }
}

/** Disk-only artwork storage. The app must use a private directory and serialize library mutations.
 * Validation reads a complete staged file; bytes are never recompressed. Readers receive immutable,
 * versioned paths. No artwork is evicted to make room for another one.
 */
class CinemaArtworkStore(
    root: File,
    private val validate: (File) -> Unit,
    private val maxFileBytes: Long = 20L * 1024 * 1024,
    private val maxTotalBytes: Long = 128L * 1024 * 1024,
    private val reserveBytes: Long = 64L * 1024 * 1024,
    private val freeBytes: () -> Long = { root.usableSpace },
) {
    private val requestedRoot = root.absoluteFile.normalize()
    private val directory: File
    private val owner = Any()

    init {
        require(maxFileBytes > 0 && maxTotalBytes > 0 && reserveBytes >= 0)
        if (Files.isSymbolicLink(requestedRoot.toPath())) throw IOException("Artwork directory is a link")
        if (!requestedRoot.isDirectory && !requestedRoot.mkdirs()) throw IOException("Cannot create artwork directory")
        directory = requestedRoot.canonicalFile
        // Android may alias a trusted ancestor (/data/user/0); the root itself must not redirect.
        val expectedRoot = File(requestedRoot.parentFile.canonicalFile, requestedRoot.name)
        if (directory != expectedRoot) throw IOException("Artwork directory redirects outside its parent")
        checkRoot()
        // Only recognized owned files may be recovered. Unknown files and links are untouched.
        val files = directory.listFiles() ?: throw IOException("Cannot list artwork directory")
        val referenced = files.filter { REF.matches(it.name) && safeRegular(it) }
            .mapNotNull { runCatching { readReference(it)?.name }.getOrNull() }.toSet()
        files.filter { (PART.matches(it.name) || (ART.matches(it.name) && it.name !in referenced)) && safeRegular(it) }
            .forEach { Files.deleteIfExists(it.toPath()) }
    }

    /** Does not close [input]. Cancellation/validation/space failures remove only this preparation. */
    fun stage(input: InputStream, checkActive: () -> Unit = {}): StagedArtwork {
        checkRoot()
        checkActive()
        val part = child("${UUID.randomUUID()}.part")
        Files.createFile(part.toPath())
        try {
            var size = 0L
            FileOutputStream(part).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (count.toLong() > maxFileBytes - size) throw IOException("Artwork exceeds file limit")
                    requireSpace(count.toLong())
                    checkRoot()
                    output.write(buffer, 0, count)
                    size += count
                }
                output.fd.sync()
            }
            checkActive()
            ensureRegular(part)
            validate(part)
            checkActive()
            ensureRegular(part)
            if (part.length() != size) throw IOException("Validator changed artwork")
            return StagedArtwork(owner, part, size, ::deleteOwned)
        } catch (failure: Throwable) {
            runCatching { deleteOwned(part) }
            throw failure
        }
    }

    @Synchronized
    fun commit(id: String, staged: StagedArtwork, checkActive: () -> Unit = {}): File {
        checkId(id)
        require(staged.owner === owner) { "Preparation belongs to another store" }
        return synchronized(staged) {
            check(staged.active) { "Preparation is already consumed" }
            checkRoot()
            ensureRegular(staged.path)
            if (staged.path.length() != staged.size) throw IOException("Preparation changed")
            checkActive()
            val ref = child("$id.ref")
            val previous = readReference(ref)
            val used = totalBytes()
            val replacedBytes = previous?.length() ?: 0L
            if (staged.size > maxTotalBytes - (used - replacedBytes)) throw IOException("Artwork storage limit reached")
            requireSpace(128L) // Staged bytes already occupy disk; replacement retains old bytes until publication.
            val target = child("${UUID.randomUUID()}.art")
            val pendingRef = child("${UUID.randomUUID()}.ref.part")
            var published = false
            try {
                move(staged.path, target, replace = false)
                Files.createFile(pendingRef.toPath())
                FileOutputStream(pendingRef).use {
                    it.write(target.name.toByteArray(Charsets.US_ASCII))
                    it.fd.sync()
                }
                checkActive()
                checkRoot()
                // Ref may be absent, but it must never be a link or directory.
                if (Files.exists(ref.toPath(), NOFOLLOW_LINKS)) ensureRegular(ref)
                move(pendingRef, ref, replace = true)
                published = true
                staged.active = false
                // Publication is the commit point. Cleanup failure must not report a failed commit.
                previous?.let { runCatching { deleteOwned(it) } }
                target
            } finally {
                runCatching { deleteOwned(pendingRef) }
                if (!published) {
                    runCatching { deleteOwned(target) }
                    runCatching { deleteOwned(staged.path) }
                    staged.active = false
                }
            }
        }
    }

    @Synchronized
    fun file(id: String): File? {
        checkId(id)
        checkRoot()
        return readReference(child("$id.ref"))
    }

    @Synchronized
    fun remove(id: String): Boolean {
        checkId(id)
        checkRoot()
        val ref = child("$id.ref")
        val previous = readReference(ref)
        if (!Files.exists(ref.toPath(), NOFOLLOW_LINKS)) return false
        ensureRegular(ref)
        Files.delete(ref.toPath())
        previous?.let { runCatching { deleteOwned(it) } }
        return true
    }

    /** Actual owned final bytes, including any orphan left by an interrupted cleanup. */
    @Synchronized
    fun totalBytes(): Long {
        checkRoot()
        return (directory.listFiles() ?: throw IOException("Cannot list artwork directory"))
            .filter { ART.matches(it.name) && safeRegular(it) }.sumOf { it.length() }
    }

    private fun readReference(ref: File): File? {
        if (!Files.exists(ref.toPath(), NOFOLLOW_LINKS)) return null
        ensureRegular(ref)
        if (ref.length() !in 1L..128L) throw IOException("Invalid artwork reference")
        val name = ref.readText(Charsets.US_ASCII)
        if (!ART.matches(name)) throw IOException("Invalid artwork reference")
        val result = child(name)
        if (!Files.exists(result.toPath(), NOFOLLOW_LINKS)) return null
        ensureRegular(result)
        return result
    }

    private fun requireSpace(extra: Long) {
        val free = freeBytes()
        if (free < reserveBytes || extra > free - reserveBytes) throw IOException("Insufficient free artwork space")
    }

    private fun checkRoot() {
        if (Files.isSymbolicLink(requestedRoot.toPath()) || !requestedRoot.isDirectory ||
            requestedRoot.canonicalFile != directory) throw IOException("Unsafe artwork directory")
    }

    private fun child(name: String): File {
        checkRoot()
        val file = File(directory, name)
        if (file.parentFile != directory || Files.isSymbolicLink(file.toPath()) ||
            file.canonicalFile != file) throw IOException("Unsafe artwork path")
        return file
    }

    private fun safeRegular(file: File) = !Files.isSymbolicLink(file.toPath()) &&
        Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.canonicalFile == file

    private fun ensureRegular(file: File) {
        checkRoot()
        if (file.parentFile != directory || !safeRegular(file)) throw IOException("Unsafe artwork file")
    }

    private fun deleteOwned(file: File) {
        checkRoot()
        if (!Files.exists(file.toPath(), NOFOLLOW_LINKS)) return
        ensureRegular(file)
        Files.delete(file.toPath())
    }

    private fun move(from: File, to: File, replace: Boolean) {
        ensureRegular(from)
        child(to.name)
        val options = if (replace) arrayOf(ATOMIC_MOVE, REPLACE_EXISTING) else arrayOf(ATOMIC_MOVE)
        try {
            Files.move(from.toPath(), to.toPath(), *options)
        } catch (failure: AtomicMoveNotSupportedException) {
            if (replace) throw IOException("Armazenamento sem troca atômica; a arte anterior foi preservada.", failure)
            else Files.move(from.toPath(), to.toPath())
        }
    }

    private fun checkId(id: String) = require(ID.matches(id)) { "Invalid artwork id" }

    private companion object {
        val ID = Regex("[A-Za-z0-9-]{1,128}")
        const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        val ART = Regex("$UUID_PATTERN\\.art")
        val PART = Regex("$UUID_PATTERN(?:\\.ref)?\\.part")
        val REF = Regex("[A-Za-z0-9-]{1,128}\\.ref")
    }
}

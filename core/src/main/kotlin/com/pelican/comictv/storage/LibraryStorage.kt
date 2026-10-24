package com.pelican.comictv.storage

import java.io.File
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

enum class CacheSelection(val label: String) { Pages("cache de páginas"), Thumbnails("miniaturas"), Both("páginas e miniaturas") }
data class StorageReport(val originals: Long, val covers: Long, val pages: Long, val thumbnails: Long,
    val freeBytes: Long, val skipped: Int = 0)
data class CacheCleanup(val freedBytes: Long, val failures: Int)

/** Only caller-owned directories are visited. Symlinks and canonical escapes are never followed. */
class LibraryStorage(private val originals: File, private val covers: File, private val pages: File,
    private val thumbnails: File, private val remove: (File) -> Boolean = { it.delete() }) {
    private fun visit(root: File, blocked: () -> Unit, file: (File) -> Unit) {
        // Context paths may have a trusted Android alias in an ancestor (/data/user/0).
        // Resolve that ancestor, but reject a redirect at the cache root or any child.
        fun checkedCanonical(item: File): File? {
            val absolute = item.absoluteFile.normalize()
            if (Files.isSymbolicLink(absolute.toPath())) return null
            val expected = absolute.parentFile?.let { File(it.canonicalFile, absolute.name) } ?: absolute
            return absolute.canonicalFile.takeIf { it == expected }
        }
        val base = try { checkedCanonical(root) }
            catch (_: java.io.IOException) { null } catch (_: SecurityException) { null }
        if (base == null) { blocked(); return }
        fun walk(item: File) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Operação cancelada")
            try {
                val canonical = checkedCanonical(item)
                if (canonical == null || !canonical.toPath().startsWith(base.toPath())) { blocked(); return }
                val path = item.toPath()
                if (!Files.exists(path, NOFOLLOW_LINKS)) return
                if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
                    val children = item.listFiles()
                    if (children == null) blocked() else children.forEach(::walk)
                } else if (Files.isRegularFile(path, NOFOLLOW_LINKS)) file(item)
                else blocked()
            } catch (e: InterruptedIOException) { throw e }
            catch (_: java.io.IOException) { blocked() }
            catch (_: SecurityException) { blocked() }
        }
        walk(base)
    }
    fun report(): StorageReport {
        var skipped = 0
        fun bytes(directory: File): Long {
            var total = 0L
            visit(directory, { skipped++ }) { total += it.length() }
            return total
        }
        val originalBytes = bytes(originals); val coverBytes = bytes(covers)
        val pageBytes = bytes(pages); val thumbBytes = bytes(thumbnails)
        return StorageReport(originalBytes, coverBytes, pageBytes, thumbBytes, originals.parentFile?.usableSpace ?: 0L, skipped)
    }
    fun clear(selection: CacheSelection): CacheCleanup {
        var freed = 0L; var failures = 0
        val targets = when (selection) { CacheSelection.Pages -> listOf(pages); CacheSelection.Thumbnails -> listOf(thumbnails); CacheSelection.Both -> listOf(pages, thumbnails) }
        for (root in targets) visit(root, { failures++ }) { file ->
            val size = file.length()
            try {
                if (remove(file)) freed += size
                else if (Files.exists(file.toPath(), NOFOLLOW_LINKS)) failures++
            } catch (_: SecurityException) { failures++ }
            catch (_: java.io.IOException) { failures++ }
        }
        return CacheCleanup(freed, failures)
    }
}

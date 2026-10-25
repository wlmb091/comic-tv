package com.pelican.comictv.archive

import java.io.File

class PageCache(private val directory: File, private val quota: Long = 256L * 1024 * 1024,
    private val scanFiles: () -> Array<File>? = {directory.listFiles()}) {
    private var needsTrim = true
    fun materialize(name: String, protectedNames: Set<String>, write: (File) -> Unit, validate: (File) -> Unit): File {
        require(name.matches(Regex("[A-Za-z0-9-]+")))
        directory.mkdirs()
        val target = File(directory, name)
        var success = false
        try {
            if (!target.isFile) { needsTrim = true; write(target) }
            validate(target)
            target.setLastModified(System.currentTimeMillis())
            success = true
            return target
        } finally {
            if (!success) { needsTrim = true; target.delete(); File(directory, "$name.part").delete() }
            trimIfNeeded(if (success) protectedNames + name else protectedNames - name)
        }
    }

    /** Reuse quota knowledge only between mutations through this single-writer cache instance. */
    fun trimIfNeeded(protectedNames: Set<String> = emptySet()) { if (needsTrim) trim(protectedNames) }

    fun trim(protectedNames: Set<String> = emptySet()) {
        val files = scanFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= quota) break
            if (file.name in protectedNames) continue
            val length = file.length()
            if (file.delete()) total -= length
        }
        // If protection prevented reaching quota, a subsequent protection change must try again.
        needsTrim = total > quota
    }
}

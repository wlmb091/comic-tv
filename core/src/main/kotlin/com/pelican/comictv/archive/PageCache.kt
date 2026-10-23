package com.pelican.comictv.archive

import java.io.File

class PageCache(private val directory: File, private val quota: Long = 256L * 1024 * 1024) {
    fun materialize(name: String, protectedNames: Set<String>, write: (File) -> Unit, validate: (File) -> Unit): File {
        require(name.matches(Regex("[A-Za-z0-9-]+")))
        directory.mkdirs()
        val target = File(directory, name)
        var success = false
        try {
            if (!target.isFile) write(target)
            validate(target)
            target.setLastModified(System.currentTimeMillis())
            success = true
            return target
        } finally {
            if (!success) { target.delete(); File(directory, "$name.part").delete() }
            trim(if (success) protectedNames + name else protectedNames - name)
        }
    }

    fun trim(protectedNames: Set<String> = emptySet()) {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= quota) break
            if (file.name in protectedNames) continue
            val length = file.length()
            if (file.delete()) total -= length
        }
    }
}

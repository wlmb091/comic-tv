package com.pelican.comictv.archive

import java.io.File
import java.nio.file.Files
import java.util.Locale

enum class ExplorerFileKind { Import, Install, Other }

/** Local explorer policy; never recursively enumerates or follows links outside its root. */
object ExplorerFiles {
    const val MAX_ENTRIES = 2000
    fun kind(name: String): ExplorerFileKind = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "apk" -> ExplorerFileKind.Install
        in ComicContainers.extensions -> ExplorerFileKind.Import
        else -> ExplorerFileKind.Other
    }
    fun inside(root: File, file: File): Boolean = runCatching {
        val base = root.canonicalFile.toPath()
        file.canonicalFile.toPath().startsWith(base)
    }.getOrDefault(false)

    fun list(root: File, folder: File, limit: Int = MAX_ENTRIES): List<File> {
        require(inside(root, folder)) { "Pasta fora da origem autorizada." }
        require(limit in 1..MAX_ENTRIES)
        check(folder.isDirectory) { "Pasta indisponível. Autorize novamente o acesso." }
        val rows = ArrayList<File>()
        Files.newDirectoryStream(folder.toPath()).use { entries ->
            for (entry in entries) {
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                val file = entry.toFile()
                if (!Files.isSymbolicLink(entry) && inside(root, file)) rows.add(file)
                if (rows.size >= limit) break
            }
        }
        return rows.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
    }
}

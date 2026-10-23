package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import java.io.File
import java.io.InputStream
import java.io.IOException

class LocalComicStore(private val root: File, private val archive: ZipComicArchive = ZipComicArchive()) {
    fun original(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]+"))) { "Invalid comic ID" }
        return File(root, "$id.cbz")
    }
    fun import(id: String, input: InputStream): List<PageRef> {
        root.mkdirs()
        val target = original(id)
        if (target.exists()) throw IOException("Uma edição com este identificador já existe.")
        val staging = File(root, "$id.import")
        try {
            copyLimited(input, staging, ArchiveLimits().maxArchiveBytes)
            val pages = archive.index(staging)
            if (!staging.renameTo(target)) throw IOException("Não foi possível salvar a edição.")
            return pages
        } finally { staging.delete(); File(root, "$id.import.part").delete() }
    }
    fun reconcile(ids: Set<String>) {
        root.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".part") || file.name.endsWith(".import") ||
                        file.extension == "cbz" && file.nameWithoutExtension !in ids)) file.delete()
        }
    }
}

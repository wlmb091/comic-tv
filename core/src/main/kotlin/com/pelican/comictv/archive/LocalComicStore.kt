package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import java.io.File
import java.io.InputStream
import java.io.IOException

class LocalComicStore(private val root: File, private val archive: ComicArchiveReader = ComicArchive()) {
    fun stageRemoval(id: String) {
        require(!id.startsWith("online-")) { "Apenas arquivos importados podem ser excluídos aqui." }
        val source = original(id)
        val staged = File(root, "$id.cbz.deleting")
        require(source.canonicalFile.parentFile == root.canonicalFile && staged.canonicalFile.parentFile == root.canonicalFile)
        if (staged.exists()) throw IOException("Existe uma exclusão pendente. Abra novamente o aplicativo.")
        if (source.exists() && (!source.isFile || !source.renameTo(staged))) throw IOException("Não foi possível preparar a exclusão do arquivo.")
    }
    fun finishRemoval(id: String): Boolean {
        val staged = File(root, original(id).name + ".deleting")
        require(staged.canonicalFile.parentFile == root.canonicalFile)
        return !staged.exists() || staged.delete()
    }
    fun rollbackRemoval(id: String) {
        val target = original(id)
        val staged = File(root, target.name + ".deleting")
        require(target.canonicalFile.parentFile == root.canonicalFile && staged.canonicalFile.parentFile == root.canonicalFile)
        if (staged.exists() && (target.exists() || !staged.renameTo(target))) throw IOException("Não foi possível recuperar o arquivo. Reinicie o aplicativo.")
    }
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
        root.listFiles()?.filter { it.name.endsWith(".cbz.deleting") }?.forEach { file ->
            val id = file.name.removeSuffix(".cbz.deleting")
            if (id in ids) rollbackRemoval(id) else finishRemoval(id)
        }
        root.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".part") || file.name.endsWith(".import") ||
                        file.extension == "cbz" && file.nameWithoutExtension !in ids)) file.delete()
        }
    }
}

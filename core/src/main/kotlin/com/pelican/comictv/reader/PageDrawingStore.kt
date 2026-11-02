package com.pelican.comictv.reader

import java.io.File

/**
 * Saved page drawings: one transparent PNG per page, beside the size of the page it was drawn on.
 * The originals are never touched, and cache cleaning does not reach this folder.
 */
class PageDrawingStore(private val root: File) {
    private val hiddenMarker = File(root, ".hidden")
    private fun folder(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,100}"))) { "Edição inválida." }
        return File(root, id)
    }
    private fun png(id: String, page: Int): File {
        require(page in 0..99_999) { "Página inválida." }
        return File(folder(id), "$page.png")
    }
    private fun sizeFile(id: String, page: Int) = File(folder(id), "$page.size")
    private fun layersFile(id: String, page: Int) = File(folder(id), "$page.layers")

    fun file(id: String, page: Int): File? = png(id, page).takeIf { it.isFile }
    /** The page the drawing was made on, so a page the source replaced is not drawn over. */
    fun pageSize(id: String, page: Int): Pair<Int, Int>? = runCatching {
        val parts = sizeFile(id, page).readText().trim().split('x')
        parts[0].toInt() to parts[1].toInt()
    }.getOrNull()
    fun save(id: String, page: Int, pageWidth: Int, pageHeight: Int, bytes: ByteArray) {
        require(pageWidth > 0 && pageHeight > 0) { "Tamanho de página inválido." }
        val target = png(id, page)
        target.parentFile!!.mkdirs()
        val staged = File(target.parentFile, "$page.tmp")
        try {
            staged.outputStream().use { it.write(bytes); it.fd.sync() }
            sizeFile(id, page).writeText("${pageWidth}x$pageHeight")
            check(staged.renameTo(target) || (target.delete() && staged.renameTo(target))) { "Não foi possível salvar o desenho." }
        } finally { staged.delete() }
    }
    /** The layers as the phone left them, so reopening the page edits them again. Only the phone reads this file. */
    fun layers(id: String, page: Int): File? = layersFile(id, page).takeIf { it.isFile }
    fun saveLayers(id: String, page: Int, input: java.io.InputStream) {
        val target = layersFile(id, page)
        target.parentFile!!.mkdirs()
        val staged = File(target.parentFile, "$page.layers.tmp")
        try {
            staged.outputStream().use { input.copyTo(it); it.fd.sync() }
            check(staged.renameTo(target) || (target.delete() && staged.renameTo(target))) { "Não foi possível salvar as camadas." }
        } finally { staged.delete() }
    }
    fun delete(id: String, page: Int) {
        png(id, page).delete(); sizeFile(id, page).delete(); layersFile(id, page).delete()
        folder(id).takeIf { it.list()?.isEmpty() == true }?.delete()
    }
    fun deleteEdition(id: String) { folder(id).deleteRecursively() }
    var hidden: Boolean
        get() = hiddenMarker.exists()
        set(value) { if (value) { root.mkdirs(); hiddenMarker.createNewFile() } else hiddenMarker.delete() }
}

package com.pelican.comictv.reader

import java.io.File

/** A stretch of a text section turned into a fixed image to draw on. It has a drawing page of its own, from [FIRST_PAGE] up. */
data class TextExcerpt(val section: Int, val start: Int, val end: Int, val page: Int) {
    companion object { const val FIRST_PAGE = 50_000 }
}

/** One finished action of a drawing, to show it being made again later. */
class ReplayStep(val x: Int, val y: Int, val width: Int, val height: Int, val layerWidth: Int, val layerHeight: Int, val png: ByteArray)

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
    private fun baseFile(id: String, page: Int) = File(folder(id), "$page.base")
    private fun excerptsFile(id: String) = File(folder(id), "text-excerpts")
    private fun pendingReplay(id: String, page: Int) = File(folder(id), "$page.rec")
    private fun replayFile(id: String, page: Int) = File(folder(id), "$page.replay")

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
    /** Waits with the session until the phone saves or discards it, so a discarded stroke never comes back in the replay. */
    fun recordStep(id: String, page: Int, step: ReplayStep) {
        val pending = pendingReplay(id, page)
        if (pending.length() + replayFile(id, page).length() + step.png.size > MAX_REPLAY_BYTES) return
        pending.parentFile!!.mkdirs()
        java.io.DataOutputStream(java.io.FileOutputStream(pending, true).buffered()).use { out ->
            for (value in intArrayOf(step.x, step.y, step.width, step.height, step.layerWidth, step.layerHeight, step.png.size)) out.writeInt(value)
            out.write(step.png)
        }
    }
    fun commitReplay(id: String, page: Int) {
        val pending = pendingReplay(id, page).takeIf { it.isFile } ?: return
        java.io.FileOutputStream(replayFile(id, page), true).use { out -> pending.inputStream().use { it.copyTo(out) } }
        pending.delete()
    }
    fun discardReplay(id: String, page: Int) { pendingReplay(id, page).delete() }
    fun hasReplay(id: String, page: Int): Boolean = replayFile(id, page).length() > 0
    /** Visits the steps in order, one in memory at a time. The visitor returns false to stop. */
    inline fun readReplay(id: String, page: Int, visit: (ReplayStep) -> Boolean) {
        val input = java.io.DataInputStream(replayStream(id, page)?.buffered() ?: return)
        input.use {
            while (true) {
                val numbers = IntArray(7)
                try { for (i in numbers.indices) numbers[i] = input.readInt() } catch (_: java.io.EOFException) { return }
                if (numbers[6] !in 1..MAX_REPLAY_BYTES) return
                val png = ByteArray(numbers[6])
                try { input.readFully(png) } catch (_: java.io.EOFException) { return }
                if (!visit(ReplayStep(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5], png))) return
            }
        }
    }
    fun replayCount(id: String, page: Int): Int { var count = 0; val input = java.io.DataInputStream(replayStream(id, page)?.buffered() ?: return 0)
        input.use { while (true) { try { input.skipBytes(24); val size = input.readInt(); if (size <= 0) return count; var left = size; while (left > 0) { val n = input.skipBytes(left); if (n <= 0) return count; left -= n }; count++ } catch (_: java.io.EOFException) { return count } } }
    }
    @PublishedApi internal fun replayStream(id: String, page: Int): java.io.InputStream? = replayFile(id, page).takeIf { it.isFile }?.inputStream()
    private fun readExcerpts(id: String): List<TextExcerpt> = runCatching {
        excerptsFile(id).readLines().mapNotNull { line ->
            val parts = line.split(',').map { it.toIntOrNull() ?: return@mapNotNull null }
            if (parts.size == 4) TextExcerpt(parts[0], parts[1], parts[2], parts[3]) else null
        }
    }.getOrDefault(emptyList())
    private fun writeExcerpts(id: String, rows: List<TextExcerpt>) {
        val target = excerptsFile(id)
        if (rows.isEmpty()) { target.delete(); return }
        target.parentFile!!.mkdirs()
        val staged = File(target.parentFile, "text-excerpts.tmp")
        staged.writeText(rows.joinToString("\n") { "${it.section},${it.start},${it.end},${it.page}" })
        check(staged.renameTo(target) || (target.delete() && staged.renameTo(target))) { "Não foi possível salvar o trecho." }
    }
    /** The section's fixed stretches in reading order. */
    fun excerpts(id: String, section: Int): List<TextExcerpt> = readExcerpts(id).filter { it.section == section }.sortedBy { it.start }
    fun excerptBase(id: String, page: Int): File? = baseFile(id, page).takeIf { it.isFile }
    fun addExcerpt(id: String, section: Int, start: Int, end: Int, base: ByteArray): TextExcerpt {
        require(section in 0..49_999 && start >= 0 && end > start) { "Trecho inválido." }
        val rows = readExcerpts(id)
        require(rows.none { it.section == section && it.start < end && start < it.end }) { "Este trecho já tem um desenho." }
        val page = (rows.maxOfOrNull { it.page } ?: (TextExcerpt.FIRST_PAGE - 1)) + 1
        require(page <= 99_999) { "Limite de trechos desta edição." }
        val row = TextExcerpt(section, start, end, page)
        baseFile(id, page).also { it.parentFile!!.mkdirs() }.writeBytes(base)
        writeExcerpts(id, rows + row)
        return row
    }
    fun delete(id: String, page: Int) {
        png(id, page).delete(); sizeFile(id, page).delete(); layersFile(id, page).delete(); pendingReplay(id, page).delete(); replayFile(id, page).delete()
        if (page >= TextExcerpt.FIRST_PAGE) { baseFile(id, page).delete(); writeExcerpts(id, readExcerpts(id).filterNot { it.page == page }) }
        folder(id).takeIf { it.list()?.isEmpty() == true }?.delete()
    }
    fun deleteEdition(id: String) { folder(id).deleteRecursively() }
    /** Every saved file, by the name it has inside a backup. */
    fun backupFiles(): List<Pair<String, File>> = root.listFiles().orEmpty().filter { it.isDirectory && it.name.matches(EDITION) }.sortedBy { it.name }.flatMap { edition ->
        edition.listFiles().orEmpty().filter { it.isFile && it.name.matches(SAVED_FILE) }.sortedBy { it.name }.map { "${edition.name}/${it.name}" to it }
    }
    /**
     * Brings one file back from a backup. What this TV already has wins: a page drawn here is never replaced by an older copy,
     * and an edition with stretches of its own keeps its numbering. Call [finishRestore] after the last file.
     */
    fun restoreFile(name: String, input: java.io.InputStream, maximum: Long) {
        val edition = name.substringBefore('/'); val file = name.substringAfter('/')
        require(edition.matches(EDITION) && file.matches(SAVED_FILE) && name.count { it == '/' } == 1) { "Arquivo de desenho inválido." }
        val page = file.substringBefore('.').toIntOrNull()
        val keepsOwn = restoringOwnExcerpts.getOrPut(edition) { excerptsFile(edition).isFile }
        if (page == null || page >= TextExcerpt.FIRST_PAGE) { if (keepsOwn) return }
        else if (restoringOwnPages.getOrPut(edition) { hashMapOf() }.getOrPut(page) { png(edition, page).isFile }) return
        val folder = folder(edition)
        folder.mkdirs()
        val staged = File(folder, "restore.tmp")
        try {
            staged.outputStream().use { out ->
                val buffer = ByteArray(16384); var total = 0L
                while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= maximum) { "Arquivo de desenho grande demais." }; out.write(buffer, 0, count) }
            }
            val target = File(folder, file)
            check(staged.renameTo(target) || (target.delete() && staged.renameTo(target))) { "Não foi possível restaurar o desenho." }
        } finally { staged.delete() }
    }
    fun finishRestore() { restoringOwnPages.clear(); restoringOwnExcerpts.clear() }
    // Decided once per page and edition, whichever of its files comes first in the backup.
    private val restoringOwnPages = hashMapOf<String, HashMap<Int, Boolean>>()
    private val restoringOwnExcerpts = hashMapOf<String, Boolean>()
    var hidden: Boolean
        get() = hiddenMarker.exists()
        set(value) { if (value) { root.mkdirs(); hiddenMarker.createNewFile() } else hiddenMarker.delete() }
    companion object {
        const val MAX_REPLAY_BYTES = 32 * 1024 * 1024
        private val EDITION = Regex("[A-Za-z0-9-]{1,100}")
        private val SAVED_FILE = Regex("[0-9]{1,5}\\.(png|size|layers|base)|text-excerpts")
    }
}

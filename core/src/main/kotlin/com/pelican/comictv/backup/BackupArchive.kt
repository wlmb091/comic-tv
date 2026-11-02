package com.pelican.comictv.backup

import com.pelican.comictv.reader.PageDrawingStore
import java.io.File
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * A backup with drawings: a ZIP with the same JSON document plus the saved files of each page.
 * Without drawings the backup stays plain JSON, and JSON from older versions keeps opening.
 */
object BackupArchive {
    const val MAX_BYTES = 1024L * 1024 * 1024
    private const val DOCUMENT = "backup.json"
    private const val DRAWINGS = "drawings/"
    private const val MAX_FILES = 200_000
    private const val MAX_FILE_BYTES = 128L * 1024 * 1024

    fun isArchive(file: File): Boolean = file.inputStream().use { input ->
        val magic = ByteArray(4)
        input.read(magic) == 4 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte() && magic[2].toInt() == 3 && magic[3].toInt() == 4
    }
    fun write(output: OutputStream, document: ByteArray, drawings: List<Pair<String, File>>) {
        var total = document.size.toLong()
        ZipOutputStream(output).use { zip ->
            // PNG is already compressed: the time goes to the JSON only.
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(DOCUMENT)); zip.write(document); zip.closeEntry()
            for ((name, file) in drawings) {
                total += file.length()
                require(total <= MAX_BYTES) { "Os desenhos excedem 1 GiB." }
                zip.putNextEntry(ZipEntry(DRAWINGS + name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
    fun document(file: File): BackupDocument {
        require(file.length() <= MAX_BYTES) { "Backup excede 1 GiB." }
        if (!isArchive(file)) return file.inputStream().use(BackupCodec::decode)
        return ZipFile(file).use { zip ->
            val entry = requireNotNull(zip.getEntry(DOCUMENT)) { "Backup sem documento." }
            zip.getInputStream(entry).use(BackupCodec::decode)
        }
    }
    /** How many drawn pages the backup carries, for the preview. */
    fun drawingCount(file: File): Int = if (!isArchive(file)) 0 else ZipFile(file).use { zip ->
        zip.entries().asSequence().count { it.name.startsWith(DRAWINGS) && it.name.endsWith(".png") }
    }
    /** An unknown or oversized entry stops the restore of the drawings; the library document was already applied. */
    fun restoreDrawings(file: File, store: PageDrawingStore) {
        if (!isArchive(file)) return
        try {
            ZipFile(file).use { zip ->
                var count = 0
                for (entry in zip.entries()) {
                    if (entry.name == DOCUMENT) continue
                    require(entry.name.startsWith(DRAWINGS) && !entry.isDirectory && ++count <= MAX_FILES) { "Arquivo inesperado no backup." }
                    zip.getInputStream(entry).use { store.restoreFile(entry.name.removePrefix(DRAWINGS), it, MAX_FILE_BYTES) }
                }
            }
        } finally { store.finishRestore() }
    }
}

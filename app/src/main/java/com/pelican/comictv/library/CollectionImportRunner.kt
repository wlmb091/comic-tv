package com.pelican.comictv.library

import com.pelican.comictv.archive.CollectionArchive
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

data class ImportedEdition(val id: String, val duplicate: Boolean)
data class ImportBatchResult(val collection: Boolean, val imported: Int, val duplicates: Int,
    val failures: List<String>, val singleId: String? = null) {
    fun summary(): String = "$imported importada(s) · $duplicates repetida(s) · ${failures.size} com erro" +
        if (failures.isEmpty()) "." else ".\n" + failures.joinToString("\n")
}

/** Disk-backed, one extracted edition at a time; the callback publishes each edition atomically. */
class CollectionImportRunner(private val scratchRoot: File,
    private val checkSpace: (Long) -> Unit,
    private val importOne: suspend (File, String) -> ImportedEdition) {
    suspend fun run(file: File, name: String, progress: (String) -> Unit): ImportBatchResult = withContext(Dispatchers.IO) {
        val reader = CollectionArchive()
        val entries = runInterruptible { reader.inspect(file) }
        if (entries == null) {
            checkSpace(file.length() + RESERVE)
            val result = importOne(file, name)
            return@withContext ImportBatchResult(false, if (result.duplicate) 0 else 1,
                if (result.duplicate) 1 else 0, emptyList(), result.id)
        }
        val directory = File(scratchRoot, UUID.randomUUID().toString())
        check(directory.mkdirs()) { "Não foi possível preparar a importação da coleção." }
        var imported = 0
        var duplicates = 0
        val failures = mutableListOf<String>()
        val target = File(directory, "edition")
        try {
            for ((index, entry) in entries.withIndex()) {
                currentCoroutineContext().ensureActive()
                val title = entry.name.replace('\\', '/').substringAfterLast('/').take(200)
                progress("Importando ${index + 1}/${entries.size} · $title")
                try {
                    checkSpace(entry.size * 2 + RESERVE)
                    runInterruptible { reader.extract(file, entry, target) }
                    val result = importOne(target, title)
                    if (result.duplicate) duplicates++ else imported++
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    failures += "$title: ${(e.message ?: "arquivo inválido").take(180)}"
                } finally { target.delete(); File(directory, "edition.part").delete() }
            }
            ImportBatchResult(true, imported, duplicates, failures)
        } finally { target.delete(); File(directory, "edition.part").delete(); directory.delete() }
    }
    companion object { const val RESERVE = 64L * 1024 * 1024 }
}

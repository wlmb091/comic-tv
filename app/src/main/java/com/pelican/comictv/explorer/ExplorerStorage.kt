package com.pelican.comictv.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.pelican.comictv.archive.ExplorerFiles
import com.pelican.comictv.browser.BrowserDownloadItem
import com.pelican.comictv.browser.BrowserDownloadStatus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal data class ExplorerEntry(val key: String, val name: String, val directory: Boolean,
    val size: Long = -1, val uri: Uri? = null, val file: File? = null, val comicId: String? = null)
internal data class ExplorerFolder(val key: String, val label: String, val file: File? = null,
    val tree: Uri? = null, val document: String? = null, val root: File? = file)

internal class ExplorerStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("explorer-folders", Context.MODE_PRIVATE)
    fun roots(): List<ExplorerFolder> = listOf(
        ExplorerFolder("downloads", "Downloads do Comic TV", File(context.filesDir, "browser-downloads")),
        ExplorerFolder("originals", "Arquivos da biblioteca", File(context.filesDir, "originals")),
        ExplorerFolder("processed", "Arquivos processados", File(context.filesDir, "archive-tools")),
    ) + prefs.getStringSet("trees", emptySet()).orEmpty().take(20).mapNotNull { value ->
        runCatching { val uri = Uri.parse(value); val id = DocumentsContract.getTreeDocumentId(uri)
            ExplorerFolder(value, id.substringAfterLast(':').ifBlank { "Armazenamento autorizado" }, tree = uri, document = id)
        }.getOrNull()
    }
    fun authorize(uri: Uri) {
        check(roots().count { it.tree != null } < 20 || uri.toString() in prefs.getStringSet("trees", emptySet()).orEmpty()) { "Limite de 20 pastas autorizadas." }
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putStringSet("trees", prefs.getStringSet("trees", emptySet()).orEmpty() + uri.toString()).apply()
    }
    suspend fun list(folder: ExplorerFolder): List<ExplorerEntry> = runInterruptible(Dispatchers.IO) {
        if (folder.key == "downloads") return@runInterruptible downloads()
        folder.file?.let { dir ->
            if (!dir.exists() && dir == folder.root) dir.mkdirs()
            return@runInterruptible ExplorerFiles.list(requireNotNull(folder.root), dir).map { file ->
                ExplorerEntry(file.path, file.name, file.isDirectory, file.length(), file = file,
                    comicId = if (folder.key == "originals" && file.extension == "cbz") file.nameWithoutExtension else null)
            }.filter { it.directory || !it.name.endsWith(".part") && !it.name.endsWith(".import") && !it.name.endsWith(".deleting") }
        }
        val tree = requireNotNull(folder.tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, requireNotNull(folder.document))
        val result = ArrayList<ExplorerEntry>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && result.size < ExplorerFiles.MAX_ENTRIES) {
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                val id = cursor.getString(0) ?: continue
                result += ExplorerEntry(id, cursor.getString(1) ?: "Sem nome", cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    if (cursor.isNull(3)) -1 else cursor.getLong(3), DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
        } ?: error("Não foi possível listar esta pasta. Autorize novamente o acesso.")
        result.sortedWith(compareBy<ExplorerEntry> { !it.directory }.thenBy { it.name.lowercase(java.util.Locale.ROOT) })
    }
    private fun downloads(): List<ExplorerEntry> {
        val directory = File(context.filesDir, "browser-downloads")
        val records = File(directory, "records.json")
        if (!records.exists()) return emptyList()
        check(records.length() <= 2L * 1024 * 1024) { "Lista de downloads excede o limite." }
        val rows = records.reader().use { Gson().fromJson(it, Array<BrowserDownloadItem>::class.java) }.orEmpty()
        return rows.take(100).filter { it.status == BrowserDownloadStatus.Complete || it.status == BrowserDownloadStatus.Imported }.mapNotNull { row ->
            if (!row.id.matches(Regex("[A-Za-z0-9-]+"))) return@mapNotNull null
            val file = File(directory, "${row.id}.download")
            if (!file.isFile || !ExplorerFiles.inside(directory, file)) null else
                ExplorerEntry(row.id, row.filename, false, file.length(), file = file, comicId = row.importedId)
        }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }
    fun uri(entry: ExplorerEntry): Uri = entry.uri ?: FileProvider.getUriForFile(context,
        "${context.packageName}.explorer", requireNotNull(entry.file), entry.name)
    fun child(folder: ExplorerFolder, entry: ExplorerEntry) = if (entry.file != null)
        folder.copy(key = entry.key, label = entry.name, file = entry.file)
    else folder.copy(key = entry.key, label = entry.name, document = entry.key)
}

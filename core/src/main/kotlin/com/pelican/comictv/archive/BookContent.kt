package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipFile

/** Passive book decoding only: no WebView, network, XML entity expansion or embedded code. */
internal object BookContent {
    const val MAX_TEXT = 16 * 1024 * 1024
    fun fail(message: String): Nothing = throw ComicArchiveException(message)
    fun interrupted() { if (Thread.currentThread().isInterrupted) fail("Leitura cancelada.") }
    fun sections(text: String): List<String> {
        if (text.length > MAX_TEXT) fail("O texto do livro excede o limite permitido.")
        val normalized = text.replace(Regex("[\\t \\x0B\\f\\r]+"), " ").replace(Regex(" *\\n *"), "\n").trim()
        val result = ArrayList<String>()
        var start = 0
        while (start < normalized.length) {
            var end = minOf(start + 12_000, normalized.length)
            if (end < normalized.length && Character.isHighSurrogate(normalized[end - 1])) end--
            result.add(normalized.substring(start, end)); start = end
        }
        return result
    }
    fun xml(bytes: ByteArray): Document = Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser().setMaxDepth(128))
    fun read(zip: ZipFile, path: String, limit: Long): ByteArray {
        val entry = zip.getEntry(path) ?: fail("Um arquivo necessário do livro não foi encontrado.")
        if (entry.size > limit) fail("Uma seção do livro excede o limite permitido.")
        return zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                interrupted()
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > limit) fail("Uma seção do livro excede o limite permitido.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
    fun validate(zip: ZipFile, limits: ArchiveLimits) {
        val seen = HashSet<String>(); var total = 0L; var count = 0
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            interrupted()
            val entry = entries.nextElement()
            if (++count > limits.maxEntries) fail("O livro contém entradas demais.")
            val name = entry.name
            if (name.startsWith('/') || '\\' in name || ':' in name || '\u0000' in name || name.split('/').any { it == ".." }) fail("O livro contém um caminho inválido.")
            if (!seen.add(name)) fail("O livro contém entradas duplicadas.")
            if (entry.size < 0 || entry.size > limits.maxPageBytes || entry.size > limits.maxTotalBytes - total) fail("O conteúdo do livro excede os limites de tamanho.")
            total += entry.size
        }
    }
    fun resolve(base: String, href: String): String {
        val uri = URI(href.replace(" ", "%20"))
        if (uri.isAbsolute || uri.rawAuthority != null || uri.path.isNullOrBlank() || uri.path.startsWith('/') || '\\' in uri.path || '\u0000' in uri.path) fail("O livro referencia conteúdo externo ou um caminho inválido.")
        val segments = base.substringBeforeLast('/', "").split('/').filter { it.isNotBlank() }.toMutableList()
        for (segment in uri.path.split('/')) when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isEmpty()) fail("O livro referencia conteúdo fora do pacote.") else segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
        return segments.joinToString("/")
    }
    fun refs(parts: List<BookPart>) = parts.mapIndexed { i, part -> PageRef(i, "epub:${if (part.text != null) "text" else "image"}:$i") }
    fun part(parts: List<BookPart>, page: PageRef): BookPart {
        val part = parts.getOrNull(page.index) ?: fail("Seção do livro não encontrada.")
        if (page.key != "epub:${if (part.text != null) "text" else "image"}:${page.index}") fail("Referência de seção do livro inválida.")
        return part
    }
}

internal data class BookPart(val text: String? = null, val image: String? = null)

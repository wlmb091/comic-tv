package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.zip.ZipFile

/** Offline EPUB subset: package spine, raster images and reflowable plain text. No active content. */
class EpubComicArchive(private val limits: ArchiveLimits = ArchiveLimits()) : ComicArchiveReader {
    private data class Part(val image: String? = null, val text: String? = null)
    private fun book(zip: ZipFile): List<Part> {
        val seen = HashSet<String>()
        var total = 0L
        var count = 0
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            interrupted()
            val entry = entries.nextElement()
            if (++count > limits.maxEntries) fail("O EPUB contém entradas demais.")
            val name = entry.name
            if (name.startsWith('/') || '\\' in name || ':' in name || '\u0000' in name || name.split('/').any { it == ".." }) fail("O EPUB contém um caminho inválido.")
            if (!seen.add(name)) fail("O EPUB contém entradas duplicadas.")
            if (entry.size < 0 || entry.size > limits.maxPageBytes || entry.size > limits.maxTotalBytes - total) fail("O conteúdo do EPUB excede os limites de tamanho.")
            total += entry.size
        }
        fun markup(path: String): org.jsoup.nodes.Document {
            val bytes = read(zip, path, 2L * 1024 * 1024)
            // Jsoup's XML parser does not fetch DTDs or expand external entities.
            return Jsoup.parse(bytes.inputStream(), null, "", Parser.xmlParser().setMaxDepth(128))
        }
        val container = markup("META-INF/container.xml")
        val packagePath = container.selectFirst("rootfile")?.attr("full-path")?.takeIf { it.isNotBlank() }
            ?: fail("O EPUB não informa seu arquivo de conteúdo.")
        val opf = resolve("", packagePath)
        val packageDoc = markup(opf)
        val manifest = packageDoc.select("manifest > item").associateBy { it.attr("id") }
        // Font obfuscation is not DRM. Native reflow uses the reader's fonts, so these
        // unused font resources need no decoding. Encrypted content remains refused.
        if (zip.getEntry("META-INF/encryption.xml") != null) {
            val encrypted = markup("META-INF/encryption.xml").getAllElements().filter { it.normalName().substringAfter(':').equals("EncryptedData", true) }
            if (encrypted.isEmpty()) fail("O EPUB contém informações de criptografia inválidas.")
            val fonts = manifest.values.filter {
                it.attr("media-type") in setOf("application/vnd.ms-opentype", "application/font-sfnt", "application/font-woff", "application/vnd.ms-fontobject", "font/ttf", "font/otf", "font/woff", "font/woff2")
            }.map { resolve(opf, it.attr("href")) }.toSet()
            for (resource in encrypted) {
                val methods = resource.getAllElements().filter { it.normalName().substringAfter(':').equals("EncryptionMethod", true) }
                val refs = resource.getAllElements().filter { it.normalName().substringAfter(':').equals("CipherReference", true) }
                if (methods.size != 1 || refs.size != 1 || methods.single().attr("Algorithm") !in setOf("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC") || resolve("", refs.single().attr("URI")) !in fonts) {
                    fail("Este EPUB usa DRM ou criptografia não suportada. Abra-o no aplicativo autorizado pelo fornecedor.")
                }
            }
        }
        val spine = packageDoc.select("spine > itemref").filter { it.attr("linear") != "no" }
        if (spine.isEmpty()) fail("O EPUB não contém uma sequência de leitura.")
        val parts = ArrayList<Part>()
        var textTotal = 0
        fun add(part: Part) {
            if (parts.size >= limits.maxEntries) fail("O EPUB contém seções demais.")
            textTotal += part.text?.length ?: 0
            if (textTotal > 16 * 1024 * 1024) fail("O texto do EPUB excede o limite permitido.")
            parts.add(part)
        }
        fun image(path: String) {
            if (path.substringAfterLast('.', "").lowercase() !in setOf("jpg", "jpeg", "png", "webp", "gif")) fail("O EPUB usa uma imagem não suportada. Use JPEG, PNG, GIF ou WebP.")
            if (zip.getEntry(path) == null) fail("Uma imagem do EPUB não foi encontrada.")
            add(Part(image = path))
        }
        for (itemref in spine) {
            interrupted()
            val item = manifest[itemref.attr("idref")] ?: fail("O EPUB contém uma referência de leitura inválida.")
            val path = resolve(opf, item.attr("href"))
            val type = item.attr("media-type")
            if (type.startsWith("image/") && type != "image/svg+xml") { image(path); continue }
            if (type !in setOf("application/xhtml+xml", "text/html", "image/svg+xml")) fail("Este EPUB usa um tipo de conteúdo não suportado.")
            val doc = markup(path)
            val body = doc.selectFirst("body") ?: doc.selectFirst("svg") ?: fail("Uma seção do EPUB está vazia ou inválida.")
            val text = StringBuilder()
            fun flush() {
                val content = text.toString().replace(Regex("[\\t \\x0B\\f\\r]+"), " ").replace(Regex(" *\\n *"), "\n").trim()
                text.setLength(0)
                // Bounded native text sections; no huge Compose layout for a whole novel.
                var start = 0
                while (start < content.length) {
                    var end = minOf(start + 12_000, content.length)
                    if (end < content.length && Character.isHighSurrogate(content[end - 1])) end--
                    add(Part(text = content.substring(start, end)))
                    start = end
                }
            }
            fun visit(node: Node) {
                interrupted()
                if (node is TextNode) { text.append(node.wholeText); return }
                if (node !is Element) return
                val tag = node.normalName().substringAfter(':')
                if (tag in setOf("script", "style", "head", "iframe", "object", "audio", "video", "form") || node.hasAttr("hidden")) return
                if (tag == "img" || tag == "image") {
                    flush()
                    val href = if (tag == "img") node.attr("src") else node.attr("href").ifBlank { node.attr("xlink:href") }
                    image(resolve(path, href))
                    return
                }
                val block = tag in setOf("p", "div", "section", "h1", "h2", "h3", "h4", "li", "blockquote", "br", "tr")
                if (block) text.append('\n')
                node.childNodes().forEach(::visit)
                if (block) text.append('\n')
                if (tag == "td" || tag == "th") text.append(" | ")
            }
            visit(body)
            flush()
        }
        if (parts.isEmpty()) fail("Nenhum texto ou imagem compatível foi encontrado no EPUB.")
        return parts
    }

    override fun index(file: File): List<PageRef> = guarded {
        openComicZip(file, limits.maxEntries).use { zip -> book(zip).mapIndexed { i, part -> PageRef(i, "epub:${if (part.text != null) "text" else "image"}:$i") } }
    }
    override fun extract(file: File, page: PageRef, destination: File): File = guarded {
        openComicZip(file, limits.maxEntries).use { zip ->
            val parts = book(zip)
            val part = parts.getOrNull(page.index) ?: fail("Seção não encontrada no EPUB.")
            val expected = "epub:${if (part.text != null) "text" else "image"}:${page.index}"
            if (page.key != expected) fail("Referência de seção EPUB inválida.")
            if (part.text != null) part.text.toByteArray(Charsets.UTF_8).inputStream().use { copyLimited(it, destination, limits.maxPageBytes) }
            else zip.getInputStream(zip.getEntry(part.image!!)).use { copyLimited(it, destination, limits.maxPageBytes) }
        }
    }
    private fun read(zip: ZipFile, path: String, limit: Long): ByteArray {
        val entry = zip.getEntry(path) ?: fail("Um arquivo necessário do EPUB não foi encontrado.")
        if (entry.size > limit) fail("Uma seção de texto do EPUB excede o limite permitido.")
        return zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                interrupted()
                val n = input.read(buffer)
                if (n < 0) break
                if (output.size().toLong() + n > limit) fail("Uma seção de texto do EPUB excede o limite permitido.")
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
    }
    private fun resolve(base: String, href: String): String {
        val uri = URI(href.replace(" ", "%20"))
        if (uri.isAbsolute || uri.rawAuthority != null || uri.path.isNullOrBlank() || uri.path.startsWith('/') || '\\' in uri.path || '\u0000' in uri.path) fail("O EPUB referencia conteúdo externo ou um caminho inválido.")
        val segments = base.substringBeforeLast('/', "").split('/').filter { it.isNotBlank() }.toMutableList()
        for (segment in uri.path.split('/')) when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isEmpty()) fail("O EPUB referencia conteúdo fora do pacote.") else segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
        return segments.joinToString("/")
    }
    private fun interrupted() { if (Thread.currentThread().isInterrupted) fail("Leitura cancelada.") }
    private fun fail(message: String): Nothing = throw ComicArchiveException(message)
    private inline fun <T> guarded(block: () -> T): T = try { block() }
        catch (e: ComicArchiveException) { throw e }
        catch (e: Exception) { throw ComicArchiveException("Não foi possível ler o EPUB. Ele pode estar corrompido ou protegido.", e) }
}

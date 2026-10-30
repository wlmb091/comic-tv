package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import java.io.File
import java.util.zip.ZipFile

/** Reflowable WordprocessingML text, table cells and inline raster images. Not a Word layout engine. */
class DocxComicArchive(private val limits: ArchiveLimits = ArchiveLimits()) : ComicArchiveReader {
    private fun book(zip: ZipFile): List<BookPart> {
        BookContent.validate(zip, limits)
        val document = "word/document.xml"
        val doc = BookContent.xml(BookContent.read(zip, document, minOf(limits.maxPageBytes, 8L * 1024 * 1024)))
        val body = doc.getAllElements().firstOrNull { it.normalName().substringAfter(':') == "body" }
            ?: BookContent.fail("O DOCX não contém o corpo do documento.")
        val relPath = "word/_rels/document.xml.rels"
        val relationships = if (zip.getEntry(relPath) == null) emptyMap() else
            BookContent.xml(BookContent.read(zip, relPath, 2L * 1024 * 1024)).getAllElements()
                .filter { it.normalName().substringAfter(':').equals("Relationship", true) }.associateBy { it.attr("Id") }
        val parts = ArrayList<BookPart>(); val text = StringBuilder(); var totalText = 0
        fun flush() {
            val value = text.toString(); text.setLength(0)
            totalText += value.length
            if (totalText > BookContent.MAX_TEXT) BookContent.fail("O texto do DOCX excede o limite permitido.")
            BookContent.sections(value).forEach { parts.add(BookPart(text = it)) }
        }
        fun visit(node: Node) {
            BookContent.interrupted()
            if (node !is Element) return
            val tag = node.normalName().substringAfter(':')
            if (tag in setOf("del", "instrText", "object", "oleObject", "altChunk")) return
            when (tag) {
                "t" -> text.append(node.wholeText())
                "tab" -> text.append(" | ")
                "br", "cr" -> text.append('\n')
                "blip" -> {
                    val id = node.attr("r:embed")
                    if (id.isNotBlank()) {
                        val rel = relationships[id] ?: BookContent.fail("Uma imagem do DOCX não foi encontrada.")
                        if (rel.attr("TargetMode").equals("External", true)) BookContent.fail("O DOCX referencia uma imagem externa.")
                        val path = BookContent.resolve(document, rel.attr("Target"))
                        if (path.substringAfterLast('.').lowercase() !in setOf("png", "jpg", "jpeg", "webp")) {
                            text.append(" [Imagem não compatível] ")
                        } else {
                            if (zip.getEntry(path) == null) BookContent.fail("Uma imagem do DOCX não foi encontrada.")
                            flush(); parts.add(BookPart(image = path))
                        }
                    }
                }
                else -> {
                    if (tag == "p" || tag == "tr") text.append('\n')
                    node.childNodes().forEach(::visit)
                    if (tag == "p" || tag == "tr") text.append('\n')
                    if (tag == "tc") text.append(" | ")
                }
            }
            if (text.length + totalText > BookContent.MAX_TEXT || parts.size > limits.maxEntries) BookContent.fail("O DOCX excede os limites de leitura.")
        }
        visit(body); flush()
        if (parts.isEmpty() || parts.size > limits.maxEntries) BookContent.fail("Nenhum texto ou imagem compatível foi encontrado no DOCX.")
        return parts
    }
    override fun index(file: File): List<PageRef> = guarded { openComicZip(file, limits.maxEntries).use { BookContent.refs(book(it)) } }
    override fun extract(file: File, page: PageRef, destination: File): File = guarded {
        openComicZip(file, limits.maxEntries).use { zip ->
            val part = BookContent.part(book(zip), page)
            if (part.text != null) part.text.toByteArray(Charsets.UTF_8).inputStream().use { copyLimited(it, destination, limits.maxPageBytes) }
            else zip.getInputStream(zip.getEntry(part.image!!)).use { copyLimited(it, destination, limits.maxPageBytes) }
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: ComicArchiveException) { throw e }
      catch (e: Exception) { throw ComicArchiveException("Não foi possível ler o DOCX. Ele pode estar corrompido ou protegido.", e) }
}

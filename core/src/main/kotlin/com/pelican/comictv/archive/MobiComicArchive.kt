package com.pelican.comictv.archive

import com.pelican.comictv.model.PageRef
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/** Independent, bounded legacy MOBI/AZW decoder. DRM, KF8 and HUFF/CDIC are explicitly refused. */
class MobiComicArchive(private val limits: ArchiveLimits = ArchiveLimits()) : ComicArchiveReader {
    private fun book(file: File): List<BookPart> = RandomAccessFile(file, "r").use { input ->
        if (file.length() > limits.maxArchiveBytes || file.length() < 86) BookContent.fail("O livro MOBI está incompleto ou excede o limite permitido.")
        input.seek(60)
        val identity = ByteArray(8).also(input::readFully).toString(Charsets.US_ASCII)
        if (identity != "BOOKMOBI") BookContent.fail("O arquivo não é um livro MOBI/AZW compatível.")
        input.seek(76); val count = input.readUnsignedShort()
        if (count < 2 || count > limits.maxEntries || 78L + count * 8L > file.length()) BookContent.fail("A lista de registros do MOBI é inválida.")
        val offsets = LongArray(count + 1)
        for (i in 0 until count) { offsets[i] = input.readInt().toLong() and 0xffffffffL; input.skipBytes(4) }
        offsets[count] = file.length()
        if (offsets[0] < 78L + count * 8L || (0 until count).any { offsets[it] >= offsets[it + 1] }) BookContent.fail("O MOBI contém registros inválidos.")
        fun record(index: Int, limit: Long): ByteArray {
            BookContent.interrupted()
            if (index !in 0 until count || offsets[index + 1] - offsets[index] > limit) BookContent.fail("Um registro MOBI excede o limite permitido.")
            input.seek(offsets[index]); return ByteArray((offsets[index + 1] - offsets[index]).toInt()).also(input::readFully)
        }
        val header = record(0, 2L * 1024 * 1024)
        fun u16(at: Int): Int {
            if (at < 0 || at + 2 > header.size) BookContent.fail("Cabeçalho MOBI incompleto.")
            return ((header[at].toInt() and 255) shl 8) or (header[at + 1].toInt() and 255)
        }
        fun u32(at: Int): Long = (u16(at).toLong() shl 16) or u16(at + 2).toLong()
        if (header.size < 40 || header.copyOfRange(16, 20).toString(Charsets.US_ASCII) != "MOBI") BookContent.fail("Cabeçalho MOBI não reconhecido.")
        if (u16(12) != 0) BookContent.fail("Este MOBI/AZW usa DRM. Abra-o no aplicativo autorizado pelo fornecedor.")
        val compression = u16(0)
        if (compression !in 1..2) BookContent.fail("Este MOBI usa compressão HUFF/CDIC ou outra compressão ainda não suportada. Exporte para EPUB sem DRM.")
        val headerLength = u32(20)
        if (headerLength < 24 || headerLength + 16 > header.size) BookContent.fail("Cabeçalho MOBI inválido.")
        val version = if (headerLength >= 92) u32(104) else u32(36)
        if (version >= 8) BookContent.fail("Este livro usa KF8/AZW3, ainda não suportado. Exporte para EPUB sem DRM.")
        val charset = when (u32(28)) { 1252L -> Charset.forName("windows-1252"); 65001L -> Charsets.UTF_8; else -> BookContent.fail("A codificação deste MOBI não é suportada.") }
        val textLength = u32(4)
        val textCount = u16(8)
        val recordSize = u16(10)
        if (textLength == 0L || textLength > BookContent.MAX_TEXT || textLength > limits.maxTotalBytes || textCount == 0 || textCount >= count || recordSize == 0) BookContent.fail("O texto do MOBI está vazio ou excede os limites permitidos.")
        val imageStart = if (headerLength >= 96) u32(108) else 0xffffffffL
        val flags = if (headerLength >= 228) u16(242) else 0
        val output = ByteArrayOutputStream()
        for (i in 1..textCount) {
            val raw = record(i, minOf(limits.maxPageBytes, 1024L * 1024))
            val text = raw.copyOfRange(0, textEnd(raw, flags))
            val decoded = if (compression == 1) text else decompress(text, recordSize)
            if (decoded.size > recordSize || output.size().toLong() + decoded.size > BookContent.MAX_TEXT) BookContent.fail("O texto expandido do MOBI excede o limite permitido.")
            output.write(decoded)
        }
        if (output.size().toLong() < textLength) BookContent.fail("O texto do MOBI está incompleto.")
        val markup = String(output.toByteArray(), 0, textLength.toInt(), charset)
        val doc = Jsoup.parse(markup, "", Parser.htmlParser().setMaxDepth(128))
        val parts = ArrayList<BookPart>(); val text = StringBuilder()
        fun flush() { BookContent.sections(text.toString()).forEach { parts.add(BookPart(text = it)) }; text.setLength(0) }
        fun visit(node: Node) {
            BookContent.interrupted()
            if (node is TextNode) { text.append(node.wholeText); return }
            if (node !is Element) return
            val tag = node.normalName().substringAfter(':')
            if (tag in setOf("script", "style", "head", "iframe", "object", "audio", "video", "form") || node.hasAttr("hidden")) return
            if (tag == "img") {
                val relative = node.attr("recindex").toIntOrNull()
                if (relative != null && relative > 0 && imageStart < count && imageStart + relative - 1 < count) {
                    val index = (imageStart + relative - 1).toInt()
                    val bytes = record(index, limits.maxPageBytes)
                    if (raster(bytes)) { flush(); parts.add(BookPart(image = index.toString())) }
                    else text.append(" [${node.attr("alt").ifBlank { "Imagem não compatível" }}] ")
                } else text.append(" [${node.attr("alt").ifBlank { "Imagem não disponível" }}] ")
                return
            }
            val block = tag in setOf("p", "div", "section", "h1", "h2", "h3", "h4", "li", "blockquote", "br", "tr", "pagebreak")
            if (block) text.append('\n')
            node.childNodes().forEach(::visit)
            if (block) text.append('\n')
            if (tag == "td" || tag == "th") text.append(" | ")
            if (parts.size > limits.maxEntries) BookContent.fail("O MOBI contém seções demais.")
        }
        visit(doc.body()); flush()
        if (parts.isEmpty() || parts.size > limits.maxEntries) BookContent.fail("Nenhum texto ou imagem compatível foi encontrado no MOBI.")
        parts
    }
    /** Strip each backward-sized trailing record, then the optional UTF-8 overlap. */
    private fun textEnd(bytes: ByteArray, flags: Int): Int {
        var end = bytes.size
        for (bit in 15 downTo 1) if (flags and (1 shl bit) != 0) {
            var cursor = end - 1; var size = 0; var shift = 0; var complete = false
            while (cursor >= 0 && shift < 28) {
                val value = bytes[cursor--].toInt() and 255
                size = size or ((value and 127) shl shift); shift += 7
                if (value and 128 != 0) { complete = true; break }
            }
            if (!complete || size < end - cursor - 1 || size > end) BookContent.fail("Dados adicionais MOBI inválidos.")
            end -= size
        }
        if (flags and 1 != 0) {
            if (end == 0) BookContent.fail("Dados adicionais MOBI incompletos.")
            end -= (bytes[end - 1].toInt() and 3) + 1
            if (end < 0) BookContent.fail("Dados adicionais MOBI inválidos.")
        }
        return end
    }
    private fun decompress(bytes: ByteArray, limit: Int): ByteArray {
        val output = ByteArray(limit); var size = 0; var at = 0
        fun put(value: Byte) { if (size >= limit) BookContent.fail("Um registro MOBI expandido excede o limite permitido."); output[size++] = value }
        while (at < bytes.size) {
            BookContent.interrupted()
            val value = bytes[at++].toInt() and 255
            when (value) {
                in 1..8 -> { if (at + value > bytes.size) BookContent.fail("Registro PalmDOC incompleto."); repeat(value) { put(bytes[at++]) } }
                in 0..127 -> put(value.toByte())
                in 128..191 -> {
                    if (at >= bytes.size) BookContent.fail("Referência PalmDOC incompleta.")
                    val pair = ((value and 63) shl 8) or (bytes[at++].toInt() and 255)
                    val distance = pair shr 3; val length = (pair and 7) + 3
                    if (distance == 0 || distance > size) BookContent.fail("Referência PalmDOC inválida.")
                    repeat(length) { put(output[size - distance]) }
                }
                else -> { put(32); put((value xor 128).toByte()) }
            }
        }
        return output.copyOf(size)
    }
    private fun raster(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() ||
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)) ||
        bytes.size >= 6 && bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) in setOf("GIF87a", "GIF89a") ||
        bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
    override fun index(file: File): List<PageRef> = guarded { BookContent.refs(book(file)) }
    override fun extract(file: File, page: PageRef, destination: File): File = guarded {
        val part = BookContent.part(book(file), page)
        if (part.text != null) part.text.toByteArray(Charsets.UTF_8).inputStream().use { copyLimited(it, destination, limits.maxPageBytes) }
        else RandomAccessFile(file, "r").use { input ->
            val index = part.image!!.toInt(); input.seek(76); val count = input.readUnsignedShort()
            input.seek(78L + index * 8L); val start = input.readInt().toLong() and 0xffffffffL
            val end = if (index + 1 == count) file.length() else { input.seek(78L + (index + 1) * 8L); input.readInt().toLong() and 0xffffffffL }
            if (end <= start || end - start > limits.maxPageBytes) BookContent.fail("Imagem MOBI inválida.")
            input.seek(start); val bytes = ByteArray((end - start).toInt()).also(input::readFully)
            bytes.inputStream().use { copyLimited(it, destination, limits.maxPageBytes) }
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() }
        catch (e: ComicArchiveException) { throw e }
        catch (e: Exception) { throw ComicArchiveException("Não foi possível ler o MOBI/AZW. Ele pode estar corrompido ou protegido.", e) }
}

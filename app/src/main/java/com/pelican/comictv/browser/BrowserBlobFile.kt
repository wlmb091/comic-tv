package com.pelican.comictv.browser

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/** Inspect the received bytes; a generic .bin is never assumed to be a comic. Originals stay untouched. */
internal object BrowserBlobFile {
    fun name(captured: String?, suggested: String, mime: String?): String {
        val result = BrowserDownloadNames.resolve("", null, mime, captured?.takeIf { it.isNotBlank() } ?: suggested)
        BrowserDownloadNames.validate(result, mime)
        return result
    }
    fun confirm(file: File, suggested: String): String {
        val name = BrowserDownloadNames.safe(suggested)
        BrowserDownloadNames.validate(name, null)
        val prefix = ByteArray(32)
        val count = file.inputStream().use { it.read(prefix) }
        if (count <= 0) throw IOException("O site forneceu um arquivo vazio.")
        fun starts(vararg bytes: Int) = count >= bytes.size && bytes.indices.all { prefix[it].toInt() and 255 == bytes[it] }
        val extension = name.substringAfterLast('.', "").lowercase()
        val actual = when {
            starts(82,97,114,33,26,7,0) || starts(82,97,114,33,26,7,1,0) -> if (extension == "rar") "rar" else "cbr"
            starts(80,75,3,4) || starts(80,75,5,6) || starts(80,75,7,8) -> {
              checkZipDirectory(file)
              ZipFile(file).use { zip ->
                if (zip.getEntry("AndroidManifest.xml") != null) throw IOException("Downloads de aplicativos Android não são permitidos neste navegador.")
                val epub = zip.getEntry("mimetype")?.let { entry -> zip.getInputStream(entry).use { input ->
                    val bytes = ByteArray(32); val n = input.read(bytes)
                    n > 0 && String(bytes, 0, n, Charsets.US_ASCII).trim() == "application/epub+zip"
                } } == true
                if (epub) "epub" else if (extension == "cbz") "cbz" else "zip"
              }
            }
            starts(37,80,68,70,45) -> "pdf"
            else -> null
        }
        if (actual == null) {
            if (BrowserDownloadNames.importable(name)) throw IOException("O conteúdo recebido não corresponde a um quadrinho ou livro reconhecido. Reabra o download no site.")
            return name
        }
        if (extension == actual) return name
        return name.substringBeforeLast('.', name).ifBlank { "arquivo" }.take(150) + ".$actual"
    }
    private fun checkZipDirectory(file: File) {
        val tail = RandomAccessFile(file, "r").use { input ->
            val bytes = ByteArray(minOf(input.length(), 65557L).toInt())
            input.seek(input.length() - bytes.size); input.readFully(bytes); bytes
        }
        fun short(at: Int) = (tail[at].toInt() and 255) or ((tail[at + 1].toInt() and 255) shl 8)
        fun integer(at: Int) = (0..3).fold(0L) { value, index -> value or ((tail[at + index].toLong() and 255) shl (8 * index)) }
        val end = (tail.size - 22 downTo 0).firstOrNull { at ->
            tail[at] == 80.toByte() && tail[at + 1] == 75.toByte() && tail[at + 2] == 5.toByte() && tail[at + 3] == 6.toByte() &&
                at + 22 + short(at + 20) == tail.size
        } ?: throw IOException("O arquivo ZIP recebido está incompleto ou não é compatível.")
        if (short(end + 10) > 5000 || integer(end + 12) > 8L * 1024 * 1024)
            throw IOException("O ZIP excede o limite de entradas para conferência neste aparelho.")
    }
}

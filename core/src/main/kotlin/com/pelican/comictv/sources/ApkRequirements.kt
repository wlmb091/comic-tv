package com.pelican.comictv.sources

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

/** Reads only the installation requirement; never loads or executes APK code. */
object ApkRequirements {
    private const val MAX_MANIFEST = 1024 * 1024
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    private const val MIN_SDK_ID = 0x0101020c

    fun minimumSdk(apk: File): Int = ZipFile(apk).use { zip ->
        val entries = zip.entries().asSequence().filter { it.name == "AndroidManifest.xml" }.toList()
        require(entries.size == 1) { "APK sem um AndroidManifest.xml único." }
        val entry = entries.single()
        require(entry.size <= MAX_MANIFEST) { "AndroidManifest.xml excede o limite permitido." }
        val bytes = zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count <= MAX_MANIFEST - output.size()) { "AndroidManifest.xml excede o limite permitido." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        minimumSdkFromManifest(bytes)
    }

    fun requireSupported(minimum: Int, current: Int) {
        require(minimum > 0 && current > 0) { "Não foi possível determinar a versão do Android exigida." }
        val version = when (minimum) { 23 -> "6.0"; 24 -> "7.0"; 25 -> "7.1"; 26 -> "8.0"; 27 -> "8.1"; 28 -> "9"; 29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"; else -> null }
        require(minimum <= current) {
            "Esta extensão exige Android ${version?.let { "$it (API $minimum)" } ?: "API $minimum"}; esta TV usa API $current. Use uma extensão compatível ou atualize o Android da TV."
        }
    }

    fun minimumSdkFromManifest(bytes: ByteArray): Int = Reader(bytes).read()

    private class Reader(private val bytes: ByteArray) {
        private fun invalid(): Nothing = throw IllegalArgumentException("Não foi possível verificar o Android exigido: manifesto APK inválido ou minSdkVersion não numérico.")
        private fun check(ok: Boolean) { if (!ok) invalid() }
        private fun range(offset: Int, count: Int, end: Int = bytes.size) { check(offset >= 0 && count >= 0 && offset <= end && count <= end - offset) }
        private fun u16(p: Int): Int { range(p, 2); return (bytes[p].toInt() and 255) or ((bytes[p + 1].toInt() and 255) shl 8) }
        private fun i32(p: Int): Int { range(p, 4); return u16(p) or (u16(p + 2) shl 16) }
        private var strings: List<String>? = null
        private var resources = IntArray(0)
        private fun string(index: Int): String = strings?.getOrNull(index) ?: invalid()
        private fun optionalString(index: Int): String? = if (index == -1) null else string(index)

        fun read(): Int {
            check(bytes.size in 8..MAX_MANIFEST)
            check(u16(0) == 3 && u16(2) == 8 && i32(4) == bytes.size)
            var p = 8
            val elements = ArrayList<Pair<String?, String>>()
            val namespaces = ArrayList<Pair<String?, String>>()
            var resourceMapSeen = false
            var rootSeen = false
            var sdkSeen = false
            var minimum = 1
            while (p < bytes.size) {
                range(p, 8)
                val type = u16(p)
                val header = u16(p + 2)
                val size = i32(p + 4)
                check(header >= 8 && size >= header)
                range(p, size)
                val end = p + size
                when (type) {
                    1 -> { check(strings == null && !rootSeen && header >= 28); strings = pool(p, header, end) }
                    0x180 -> {
                        check(strings != null && !rootSeen && !resourceMapSeen && header == 8 && (size - 8) % 4 == 0)
                        resourceMapSeen = true
                        resources = IntArray((size - 8) / 4) { i32(p + 8 + it * 4) }
                    }
                    0x100, 0x101 -> {
                        check(header == 16); range(p + header, 8, end)
                        val namespace = optionalString(i32(p + 16)) to string(i32(p + 20))
                        if (type == 0x100) namespaces.add(namespace)
                        else check(namespaces.isNotEmpty() && namespaces.removeAt(namespaces.lastIndex) == namespace)
                    }
                    0x102 -> {
                        check(header == 16); range(p + header, 20, end)
                        val ns = optionalString(i32(p + 16))
                        val name = string(i32(p + 20))
                        if (elements.isEmpty()) { check(!rootSeen && name == "manifest" && ns == null); rootSeen = true }
                        val sdk = name == "uses-sdk"
                        if (sdk) { check(elements.size == 1 && ns == null && !sdkSeen); sdkSeen = true }
                        val start = u16(p + 24)
                        val stride = u16(p + 26)
                        val count = u16(p + 28)
                        check(start >= 20 && stride >= 20)
                        range(p + 16, start, end)
                        val attributes = p + 16 + start
                        check(count <= (end - attributes) / stride)
                        var found = false
                        repeat(count) { index ->
                            val a = attributes + index * stride
                            val attrNs = optionalString(i32(a))
                            val attrNameIndex = i32(a + 4)
                            val attrName = string(attrNameIndex)
                            val raw = optionalString(i32(a + 8))
                            check(u16(a + 12) == 8 && bytes[a + 14].toInt() == 0)
                            val valueType = bytes[a + 15].toInt() and 255
                            val value = i32(a + 16)
                            if (valueType == 3) string(value)
                            val resource = resources.getOrNull(attrNameIndex) ?: 0
                            if (sdk && (attrName == "minSdkVersion" || resource == MIN_SDK_ID)) {
                                check(!found && attrNs == ANDROID && (resource == 0 || resource == MIN_SDK_ID))
                                found = true
                                minimum = when (valueType) {
                                    0x10, 0x11 -> value.also { check(it > 0) }
                                    3 -> number(string(value))
                                    else -> invalid()
                                }
                                if (raw != null) check(number(raw) == minimum)
                            }
                        }
                        elements.add(ns to name)
                    }
                    0x103 -> {
                        check(header == 16); range(p + header, 8, end)
                        val element = optionalString(i32(p + 16)) to string(i32(p + 20))
                        check(elements.isNotEmpty() && elements.removeAt(elements.lastIndex) == element)
                    }
                    else -> invalid()
                }
                p = end
            }
            check(rootSeen && elements.isEmpty() && namespaces.isEmpty())
            return minimum
        }

        private fun number(value: String): Int {
            check(value.isNotEmpty() && value.all { it in '0'..'9' })
            return (value.toIntOrNull() ?: invalid()).also { check(it > 0) }
        }

        private fun pool(p: Int, header: Int, end: Int): List<String> {
            val count = i32(p + 8)
            val styles = i32(p + 12)
            val utf8 = i32(p + 16) and 0x100 != 0
            val start = i32(p + 20)
            val styleStart = i32(p + 24)
            check(count >= 0 && styles >= 0 && count <= (end - p - header) / 4)
            check(styles <= (end - p - header - count * 4) / 4)
            check(start >= header + (count + styles) * 4 && start <= end - p)
            check(styleStart == 0 || styleStart in start..(end - p))
            val dataEnd = if (styleStart == 0) end else p + styleStart
            var decodedCharacters = 0
            return List(count) { index ->
                val offset = i32(p + header + index * 4)
                check(offset >= 0 && offset <= dataEnd - p - start)
                var cursor = p + start + offset
                fun length(): Int {
                    range(cursor, if (utf8) 1 else 2, dataEnd)
                    val first = if (utf8) bytes[cursor++].toInt() and 255 else u16(cursor).also { cursor += 2 }
                    val flag = if (utf8) 0x80 else 0x8000
                    if (first and flag == 0) return first
                    range(cursor, if (utf8) 1 else 2, dataEnd)
                    val second = if (utf8) bytes[cursor++].toInt() and 255 else u16(cursor).also { cursor += 2 }
                    return ((first and (flag - 1)) shl (if (utf8) 8 else 16)) or second
                }
                val chars = length()
                check(chars >= 0 && chars <= MAX_MANIFEST - decodedCharacters)
                decodedCharacters += chars
                val byteCount = if (utf8) length() else chars * 2
                range(cursor, byteCount, dataEnd)
                range(cursor + byteCount, if (utf8) 1 else 2, dataEnd)
                check(bytes[cursor + byteCount].toInt() == 0 && (utf8 || bytes[cursor + byteCount + 1].toInt() == 0))
                val decoder = (if (utf8) Charsets.UTF_8 else Charsets.UTF_16LE).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                val text = try { decoder.decode(ByteBuffer.wrap(bytes, cursor, byteCount)).toString() } catch (_: java.nio.charset.CharacterCodingException) { invalid() }
                check(text.length == chars)
                text
            }
        }
    }
}

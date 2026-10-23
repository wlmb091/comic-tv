package com.pelican.comictv.sources

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

/** Blocking, bounded repository reader. Call from an IO dispatcher; never loads extension code. */
class MihonRepoClient(private val transport: (String) -> ByteArray = ::download) {
    fun normalize(url: String): String {
        val uri = secure(url.trim())
        require(uri.query == null) { "URL de repositório não aceita parâmetros de consulta" }
        val parts = uri.path.trim('/').split('/').filter { it.isNotEmpty() }
        if (uri.host.equals("github.com", true)) {
            require(parts.size >= 2) { "Informe proprietário e repositório GitHub" }
            val branch = if (parts.size >= 4 && parts[2] in listOf("tree", "raw", "blob")) parts[3] else "repo"
            val file = if (parts.size > 4) parts.drop(4).joinToString("/") else "repo.json"
            return secure("https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/$branch/$file").toString()
        }
        return if (uri.path.substringAfterLast('/').contains('.')) uri.toString()
            else uri.toString().trimEnd('/') + "/repo.json"
    }

    fun fetch(url: String): ExtensionRepository {
        val normalized = normalize(url)
        val base = URI(normalized).resolve(".").toString()
        val initial = read(normalized)
        val json = json(initial)
        var name = URI(base).host
        val fingerprint: String
        val entries: List<ExtensionEntry>
        if (json?.isJsonObject == true && json.asJsonObject.has("meta")) {
            val descriptor = json.asJsonObject
            val meta = descriptor.getAsJsonObject("meta")
            name = meta.string("name").ifBlank { name }
            fingerprint = meta.string("signingKeyFingerprint")
            require(fingerprint.matches(Regex("[a-fA-F0-9]{64}"))) { "Fingerprint de repositório inválido" }
            val indexUrl = descriptor.string("index_v2").ifBlank { "index.min.json" }
            entries = parse(read(resolve(base, indexUrl)), base, false).third
        } else {
            val parsed = parse(initial, base, false)
            name = parsed.first.ifBlank { name }
            fingerprint = parsed.second
            entries = parsed.third
        }
        require(entries.isNotEmpty()) { "Repositório vazio ou incompatível" }
        require(entries.none { it.name.contains("outdated", true) || it.packageName.contains("outdated", true) }) {
            "Índice antigo incompatível; use repo.json atual"
        }
        return ExtensionRepository(base, name, fingerprint.lowercase(), entries)
    }

    private fun read(url: String): ByteArray {
        secure(url)
        val raw = transport(url)
        require(raw.size <= MAX_WIRE) { "Índice excede 8 MiB" }
        val gzip = raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()
        return if (gzip) GZIPInputStream(raw.inputStream()).use { bounded(it, MAX_PLAIN) } else raw
    }

    private fun parse(bytes: ByteArray, base: String, external: Boolean): Triple<String, String, List<ExtensionEntry>> {
        val json = json(bytes)
        if (json != null) {
            if (json.isJsonArray) {
                require(json.asJsonArray.size() <= MAX_EXT)
                return Triple("", "", json.asJsonArray.map { entry(it.asJsonObject, base, true) })
            }
            val objectJson = json.asJsonObject
            val link = objectJson.string("extensionListUrl")
            if (link.isNotBlank()) {
                require(!external) { "Listas externas recursivas" }
                return Triple(objectJson.string("name"), objectJson.string("signingKey"),
                    parse(read(resolve(base, link)), base, true).third)
            }
            val list = objectJson.getAsJsonObject("extensionList")?.get("extensions") ?: objectJson.get("extensions")
            require(list != null && list.isJsonArray) { "Lista v2 ausente" }
            require(list.asJsonArray.size() <= MAX_EXT)
            return Triple(objectJson.string("name"), objectJson.string("signingKey"),
                list.asJsonArray.map { entry(it.asJsonObject, base, false) })
        }
        val fields = Proto(bytes).fields()
        val link = fields.text(INDEX_LIST_URL)
        if (link.isNotBlank()) {
            require(!external) { "Listas externas recursivas" }
            return Triple(fields.text(INDEX_NAME), fields.text(INDEX_SIGNING_KEY),
                parse(read(resolve(base, link)), base, true).third)
        }
        val list = fields.firstOrNull { it.number == INDEX_LIST }?.data
        val extensions = Proto(list ?: bytes).fields().filter { it.number == LIST_EXTENSION && it.data != null }
        require(extensions.size <= MAX_EXT)
        return Triple(if (list != null) fields.text(INDEX_NAME) else "",
            if (list != null) fields.text(INDEX_SIGNING_KEY) else "",
            extensions.map { protoEntry(it.data!!, base) })
    }

    private fun protoEntry(bytes: ByteArray, base: String): ExtensionEntry {
        val fields = Proto(bytes).fields()
        val resources = fields.firstOrNull { it.number == EXT_RESOURCES }?.data
            ?.let { Proto(it).fields() }.orEmpty()
        val sources = fields.filter { it.number == EXT_SOURCE }
        require(sources.size <= MAX_SOURCES)
        val sourceFields = sources.map { Proto(it.data ?: error("Fonte inválida")).fields() }
        return valid(ExtensionEntry(
            packageName = fields.text(EXT_PACKAGE),
            name = fields.text(EXT_NAME),
            versionName = fields.text(EXT_VERSION_NAME),
            versionCode = fields.firstOrNull { it.number == EXT_VERSION_CODE }?.value ?: 0,
            libVersion = fields.text(EXT_LIBRARY),
            apkUrl = resolve(base, resources.text(RESOURCE_APK)),
            iconUrl = resources.text(RESOURCE_ICON).takeIf { it.isNotBlank() }?.let { resolve(base, it) },
            languages = sourceFields.map { it.text(SOURCE_LANGUAGE) }.distinct(),
            sourceNames = sourceFields.map { it.text(SOURCE_NAME) },
        ))
    }

    private fun entry(json: JsonObject, base: String, legacy: Boolean): ExtensionEntry {
        val sources = json.getAsJsonArray("sources")?.toList().orEmpty()
        require(sources.size <= MAX_SOURCES)
        val resources = if (legacy) json else json.getAsJsonObject("resources") ?: JsonObject()
        val version = json.string(if (legacy) "version" else "versionName")
        val apkPath = if (legacy) "apk/" + json.string("apk") else resources.string("apkUrl")
        return valid(ExtensionEntry(
            packageName = json.string(if (legacy) "pkg" else "packageName"),
            name = json.string("name"),
            versionName = version,
            versionCode = json.get(if (legacy) "code" else "versionCode")?.asLong ?: 0,
            libVersion = if (legacy) version.substringBeforeLast('.', "") else json.string("extensionLib"),
            apkUrl = resolve(base, apkPath),
            iconUrl = resources.string(if (legacy) "icon" else "iconUrl")
                .takeIf { it.isNotBlank() }?.let { resolve(base, it) },
            languages = sources.map { it.asJsonObject.string(if (legacy) "lang" else "language") }
                .ifEmpty { listOf(json.string("lang")) }.filter { it.isNotBlank() }.distinct(),
            sourceNames = sources.map { it.asJsonObject.string("name") },
        ))
    }

    private fun valid(entry: ExtensionEntry): ExtensionEntry {
        require(entry.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) &&
            entry.name.isNotBlank() && entry.versionCode > 0) { "Extensão inválida" }
        return entry
    }

    private fun resolve(base: String, path: String): String {
        require(path.isNotBlank()) { "URL de recurso ausente" }
        return secure(URI(base).resolve(path).toString()).toString()
    }

    private fun json(bytes: ByteArray): JsonElement? {
        val text = bytes.toString(Charsets.UTF_8).trimStart()
        if (!text.startsWith("{") && !text.startsWith("[")) return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 64) { "JSON muito aninhado" } }
                '}', ']' -> depth--
            }
        }
        return JsonParser.parseString(text)
    }

    private fun JsonObject.string(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private data class Field(val number: Int, val data: ByteArray? = null, val value: Long = 0)
    private fun List<Field>.text(number: Int) = firstOrNull { it.number == number }?.data?.toString(Charsets.UTF_8).orEmpty()

    private class Proto(private val bytes: ByteArray) {
        private var position = 0
        private fun variable(): Long {
            var result = 0L
            for (index in 0..9) {
                require(position < bytes.size)
                val byte = bytes[position++].toInt() and 255
                require(index < 9 || byte <= 1)
                result = result or ((byte and 127).toLong() shl (index * 7))
                if (byte < 128) return result
            }
            error("Varint inválido")
        }

        fun fields(): List<Field> {
            val result = ArrayList<Field>()
            while (position < bytes.size) {
                require(result.size < 100000)
                val tag = variable()
                val number = (tag ushr 3).toInt()
                require(number > 0)
                when ((tag and 7).toInt()) {
                    0 -> result.add(Field(number, value = variable()))
                    2 -> {
                        val length = variable()
                        require(length >= 0 && length <= bytes.size - position)
                        val end = position + length.toInt()
                        result.add(Field(number, bytes.copyOfRange(position, end)))
                        position = end
                    }
                    1 -> { require(position + 8 <= bytes.size); position += 8 }
                    5 -> { require(position + 4 <= bytes.size); position += 4 }
                    else -> error("Wire type protobuf inválido")
                }
            }
            return result
        }
    }

    companion object {
        private const val MAX_WIRE = 8 * 1024 * 1024
        private const val MAX_PLAIN = 16 * 1024 * 1024
        private const val MAX_EXT = 10000
        private const val MAX_SOURCES = 1000
        // mihonapp/extensions-lib index/index.proto field numbers.
        private const val INDEX_NAME = 1
        private const val INDEX_SIGNING_KEY = 3
        private const val INDEX_LIST = 101
        private const val INDEX_LIST_URL = 102
        private const val LIST_EXTENSION = 1
        private const val EXT_NAME = 1
        private const val EXT_PACKAGE = 2
        private const val EXT_RESOURCES = 3
        private const val EXT_LIBRARY = 4
        private const val EXT_VERSION_CODE = 5
        private const val EXT_VERSION_NAME = 6
        private const val EXT_SOURCE = 8
        private const val RESOURCE_APK = 1
        private const val RESOURCE_ICON = 2
        private const val SOURCE_NAME = 2
        private const val SOURCE_LANGUAGE = 3

        private fun secure(url: String): URI {
            val uri = URI(url)
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.fragment == null) { "Use URL HTTPS sem credenciais ou fragmentos" }
            return uri
        }

        private fun bounded(input: InputStream, maximum: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) { "Resposta excede limite" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        private fun download(url: String): ByteArray {
            var current = url
            repeat(6) {
                val connection = secure(current).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept-Encoding", "identity")
                try {
                    val status = connection.responseCode
                    if (status in listOf(301, 302, 303, 307, 308)) {
                        val location = connection.getHeaderField("Location") ?: error("Redirect sem URL")
                        current = secure(URI(current).resolve(location).toString()).toString()
                    } else {
                        require(status in 200..299) { "HTTP $status" }
                        require(connection.contentLengthLong <= MAX_WIRE)
                        return connection.inputStream.use { bounded(it, MAX_WIRE) }
                    }
                } finally {
                    connection.disconnect()
                }
            }
            error("Muitos redirecionamentos")
        }
    }
}

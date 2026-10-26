package com.pelican.comictv.updates

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

data class UpdateManifest(val versionCode: Long, val versionName: String, val minSdk: Int,
    val sizeBytes: Long, val sha256: String, val apkUrl: String, val notes: String) {
    init {
        require(versionCode in 1..Int.MAX_VALUE.toLong())
        require(versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) && versionName.length <= 40)
        require(minSdk in 1..10_000 && sizeBytes in 1..MAX_APK_BYTES)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(apkUrl == "$RELEASE_BASE/v$versionName/Comic-TV-$versionName.apk")
        require(notes.length <= 12_000 && notes.none { it.code < 32 && it !in "\n\r\t" })
    }
    fun isNewerThan(installed: Long) = versionCode > installed
    companion object {
        const val MAX_APK_BYTES = 64L * 1024 * 1024
        const val RELEASE_BASE = "https://github.com/wlmb091/comic-tv-updates/releases/download"
        const val CHANNEL_URL = "https://github.com/wlmb091/comic-tv-updates/releases/latest/download/update.json"
    }
}

object UpdateManifestCodec {
    const val MAX_BYTES = 64 * 1024
    private val fields = setOf("schemaVersion", "applicationId", "versionCode", "versionName", "minSdk", "sizeBytes", "sha256", "apkUrl", "notes")
    fun decode(bytes: ByteArray): UpdateManifest {
        require(bytes.size <= MAX_BYTES) { "Informações de atualização excedem o limite." }
        try {
            val values = linkedMapOf<String, JsonElement>()
            JsonReader(StringReader(bytes.toString(Charsets.UTF_8))).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(key in fields && key !in values)
                    values[key] = JsonParser.parseReader(reader)
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT && values.keys == fields)
            }
            fun number(key: String): Long {
                val value = requireNotNull(values[key]).asJsonPrimitive
                require(value.isNumber && value.toString().matches(Regex("0|[1-9][0-9]*")))
                return requireNotNull(value.toString().toLongOrNull())
            }
            fun string(key: String): String {
                val value = requireNotNull(values[key]).asJsonPrimitive
                require(value.isString); return value.asString
            }
            require(number("schemaVersion") == 1L && string("applicationId") == "com.pelican.comictv")
            val sdk = number("minSdk"); require(sdk in 1..10_000)
            return UpdateManifest(number("versionCode"), string("versionName"), sdk.toInt(), number("sizeBytes"),
                string("sha256"), string("apkUrl"), string("notes"))
        } catch (e: Exception) { throw IllegalArgumentException("Informações de atualização inválidas.", e) }
    }
}

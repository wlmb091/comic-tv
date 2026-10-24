package com.pelican.comictv.backup

import com.google.gson.Gson
import com.google.gson.Strictness
import com.google.gson.stream.*
import com.pelican.comictv.library.validatedCollectionName
import com.pelican.comictv.sources.OnlineIdentity
import java.io.*
import java.math.BigDecimal

/** Fixed schema bounds depth/types before Gson can coerce a string to a boolean/number. */
object BackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val gson = Gson()
    private data class Rule(val fields: Map<String, Rule>? = null, val element: Rule? = null,
        val limit: Int = 0, val kind: JsonToken? = null, val optional: Boolean = false, val decimal: Boolean = false, val intValue: Boolean = false)
    private fun text(max: Int, optional: Boolean = false) = Rule(limit = max, kind = JsonToken.STRING, optional = optional)
    private val number = Rule(kind = JsonToken.NUMBER)
    private val smallNumber = number.copy(intValue = true)
    private val boolean = Rule(kind = JsonToken.BOOLEAN)
    private fun obj(vararg fields: Pair<String, Rule>) = Rule(fields = mapOf(*fields))
    private val source = obj("packageName" to text(200), "id" to number, "name" to text(300), "language" to text(30), "baseUrl" to text(4096))
    private val title = obj("key" to text(4096), "title" to text(300), "coverUrl" to text(4096, true), "description" to text(16000, true),
        "author" to text(300, true), "artist" to text(300, true), "publisher" to text(300, true), "characters" to text(2000, true), "director" to text(300, true))
    private val chapter = obj("key" to text(4096), "title" to text(300), "number" to number.copy(decimal = true))
    private val online = obj("source" to source, "title" to title, "chapter" to chapter, "pin" to text(64)).copy(optional = true)
    private val comic = obj("id" to text(100), "title" to text(300), "pageCount" to smallNumber, "currentPage" to smallNumber,
        "lastRead" to number, "importedAt" to number, "fit" to smallNumber, "rtl" to boolean, "hiddenFromHistory" to boolean,
        "favorite" to boolean, "sha256" to text(64, true), "online" to online, "imagePreset" to text(30), "imageIntensity" to smallNumber)
    private val schema = obj("format" to text(30), "version" to smallNumber, "createdAt" to number,
        "comics" to Rule(element = comic, limit = 20000),
        "collections" to Rule(element = obj("id" to text(100), "name" to text(100)), limit = 2000),
        "links" to Rule(element = obj("comicId" to text(100), "collectionId" to text(100)), limit = 100000),
        "appearance" to obj("preset" to text(30), "textSize" to smallNumber, "highContrast" to boolean, "coverBackground" to boolean,
            "textPercent" to smallNumber.copy(optional = true)))

    fun decode(input: InputStream): BackupDocument {
        val bytes = ByteArrayOutputStream().also { output ->
            val buffer = ByteArray(16384)
            while (true) { val count = input.read(buffer); if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "Backup excede 32 MiB." }; output.write(buffer, 0, count) }
        }.toByteArray()
        JsonReader(InputStreamReader(bytes.inputStream(), Charsets.UTF_8)).use { reader ->
            reader.strictness = Strictness.STRICT
            checkRule(reader, schema)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Há conteúdo após o backup." }
        }
        val result = gson.fromJson(InputStreamReader(bytes.inputStream(), Charsets.UTF_8), BackupDocument::class.java)
        validate(result)
        return result
    }
    fun encode(document: BackupDocument): ByteArray {
        validate(document)
        val bytes = ByteArrayOutputStream()
        val bounded = object : OutputStream() {
            override fun write(value: Int) { require(bytes.size() < MAX_BYTES); bytes.write(value) }
            override fun write(buffer: ByteArray, offset: Int, length: Int) { require(bytes.size().toLong() + length <= MAX_BYTES); bytes.write(buffer, offset, length) }
        }
        OutputStreamWriter(bounded, Charsets.UTF_8).use { gson.toJson(document, it) }
        // Also enforce per-field bounds on internally generated data.
        val result = bytes.toByteArray()
        JsonReader(InputStreamReader(result.inputStream(), Charsets.UTF_8)).use { checkRule(it, schema) }
        return result
    }
    private fun checkRule(reader: JsonReader, rule: Rule) {
        if (reader.peek() == JsonToken.NULL && rule.optional) { reader.nextNull(); return }
        when {
            rule.fields != null -> {
                require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Objeto inválido no backup." }
                reader.beginObject(); val seen = hashSetOf<String>()
                while (reader.hasNext()) {
                    val name = reader.nextName(); require(seen.add(name)) { "Campo repetido: $name" }
                    checkRule(reader, requireNotNull(rule.fields[name]) { "Campo desconhecido: $name" })
                }
                reader.endObject()
                require(rule.fields.all { (name, field) -> field.optional || name in seen }) { "Backup incompleto." }
            }
            rule.element != null -> {
                require(reader.peek() == JsonToken.BEGIN_ARRAY); reader.beginArray(); var count = 0
                while (reader.hasNext()) { require(++count <= rule.limit) { "Backup excede o limite de registros." }; checkRule(reader, rule.element) }
                reader.endArray()
            }
            rule.kind == JsonToken.STRING -> { require(reader.peek() == JsonToken.STRING); require(reader.nextString().length <= rule.limit) { "Texto acima do limite." } }
            rule.kind == JsonToken.BOOLEAN -> { require(reader.peek() == JsonToken.BOOLEAN); reader.nextBoolean() }
            else -> {
                require(reader.peek() == JsonToken.NUMBER); val value = reader.nextString(); require(value.length <= 30)
                if (rule.decimal) require(value.toFloat().isFinite())
                else if (rule.intValue) BigDecimal(value).intValueExact() else BigDecimal(value).longValueExact()
            }
        }
    }
    fun validate(document: BackupDocument) {
        require(document.format == "ComicTVBackup" && document.version == 1) { "Formato ou versão de backup não suportada." }
        require(document.createdAt >= 0 && document.comics.size <= 20000 && document.collections.size <= 2000 && document.links.size <= 100000)
        fun id(value: String) = value.matches(Regex("[A-Za-z0-9-]{1,100}"))
        require(document.comics.map { it.id }.toSet().size == document.comics.size)
        require(document.collections.map { it.id }.toSet().size == document.collections.size)
        val names = document.collections.map { require(id(it.id)); validatedCollectionName(it.name).second }
        require(names.toSet().size == names.size) { "Nomes de coleção repetidos." }
        for (entry in document.comics) {
            require(id(entry.id) && entry.title.isNotBlank() && entry.title.length <= 300)
            require(entry.pageCount in 1..5000 && entry.currentPage in 0 until entry.pageCount && entry.lastRead >= 0 && entry.importedAt >= 0 && entry.fit in 0..2)
            require(entry.imagePreset in setOf("Original", "Ink", "Vintage") && entry.imageIntensity in 0..2)
            require(entry.sha256 == null || entry.sha256.matches(Regex("[a-f0-9]{64}")))
            if (OnlineIdentity.isOnline(entry.id)) {
                val online = requireNotNull(entry.online) { "Metadados online ausentes." }
                require(entry.sha256 == null && OnlineIdentity.id(online.source, online.title, online.chapter) == entry.id)
                require(online.pin.matches(Regex("[a-f0-9]{64}")) && online.source.packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_.]{1,199}")))
            } else require(!entry.id.startsWith("online-") && entry.online == null && entry.sha256 != null) { "Arquivo local sem impressão digital ou identidade inválida." }
        }
        val comics = document.comics.map { it.id }.toSet(); val collections = document.collections.map { it.id }.toSet()
        require(document.links.toSet().size == document.links.size)
        require(document.links.all { it.comicId in comics && it.collectionId in collections }) { "Vínculo sem edição/coleção correspondente." }
        require(document.appearance.textSize in 0..2 && document.appearance.preset in setOf("Amber", "Flix", "Prime", "Max", "Cinema", "Green", "Blue", "Violet"))
        document.appearance.textPercent?.let { require(it in 85..130 && it % 5 == 0) { "Tamanho do texto inválido." } }
    }
}

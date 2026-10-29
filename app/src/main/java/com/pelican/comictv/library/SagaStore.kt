package com.pelican.comictv.library

import android.util.AtomicFile
import com.google.gson.Gson
import com.pelican.comictv.sources.OnlineIdentity
import com.pelican.comictv.sources.OnlineRecordCodec
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

/** Only exact source + title keys establish automatic membership. Display names never do. */
internal fun sagaIdentity(packageName: String, sourceId: Long, titleKey: String): String {
    val value = listOf(packageName, sourceId.toString(), titleKey).joinToString("") { "${it.length}:$it" }
    return "source-" + MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

data class SagaGroup(val key: String, val title: String, val editions: List<ComicEntity>, val cover: Any?) {
    val representative: ComicEntity get() = editions.minWith(compareBy<ComicEntity> { it.importedAt }.thenBy { it.id })
    val display: ComicEntity get() = representative.copy(title = title, lastRead = 0, currentPage = 0)
}
internal data class SagaMetadata(val key: String, val title: String, val coverUrl: String?, val chapterNumber: Float)
internal data class SagaSettings(val assignments: Map<String, String> = emptyMap(), val covers: Map<String, String> = emptyMap())

/** Independent, atomic user preferences: no original file, progress or collection is changed. */
internal class SagaStore(private val directory: File) {
    private val file = AtomicFile(File(directory, "sagas.json"))
    private val gson = Gson()
    fun read(): SagaSettings {
        if (!file.baseFile.exists() && !File(directory, "sagas.json.bak").exists()) return SagaSettings()
        return file.openRead().use { input ->
            val bytes = input.readSagaBytes(MAX_BYTES)
            require(bytes.size <= MAX_BYTES)
            (gson.fromJson(bytes.toString(Charsets.UTF_8), SagaSettings::class.java) ?: SagaSettings()).also(::validate)
        }
    }
    fun write(settings: SagaSettings) {
        validate(settings)
        val bytes = gson.toJson(settings).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        directory.mkdirs()
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    private fun validate(settings: SagaSettings) {
        require(settings.assignments.size <= 20000 && settings.covers.size <= 20000)
        require(settings.assignments.all { (id, group) -> validId(id) && group.length in 1..200 })
        require(settings.covers.all { (group, id) -> group.length in 1..200 && validId(id) })
    }
    private fun validId(id: String) = id.matches(Regex("[A-Za-z0-9-]{1,100}"))
    companion object { const val MAX_BYTES = 4 * 1024 * 1024 }
}

internal data class SagaMetadataCacheEntry(val modified: Long, val length: Long, val metadata: SagaMetadata)
internal suspend fun readSagaMetadata(directory: File, comics: List<ComicEntity>,
    cache: MutableMap<String, SagaMetadataCacheEntry> = linkedMapOf()): Map<String, SagaMetadata> = buildMap {
    val activeIds = comics.mapTo(hashSetOf()) { it.id }
    cache.keys.retainAll(activeIds)

    for (comic in comics) {
        currentCoroutineContext().ensureActive()
        if (!OnlineIdentity.isOnline(comic.id)) continue
        val file = File(directory, "${comic.id}.json")
        if (!file.isFile || file.length() > OnlineRecordCodec.MAX_BYTES) { cache.remove(comic.id); continue }
        val modified = file.lastModified()
        val length = file.length()
        cache[comic.id]?.takeIf { it.modified == modified && it.length == length }?.let {
            put(comic.id, it.metadata)
        }
        if (containsKey(comic.id)) continue
        try {
            val record = OnlineRecordCodec.decode(comic.id, file.inputStream().use { it.readSagaBytes(OnlineRecordCodec.MAX_BYTES) })
            val metadata = SagaMetadata(sagaIdentity(record.source.packageName, record.source.id, record.title.key),
                record.title.title.take(300), record.title.coverUrl?.takeIf { it.length <= 4096 && it.startsWith("https://") }, record.chapter.number)
            put(comic.id, metadata)
            if (cache.size >= 512) cache.remove(cache.keys.first())
            cache[comic.id] = SagaMetadataCacheEntry(modified, length, metadata)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { cache.remove(comic.id) /* Damaged records stay individual. */ }
    }
}

internal fun buildSagaGroups(comics: List<ComicEntity>, metadata: Map<String, SagaMetadata>, settings: SagaSettings,
    collectionNames: Map<String, String>, cover: (String) -> Any?): List<SagaGroup> {
    val grouped = comics.groupBy { settings.assignments[it.id] ?: metadata[it.id]?.key ?: "edition:${it.id}" }
    return grouped.map { (key, editions) ->
        val stable = editions.minWith(compareBy<ComicEntity> { it.importedAt }.thenBy { it.id })
        val selected = settings.covers[key]?.takeIf { id -> editions.any { it.id == id } }
        val automatic = editions.sortedBy { it.importedAt }.firstNotNullOfOrNull { metadata[it.id] }
        val title = collectionNames[key.removePrefix("manual:")]?.takeIf { key.startsWith("manual:") }
            ?: automatic?.title ?: stable.title
        val model = selected?.let(cover) ?: automatic?.coverUrl ?: if (key.startsWith("edition:")) cover(stable.id) else null
        SagaGroup(key, title, editions.sortedWith(compareBy<ComicEntity> { metadata[it.id]?.chapterNumber ?: Float.MAX_VALUE }
            .thenBy { it.title }.thenBy { it.id }), model)
    }
}

private fun java.io.InputStream.readSagaBytes(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
        require(output.size() <= limit) { "Registro de saga excede o limite." }
    }
    return output.toByteArray()
}

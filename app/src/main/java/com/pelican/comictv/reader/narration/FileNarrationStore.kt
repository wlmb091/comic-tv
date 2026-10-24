package com.pelican.comictv.reader.narration

import com.google.gson.Gson
import com.pelican.comictv.reader.narrationDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileNarrationStore(private val directory: File) : NarrationStore {
    private val mutex = Mutex()
    private val gson = Gson()
    private fun file(book: String) = File(directory, narrationDigest(book) + ".json")
    private fun <T> read(file: File, type: Class<T>): T? = runCatching {
        if (!file.isFile || file.length() > 4096) null else gson.fromJson(file.readText(Charsets.UTF_8), type)
    }.getOrNull()
    private fun write(file: File, value: Any) {
        check(directory.isDirectory || directory.mkdirs()) { "Não foi possível salvar a narração." }
        val temporary = File.createTempFile("narration-", ".part", directory)
        try {
            FileOutputStream(temporary).use { out -> out.write(gson.toJson(value).toByteArray(Charsets.UTF_8)); out.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
    }
    override suspend fun load(book: String): NarrationBookmark? = withContext(Dispatchers.IO) {
        mutex.withLock { read(file(book), NarrationBookmark::class.java)?.takeIf {
            runCatching { it.page in 0..4999 && it.offset in 0..120_000 && it.digest.matches(Regex("[a-f0-9]{64}")) }.getOrDefault(false)
        } }
    }
    override suspend fun save(book: String, position: NarrationBookmark) = withContext(Dispatchers.IO) {
        require(position.page in 0..4999 && position.offset in 0..120_000 && position.digest.matches(Regex("[a-f0-9]{64}")))
        mutex.withLock { write(file(book), position) }
    }
    override suspend fun settings(): NarrationPreferences = withContext(Dispatchers.IO) {
        mutex.withLock { read(File(directory, "settings.json"), NarrationPreferences::class.java)?.takeIf {
            it.rateIndex in NARRATION_RATES.indices && (it.voiceId?.length ?: 0) <= 300
        } ?: NarrationPreferences() }
    }
    override suspend fun saveSettings(settings: NarrationPreferences) = withContext(Dispatchers.IO) {
        require(settings.rateIndex in NARRATION_RATES.indices && (settings.voiceId?.length ?: 0) <= 300)
        mutex.withLock { write(File(directory, "settings.json"), settings) }
    }
}

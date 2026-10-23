package com.pelican.comictv.sources

import com.google.gson.Gson

internal data class OnlineReadingRecord(
    val source: SourceHandle,
    val title: OnlineTitle,
    val chapter: OnlineChapter,
    val pin: String,
    val pages: List<OnlinePage>,
)

/** Persisted identity is verified independently from UI labels and transient URLs. */
internal object OnlineRecordCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    private val gson = Gson()
    fun encode(record: OnlineReadingRecord): ByteArray = gson.toJson(record).toByteArray(Charsets.UTF_8).also {
        require(it.size <= MAX_BYTES) { "Registro online excede o limite." }
    }
    fun decode(id: String, bytes: ByteArray): OnlineReadingRecord {
        require(bytes.size <= MAX_BYTES) { "Registro online excede o limite." }
        val record = requireNotNull(gson.fromJson(bytes.toString(Charsets.UTF_8), OnlineReadingRecord::class.java))
        require(OnlineIdentity.isOnline(id) && OnlineIdentity.id(record.source, record.title, record.chapter) == id) { "Registro online inválido." }
        require(record.pin.matches(Regex("[a-f0-9]{64}"))) { "Certificado inválido." }
        requirePages(record.pages)
        return record
    }
    fun requirePages(pages: List<OnlinePage>) {
        require(pages.isNotEmpty() && pages.size <= 5_000) { "Capítulo sem páginas ou acima do limite de 5.000 páginas." }
    }
}

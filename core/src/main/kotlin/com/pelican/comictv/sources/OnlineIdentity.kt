package com.pelican.comictv.sources

import java.security.MessageDigest

/** Stable reader identity excludes mutable labels, covers and signed image URLs. */
object OnlineIdentity {
    fun id(source: SourceHandle, title: OnlineTitle, chapter: OnlineChapter): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(source.packageName, source.id.toString(), title.key, chapter.key).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
            digest.update(bytes)
        }
        return "online-" + digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun isOnline(id: String) = id.matches(Regex("online-[a-f0-9]{64}"))
}

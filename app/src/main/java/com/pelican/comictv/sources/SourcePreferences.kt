package com.pelican.comictv.sources

import android.content.Context
import com.google.gson.Gson

class SourcePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("source-repositories", 0)
    private val gson = Gson()
    fun repositories(): List<SavedRepository> = runCatching {
        gson.fromJson(preferences.getString("repositories", "[]"), Array<SavedRepository>::class.java).toList()
            .take(32).filter { it.baseUrl.startsWith("https://") && it.fingerprint.matches(Regex("[a-f0-9]{64}")) }
    }.getOrDefault(emptyList())
    fun save(repositories: List<SavedRepository>) {
        require(repositories.size <= 32) { "Limite de 32 repositórios." }
        check(preferences.edit().putString("repositories", gson.toJson(repositories)).commit()) { "Não foi possível salvar os repositórios." }
    }
    fun isTrusted(fingerprint: String) = repositories().any { it.fingerprint == fingerprint }
    fun reversedSequence(context: OnlineEditions): Boolean = preferences.getBoolean(sequenceKey(context), false)
    fun saveSequenceOrder(context: OnlineEditions, reversed: Boolean) {
        check(preferences.edit().putBoolean(sequenceKey(context), reversed).commit()) { "Não foi possível salvar a ordem." }
    }
    private fun sequenceKey(context: OnlineEditions): String {
        val identity = gson.toJson(listOf(context.source.packageName, context.source.id.toString(), context.title.key))
        return "sequence-" + java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

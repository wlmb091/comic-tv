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
}

package com.pelican.comictv.reader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ReaderImageStore {
    fun read(id: String): ReaderImageOptions = ReaderImageOptions()
    suspend fun save(id: String, options: ReaderImageOptions) {}
    suspend fun saveAll(options: Map<String, ReaderImageOptions>) { for ((id,value) in options) save(id,value) }
}
class ReaderImagePreferences(context: Context) : ReaderImageStore {
    private val preferences = context.getSharedPreferences("reader-image", Context.MODE_PRIVATE)
    override fun read(id: String) = ReaderImageOptions.restore(preferences.getString(id + ":preset", null), preferences.getInt(id + ":intensity", 1))
    override suspend fun save(id: String, options: ReaderImageOptions) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(id + ":preset", options.preset.name).putInt(id + ":intensity", options.intensity).commit()) {
            "Não foi possível salvar o filtro desta edição."
        }
    }
    override suspend fun saveAll(options: Map<String, ReaderImageOptions>) = withContext(Dispatchers.IO) {
        val editor = preferences.edit()
        for ((id,value) in options) editor.putString(id + ":preset",value.preset.name).putInt(id + ":intensity",value.intensity)
        check(editor.commit()) { "Não foi possível salvar os filtros restaurados." }
    }
}

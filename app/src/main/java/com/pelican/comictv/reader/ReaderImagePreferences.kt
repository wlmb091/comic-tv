package com.pelican.comictv.reader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ReaderImageStore {
    fun read(id: String): ReaderImageOptions = ReaderImageOptions()
    suspend fun save(id: String, options: ReaderImageOptions) {}
}
class ReaderImagePreferences(context: Context) : ReaderImageStore {
    private val preferences = context.getSharedPreferences("reader-image", Context.MODE_PRIVATE)
    override fun read(id: String) = ReaderImageOptions.restore(preferences.getString(id + ":preset", null), preferences.getInt(id + ":intensity", 1))
    override suspend fun save(id: String, options: ReaderImageOptions) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(id + ":preset", options.preset.name).putInt(id + ":intensity", options.intensity).commit()) {
            "Não foi possível salvar o filtro desta edição."
        }
    }
}

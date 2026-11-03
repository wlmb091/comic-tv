package com.pelican.comictv.reader

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The presets live on the TV: the phone creates them, and the reader on the TV applies them. */
class AdjustmentPresetStore(context: Context) {
    private val preferences = context.getSharedPreferences("image-presets", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private fun read(): List<AdjustmentPreset> = runCatching {
        Gson().fromJson(preferences.getString("list", "[]"), Array<AdjustmentPreset>::class.java).filter { it.valid() }
    }.getOrDefault(emptyList())
    private fun write(list: List<AdjustmentPreset>) {
        check(preferences.edit().putString("list", Gson().toJson(list)).commit()) { "Não foi possível salvar o preset." }
        mutable.value = list
    }
    @Synchronized fun save(preset: AdjustmentPreset) {
        val list = mutable.value
        require(list.size < AdjustmentPreset.MAX || list.any { it.id == preset.id }) { "Limite de ${AdjustmentPreset.MAX} presets." }
        write(if (list.any { it.id == preset.id }) list.map { if (it.id == preset.id) preset else it } else list + preset)
    }
    @Synchronized fun delete(id: String) = write(mutable.value.filterNot { it.id == id })
    @Synchronized fun merge(incoming: List<AdjustmentPreset>) { if (incoming.isNotEmpty()) write(AdjustmentPreset.merge(mutable.value, incoming)) }
}

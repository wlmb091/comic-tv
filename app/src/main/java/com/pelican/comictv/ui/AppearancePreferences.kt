package com.pelican.comictv.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ColorPreset(val label: String, val accent: Long) {
    Amber("Preto e amarelo", 0xFFFFBD59),
    Green("Preto e verde", 0xFF72E39A),
    Blue("Preto e azul", 0xFF82C7FF),
    Violet("Preto e violeta", 0xFFD1A5FF),
}
data class AppearanceOptions(val preset: ColorPreset = ColorPreset.Amber, val textSize: Int = 0, val highContrast: Boolean = false) {
    val textScale: Float get() = listOf(1f, 1.15f, 1.3f)[textSize.coerceIn(0, 2)]
    companion object {
        fun restore(preset: String?, textSize: Int, highContrast: Boolean) = AppearanceOptions(
            ColorPreset.entries.firstOrNull { it.name == preset } ?: ColorPreset.Amber,
            textSize.coerceIn(0, 2), highContrast)
    }
}
class AppearancePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val writes = Mutex()
    private val _state = MutableStateFlow(AppearanceOptions.restore(
        preferences.getString("preset", null), preferences.getInt("text-size", 0), preferences.getBoolean("contrast", false)))
    val state = _state.asStateFlow()
    suspend fun update(options: AppearanceOptions) = withContext(Dispatchers.IO) {
        writes.withLock {
            val value = options.copy(textSize = options.textSize.coerceIn(0, 2))
            check(preferences.edit().putString("preset", value.preset.name).putInt("text-size", value.textSize)
                .putBoolean("contrast", value.highContrast).commit()) { "Não foi possível salvar a aparência." }
            _state.value = value
        }
    }
}

package com.pelican.comictv.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Names are persisted: preserve existing entries when extending the theme catalog.
enum class ColorPreset(
    val label: String, val accent: Long,
    val background: Long = 0xFF090A0C, val panel: Long = 0xFF191A1D,
    val track: Long = 0xFF383A3E, val action: Long = accent,
    val onAction: Long = 0xFF101010,
) {
    Amber("Comic TV", 0xFFFFBD59),
    Flix("Tema Flix", 0xFFFF7884, 0xFF080808, 0xFF202020, 0xFF454545, 0xFFD90920, 0xFFFFFFFF),
    Prime("Tema Prime", 0xFF40D5FF, 0xFF071522, 0xFF112B40, 0xFF285575, 0xFF40D5FF),
    Max("Tema Max", 0xFFB69AFF, 0xFF09091B, 0xFF252044, 0xFF4C426D, 0xFFB69AFF),
    Cinema("Tema Cinema", 0xFFFFFFFF, 0xFF000000, 0xFF202020, 0xFF454545),
    Green("Preto e verde", 0xFF72E39A, 0xFF07120C, 0xFF14281C, 0xFF345341),
    Blue("Preto e azul", 0xFF82C7FF, 0xFF080F1B, 0xFF18263B, 0xFF354B69),
    Violet("Preto e violeta", 0xFFD1A5FF, 0xFF110B1B, 0xFF2B1E3B, 0xFF503B67),
}
data class AppearanceOptions(val preset: ColorPreset = ColorPreset.Amber, val textSize: Int = 0, val highContrast: Boolean = false, val coverBackground: Boolean = false) {
    val textScale: Float get() = listOf(1f, 1.15f, 1.3f)[textSize.coerceIn(0, 2)]
    companion object {
        fun restore(preset: String?, textSize: Int, highContrast: Boolean, coverBackground: Boolean = false) = AppearanceOptions(
            ColorPreset.entries.firstOrNull { it.name == preset } ?: ColorPreset.Amber,
            textSize.coerceIn(0, 2), highContrast, coverBackground)
    }
}
class AppearancePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val writes = Mutex()
    private val _state = MutableStateFlow(AppearanceOptions.restore(
        preferences.getString("preset", null), preferences.getInt("text-size", 0), preferences.getBoolean("contrast", false), preferences.getBoolean("cover-background", false)))
    val state = _state.asStateFlow()
    suspend fun update(options: AppearanceOptions) = withContext(Dispatchers.IO) {
        writes.withLock {
            val value = options.copy(textSize = options.textSize.coerceIn(0, 2))
            check(preferences.edit().putString("preset", value.preset.name).putInt("text-size", value.textSize)
                .putBoolean("contrast", value.highContrast).putBoolean("cover-background", value.coverBackground).commit()) { "Não foi possível salvar a aparência." }
            _state.value = value
        }
    }
}

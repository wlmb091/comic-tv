package com.pelican.comictv.ui

import android.content.Context
import com.pelican.comictv.reader.ImagePreset
import com.pelican.comictv.reader.ReaderImageOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val carouselMotionChoices = linkedMapOf("None" to "Sem movimento", "Pan" to "Vertical",
    "ZoomIn" to "Zoom in", "ZoomOut" to "Zoom out", "Dynamic" to "Dinâmico")
private fun normalizeCarouselMotion(value: String?) = value?.takeIf { it in carouselMotionChoices } ?: "Pan"

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
    Max("Tema Max", 0xFFB69AFF, 0xFF0B0818, 0xFF252044, 0xFF4C426D, 0xFF281850, 0xFFFFFFFF),
    Cinema("Tema Cinema", 0xFFFFFFFF, 0xFF000000, 0xFF202020, 0xFF454545),
    // Legacy names stay persisted for preferences/backup compatibility; presentation was replaced.
    Green("Tema Rosa", 0xFFFF92C4, 0xFF180A13, 0xFF321827, 0xFF65304A),
    Blue("Tema Laranja", 0xFFFF9A62, 0xFF190D07, 0xFF341F14, 0xFF68452D),
    Violet("Tema Lima", 0xFFC4E76D, 0xFF111508, 0xFF252D16, 0xFF4D5D2F),
    ;

    val selectable get() = this != Green && this != Blue && this != Violet
}
data class AppearanceOptions(val preset: ColorPreset = ColorPreset.Amber, val textSize: Int = 0, val highContrast: Boolean = false,
    val coverBackground: Boolean = false, val textPercent: Int? = null, val cinemaMode: Boolean = false,
    val carouselImagePreset: ImagePreset = ImagePreset.Vintage, val carouselImageIntensity: Int = 2,
    val carouselAutoplay: Boolean = true, val carouselVerticalPan: Boolean = true, val carouselMotion: String = "Pan") {
    val textPercentage: Int get() = textPercent?.let(::normalizeTextPercentage) ?: listOf(100, 115, 130)[textSize.coerceIn(0, 2)]
    val textScale: Float get() = textPercentage / 100f
    val carouselImageOptions: ReaderImageOptions get() = ReaderImageOptions(carouselImagePreset, carouselImageIntensity.coerceIn(0, 2))
    fun withTextPercentage(percent: Int): AppearanceOptions {
        val normalized = normalizeTextPercentage(percent)
        val legacy = listOf(100, 115, 130).indices.minBy { kotlin.math.abs(listOf(100, 115, 130)[it] - normalized) }
        return copy(textSize = legacy, textPercent = normalized)
    }
    companion object {
        fun restore(preset: String?, textSize: Int, highContrast: Boolean, coverBackground: Boolean = false, textPercent: Int? = null,
            cinemaMode: Boolean = false, carouselImagePreset: String? = null, carouselImageIntensity: Int = 2,
            carouselAutoplay: Boolean = true, carouselVerticalPan: Boolean = true, carouselMotion: String? = null) = AppearanceOptions(
            ColorPreset.entries.firstOrNull { it.name == preset && it.selectable } ?: ColorPreset.Amber,
            textSize.coerceIn(0, 2), highContrast, coverBackground, cinemaMode = cinemaMode,
            carouselImagePreset = ImagePreset.entries.firstOrNull { it.name == carouselImagePreset } ?: ImagePreset.Vintage,
            carouselImageIntensity = carouselImageIntensity.coerceIn(0, 2), carouselAutoplay = carouselAutoplay,
            carouselVerticalPan = carouselVerticalPan, carouselMotion = normalizeCarouselMotion(carouselMotion))
            .let { if (textPercent == null) it else it.withTextPercentage(textPercent) }
        private fun normalizeTextPercentage(value: Int) = ((value.coerceIn(85, 130) + 2) / 5) * 5
    }
}
class AppearancePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val writes = Mutex()
    private val _state = MutableStateFlow(AppearanceOptions.restore(
        preferences.getString("preset", null), preferences.getInt("text-size", 0), preferences.getBoolean("contrast", false), preferences.getBoolean("cover-background", false),
        if (preferences.contains("text-percent")) preferences.getInt("text-percent", 100) else null,
        preferences.getBoolean("cinema-mode",false), preferences.getString("carousel-image-preset", null),
        preferences.getInt("carousel-image-intensity", 2), preferences.getBoolean("carousel-autoplay", true),
        preferences.getBoolean("carousel-vertical-pan", true), preferences.getString("carousel-motion", null)))
    val state = _state.asStateFlow()
    suspend fun update(options: AppearanceOptions) = withContext(Dispatchers.IO) {
        writes.withLock {
            val value = options.copy(preset = options.preset.takeIf { it.selectable } ?: ColorPreset.Amber,
                textSize = options.textSize.coerceIn(0, 2), carouselImageIntensity = options.carouselImageIntensity.coerceIn(0, 2),
                carouselMotion = normalizeCarouselMotion(options.carouselMotion))
                .let { if (it.textPercent == null) it else it.withTextPercentage(it.textPercent) }
            check(preferences.edit().putString("preset", value.preset.name).putInt("text-size", value.textSize)
                .apply { if (value.textPercent == null) remove("text-percent") else putInt("text-percent", value.textPercent) }
                .putBoolean("contrast", value.highContrast).putBoolean("cover-background", value.coverBackground)
                .putBoolean("cinema-mode",value.cinemaMode)
                .putString("carousel-image-preset", value.carouselImagePreset.name)
                .putInt("carousel-image-intensity", value.carouselImageIntensity)
                .putBoolean("carousel-autoplay", value.carouselAutoplay)
                .putBoolean("carousel-vertical-pan", value.carouselVerticalPan)
                .putString("carousel-motion", value.carouselMotion).commit()) { "Não foi possível salvar a aparência." }
            _state.value = value
        }
    }
}
